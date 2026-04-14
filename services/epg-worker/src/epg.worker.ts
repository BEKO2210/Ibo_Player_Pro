import { Injectable, Logger, OnApplicationShutdown } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import type { AppConfig } from '@api/config/configuration';
import { PrismaService } from '@api/prisma/prisma.service';
import { SourceService } from '@api/sources/sources.service';
import { parseXmltv } from '@premium-player/parsers';
import { EpgFetcher } from './epg.fetcher';

/**
 * Polls active EPG-bearing sources (`kind ∈ {xmltv, m3u_plus_epg}`),
 * fetches XMLTV, parses it, and upserts `epg_channels` + `epg_programs`.
 *
 * Design notes:
 *  - Single-writer per source — concurrent polls of the same source id
 *    are avoided by serializing one source at a time.
 *  - Programmes outside the `[now, now + EPG_WINDOW_AHEAD_HOURS]`
 *    window are skipped.
 *  - Stale programmes (older than now) are left in place; a companion
 *    cleanup job can prune them later if storage becomes an issue.
 *
 * Invocation:
 *  - `WORKER_RUN_ONCE=true` → one tick + exit (for CI / on-demand).
 *  - Otherwise → poll loop every `EPG_WORKER_POLL_INTERVAL_MS`.
 */
@Injectable()
export class EpgWorker implements OnApplicationShutdown {
  private readonly logger = new Logger(EpgWorker.name);
  private timer: NodeJS.Timeout | null = null;
  private shuttingDown = false;
  private running = false;

  constructor(
    private readonly prisma: PrismaService,
    private readonly sources: SourceService,
    private readonly fetcher: EpgFetcher,
    private readonly config: ConfigService<AppConfig, true>,
  ) {}

  async start(): Promise<void> {
    if (this.running) return;
    this.running = true;
    const runOnce = process.env.WORKER_RUN_ONCE === 'true';
    const intervalMs = this.config.get('epg.workerPollIntervalMs', { infer: true });
    this.logger.log(`EPG worker starting (runOnce=${runOnce}, pollIntervalMs=${intervalMs})`);

    await this.tick();
    if (runOnce) {
      this.logger.log('WORKER_RUN_ONCE=true — exiting after first tick');
      return;
    }
    this.scheduleNext(intervalMs);
  }

  private scheduleNext(intervalMs: number): void {
    if (this.shuttingDown) return;
    this.timer = setTimeout(() => {
      void this.tick().finally(() => this.scheduleNext(intervalMs));
    }, intervalMs);
  }

  private async tick(): Promise<void> {
    if (this.shuttingDown) return;
    const sources = await this.prisma.source.findMany({
      where: {
        isActive: true,
        deletedAt: null,
        kind: { in: ['xmltv', 'm3u_plus_epg'] },
      },
    });

    if (sources.length === 0) {
      this.logger.debug('No EPG-bearing sources to reconcile');
      return;
    }

    this.logger.log(`Reconciling ${sources.length} source(s)`);
    for (const source of sources) {
      if (this.shuttingDown) break;
      try {
        await this.processSource(source.id);
      } catch (err) {
        this.logger.warn(
          `  source=${source.id} failed: ${err instanceof Error ? err.message : String(err)}`,
        );
      }
    }
  }

  async processSource(sourceId: string): Promise<{
    channels: number;
    programmes: number;
    skipped: number;
  }> {
    const source = await this.prisma.source.findFirstOrThrow({ where: { id: sourceId } });
    const account = await this.prisma.account.findFirstOrThrow({
      where: { id: source.accountId },
    });
    const credentials = await this.sources.getDecryptedCredentials(account, source.id);

    const xml = await this.fetcher.fetchText(credentials.url, credentials.headers ?? undefined);
    const parsed = parseXmltv(xml);

    const windowHours = this.config.get('epg.windowAheadHours', { infer: true });
    const windowEnd = Date.now() + windowHours * 60 * 60 * 1000;
    const windowStart = Date.now() - 60 * 60 * 1000; // 1h grace

    // Upsert channels first so programmes FK can resolve.
    const channelIdMap = new Map<string, string>(); // externalId -> DB id
    for (const ch of parsed.channels) {
      const row = await this.prisma.epgChannel.upsert({
        where: {
          sourceId_externalChannelId: {
            sourceId: source.id,
            externalChannelId: ch.id,
          },
        },
        update: {
          displayName: ch.displayName ?? ch.id,
          iconUrl: ch.iconUrl,
        },
        create: {
          sourceId: source.id,
          externalChannelId: ch.id,
          displayName: ch.displayName ?? ch.id,
          iconUrl: ch.iconUrl,
        },
      });
      channelIdMap.set(ch.id, row.id);
    }

    // Persist programmes inside the window.
    let persisted = 0;
    let skipped = 0;
    for (const prog of parsed.programmes) {
      if (!prog.startsAt || !prog.endsAt) {
        skipped++;
        continue;
      }
      const startsAt = new Date(prog.startsAt);
      const endsAt = new Date(prog.endsAt);
      if (endsAt.getTime() < windowStart || startsAt.getTime() > windowEnd) {
        skipped++;
        continue;
      }
      const channelDbId = channelIdMap.get(prog.channelId);
      if (!channelDbId) {
        skipped++;
        continue;
      }
      // Create-only (no natural unique key today). A companion dedupe
      // job could be added if providers re-emit programmes frequently.
      await this.prisma.epgProgram.create({
        data: {
          channelId: channelDbId,
          sourceId: source.id,
          title: prog.title ?? 'Untitled',
          subtitle: prog.subtitle,
          description: prog.description,
          category: prog.category,
          startsAt,
          endsAt,
        },
      });
      persisted++;
    }

    await this.prisma.source.update({
      where: { id: source.id },
      data: { lastValidatedAt: new Date(), validationStatus: 'valid' },
    });

    this.logger.log(
      `  source=${source.id} channels=${parsed.channels.length} programmes(persisted/skipped)=${persisted}/${skipped}`,
    );

    return {
      channels: parsed.channels.length,
      programmes: persisted,
      skipped,
    };
  }

  async onApplicationShutdown(): Promise<void> {
    this.shuttingDown = true;
    if (this.timer) {
      clearTimeout(this.timer);
      this.timer = null;
    }
    this.logger.log('EPG worker shutdown requested');
  }
}

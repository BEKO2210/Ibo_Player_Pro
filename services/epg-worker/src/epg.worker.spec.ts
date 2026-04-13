import type { ConfigService } from '@nestjs/config';
import type { AppConfig } from '@api/config/configuration';
import type { PrismaService } from '@api/prisma/prisma.service';
import type { SourceService } from '@api/sources/sources.service';
import type { Source, Account } from '@prisma/client';
import { EpgFetcher } from './epg.fetcher';
import { EpgWorker } from './epg.worker';

describe('EpgWorker', () => {
  const now = new Date('2026-04-13T12:00:00.000Z');

  function makeSource(overrides: Partial<Source> = {}): Source {
    return {
      id: 's1',
      accountId: 'a1',
      profileId: null,
      name: 'Provider',
      kind: 'm3u_plus_epg',
      isActive: true,
      validationStatus: 'pending',
      lastValidatedAt: null,
      itemCountEstimate: null,
      createdAt: now,
      updatedAt: now,
      deletedAt: null,
      ...overrides,
    };
  }

  const account: Account = {
    id: 'a1',
    firebaseUid: 'f',
    email: 'u@e.com',
    emailVerified: true,
    locale: 'en',
    trialConsumed: true,
    status: 'active',
    createdAt: now,
    updatedAt: now,
    deletedAt: null,
  };

  function mockConfig(): ConfigService<AppConfig, true> {
    return {
      get: jest.fn((k: string) => {
        if (k === 'epg.workerPollIntervalMs') return 30 * 60_000;
        if (k === 'epg.windowAheadHours') return 48;
        return undefined;
      }),
    } as unknown as ConfigService<AppConfig, true>;
  }

  function mockPrisma(source: Source) {
    return {
      source: {
        findMany: jest.fn().mockResolvedValue([source]),
        findFirstOrThrow: jest.fn().mockResolvedValue(source),
        update: jest.fn().mockResolvedValue(source),
      },
      account: { findFirstOrThrow: jest.fn().mockResolvedValue(account) },
      epgChannel: {
        upsert: jest.fn((args: { where: unknown; create: { externalChannelId: string } }) =>
          Promise.resolve({ id: `db-${args.create.externalChannelId}` }),
        ),
      },
      epgProgram: {
        create: jest.fn().mockResolvedValue({ id: 'prog1' }),
      },
    };
  }

  function mockSourceService(url = 'https://provider/xmltv.xml'): jest.Mocked<Pick<SourceService, 'getDecryptedCredentials'>> {
    return {
      getDecryptedCredentials: jest.fn().mockResolvedValue({
        url,
        username: null,
        password: null,
        headers: null,
      }),
    } as jest.Mocked<Pick<SourceService, 'getDecryptedCredentials'>>;
  }

  function fetcherReturning(xml: string): EpgFetcher {
    const fetcher = new EpgFetcher();
    fetcher.setTransport(async () => ({
      ok: true,
      status: 200,
      text: async () => xml,
    }));
    return fetcher;
  }

  function xmltvUtcTimestamp(d: Date): string {
    const pad = (n: number): string => String(n).padStart(2, '0');
    return (
      `${d.getUTCFullYear()}${pad(d.getUTCMonth() + 1)}${pad(d.getUTCDate())}` +
      `${pad(d.getUTCHours())}${pad(d.getUTCMinutes())}00`
    );
  }

  it('runOnce ticks through active sources and persists channels + programmes', async () => {
    process.env.WORKER_RUN_ONCE = 'true';
    // Programme inside the default [now-1h, now+48h] window.
    const startAt = new Date(Date.now() + 60 * 60 * 1000);
    const endAt = new Date(Date.now() + 90 * 60 * 1000);
    const xml = `
      <tv>
        <channel id="bbc1"><display-name>BBC One</display-name></channel>
        <programme channel="bbc1" start="${xmltvUtcTimestamp(startAt)} +0000" stop="${xmltvUtcTimestamp(endAt)} +0000">
          <title>Tonight's Feature</title>
          <category>Movies</category>
        </programme>
      </tv>`;
    const source = makeSource();
    const prisma = mockPrisma(source);
    const worker = new EpgWorker(
      prisma as unknown as PrismaService,
      mockSourceService() as unknown as SourceService,
      fetcherReturning(xml),
      mockConfig(),
    );

    await worker.start();

    expect(prisma.source.findMany).toHaveBeenCalledWith({
      where: {
        isActive: true,
        deletedAt: null,
        kind: { in: ['xmltv', 'm3u_plus_epg'] },
      },
    });
    expect(prisma.epgChannel.upsert).toHaveBeenCalledTimes(1);
    expect(prisma.epgProgram.create).toHaveBeenCalledTimes(1);
    expect(prisma.source.update).toHaveBeenCalledWith({
      where: { id: source.id },
      data: expect.objectContaining({ validationStatus: 'valid' }),
    });

    delete process.env.WORKER_RUN_ONCE;
  });

  it('processSource skips programmes outside the window', async () => {
    // This programme is in 2010 — well outside [now-1h, now+48h]
    const xml = `
      <tv>
        <channel id="c1"><display-name>C1</display-name></channel>
        <programme channel="c1" start="20100101000000 +0000" stop="20100101010000 +0000">
          <title>Stale</title>
        </programme>
      </tv>`;
    const source = makeSource();
    const prisma = mockPrisma(source);
    const worker = new EpgWorker(
      prisma as unknown as PrismaService,
      mockSourceService() as unknown as SourceService,
      fetcherReturning(xml),
      mockConfig(),
    );

    const result = await worker.processSource(source.id);
    expect(result.channels).toBe(1);
    expect(result.programmes).toBe(0);
    expect(result.skipped).toBe(1);
  });

  it('empty XMLTV leaves the DB unchanged but still marks the source valid', async () => {
    const source = makeSource();
    const prisma = mockPrisma(source);
    const worker = new EpgWorker(
      prisma as unknown as PrismaService,
      mockSourceService() as unknown as SourceService,
      fetcherReturning(`<tv></tv>`),
      mockConfig(),
    );
    const result = await worker.processSource(source.id);
    expect(result.channels).toBe(0);
    expect(result.programmes).toBe(0);
    expect(prisma.epgProgram.create).not.toHaveBeenCalled();
    expect(prisma.source.update).toHaveBeenCalled();
  });

  it('per-source failure does not kill the batch', async () => {
    process.env.WORKER_RUN_ONCE = 'true';
    const source = makeSource();
    const prisma = mockPrisma(source);
    // Fetcher throws on first source
    const fetcher = new EpgFetcher();
    fetcher.setTransport(async () => {
      throw new Error('network blip');
    });
    const worker = new EpgWorker(
      prisma as unknown as PrismaService,
      mockSourceService() as unknown as SourceService,
      fetcher,
      mockConfig(),
    );

    await worker.start(); // should not throw

    expect(prisma.source.findMany).toHaveBeenCalled();
    delete process.env.WORKER_RUN_ONCE;
  });
});

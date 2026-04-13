import {
  HttpException,
  HttpStatus,
  Injectable,
  Logger,
  NotFoundException,
} from '@nestjs/common';
import type { Account, PlaybackSession, PlaybackState } from '@prisma/client';
import { PrismaService } from '../prisma/prisma.service';
import { EntitlementService } from '../entitlement/entitlement.service';
import { allowsPlayback } from '../entitlement/entitlement.state-machine';
import { buildErrorEnvelope, ErrorCode } from '../common/errors';

export interface StartPlaybackInput {
  profileId: string;
  sourceId: string;
  itemId: string;
  itemType: string;
  deviceId?: string | null;
}

export interface HeartbeatInput {
  sessionId: string;
  positionSeconds: number;
  state: PlaybackState;
  durationSeconds?: number;
}

export interface StopInput {
  sessionId: string;
  finalPositionSeconds: number;
  durationSeconds?: number;
  completed?: boolean;
}

/**
 * Authoritative playback lifecycle writer. Each playback session lives
 * in `playback_sessions`; every heartbeat + the final stop upsert the
 * corresponding `continue_watching` row so Home's CW rail reflects
 * real state.
 */
@Injectable()
export class PlaybackService {
  private readonly logger = new Logger(PlaybackService.name);

  constructor(
    private readonly prisma: PrismaService,
    private readonly entitlement: EntitlementService,
  ) {}

  async start(account: Account, input: StartPlaybackInput): Promise<PlaybackSession> {
    const ent = await this.entitlement.getOrInitialize(account.id);
    if (!allowsPlayback(ent.state)) {
      throw new HttpException(
        buildErrorEnvelope(
          ErrorCode.ENTITLEMENT_REQUIRED,
          `Playback requires an active entitlement (current state: ${ent.state}).`,
        ),
        HttpStatus.PAYMENT_REQUIRED,
      );
    }
    // Defensive ownership checks.
    const profile = await this.prisma.profile.findFirst({
      where: { id: input.profileId, accountId: account.id, deletedAt: null },
    });
    if (!profile) throw new NotFoundException('Profile not found for this account.');

    const source = await this.prisma.source.findFirst({
      where: { id: input.sourceId, accountId: account.id, deletedAt: null },
    });
    if (!source) throw new NotFoundException('Source not found for this account.');

    const session = await this.prisma.playbackSession.create({
      data: {
        accountId: account.id,
        profileId: input.profileId,
        deviceId: input.deviceId ?? null,
        sourceId: input.sourceId,
        itemId: input.itemId,
        itemType: input.itemType,
        state: 'starting',
        sessionStartedAt: new Date(),
      },
    });

    this.logger.log(
      `Playback start session=${session.id} account=${account.id} profile=${input.profileId} item=${input.itemId} (${input.itemType})`,
    );
    return session;
  }

  async heartbeat(account: Account, input: HeartbeatInput): Promise<PlaybackSession> {
    const session = await this.requireOwnedSession(account, input.sessionId);

    const now = new Date();
    const updated = await this.prisma.$transaction(async (tx) => {
      const sess = await tx.playbackSession.update({
        where: { id: session.id },
        data: {
          latestPositionSeconds: input.positionSeconds,
          state: input.state,
          lastHeartbeatAt: now,
        },
      });
      // Upsert continue_watching on every heartbeat for `vod` + `series_episode`.
      // Live streams don't need a resume position — we skip CW for them.
      if (session.itemType !== 'live') {
        await this.upsertContinueWatching(tx, sess, input.positionSeconds, input.durationSeconds ?? null, now);
      }
      return sess;
    });
    return updated;
  }

  async stop(account: Account, input: StopInput): Promise<PlaybackSession> {
    const session = await this.requireOwnedSession(account, input.sessionId);

    const now = new Date();
    return this.prisma.$transaction(async (tx) => {
      const sess = await tx.playbackSession.update({
        where: { id: session.id },
        data: {
          latestPositionSeconds: input.finalPositionSeconds,
          stoppedAt: now,
          state: 'stopped',
        },
      });
      // Record watch-history event.
      await tx.watchHistory.create({
        data: {
          accountId: sess.accountId,
          profileId: sess.profileId,
          sourceId: sess.sourceId,
          itemId: sess.itemId,
          itemType: sess.itemType,
          watchedSeconds: input.finalPositionSeconds,
          durationSeconds: input.durationSeconds ?? null,
          completed: input.completed ?? false,
          occurredAt: now,
        },
      });
      // Final CW upsert (unless live, or marked completed — completed
      // items clear the row to drop them off the rail).
      if (sess.itemType !== 'live') {
        if (input.completed) {
          await tx.continueWatching.deleteMany({
            where: { profileId: sess.profileId, itemId: sess.itemId },
          });
        } else {
          await this.upsertContinueWatching(
            tx,
            sess,
            input.finalPositionSeconds,
            input.durationSeconds ?? null,
            now,
          );
        }
      }
      return sess;
    });
  }

  async listContinueWatching(
    account: Account,
    profileId: string,
    limit = 20,
  ): Promise<
    Array<{
      id: string;
      sourceId: string | null;
      itemId: string;
      itemType: string;
      resumePositionSeconds: number;
      durationSeconds: number | null;
      lastPlayedAt: Date;
    }>
  > {
    const profile = await this.prisma.profile.findFirst({
      where: { id: profileId, accountId: account.id, deletedAt: null },
    });
    if (!profile) throw new NotFoundException('Profile not found for this account.');

    const rows = await this.prisma.continueWatching.findMany({
      where: { accountId: account.id, profileId, deletedAt: null },
      orderBy: { lastPlayedAt: 'desc' },
      take: Math.max(1, Math.min(100, limit)),
    });
    return rows.map((r) => ({
      id: r.id,
      sourceId: r.sourceId,
      itemId: r.itemId,
      itemType: r.itemType,
      resumePositionSeconds: r.resumePositionSeconds,
      durationSeconds: r.durationSeconds,
      lastPlayedAt: r.lastPlayedAt,
    }));
  }

  private async upsertContinueWatching(
    tx: Parameters<Parameters<PrismaService['$transaction']>[0]>[0],
    session: PlaybackSession,
    positionSeconds: number,
    durationSeconds: number | null,
    now: Date,
  ): Promise<void> {
    await tx.continueWatching.upsert({
      where: {
        profileId_itemId: {
          profileId: session.profileId,
          itemId: session.itemId,
        },
      },
      update: {
        resumePositionSeconds: positionSeconds,
        durationSeconds: durationSeconds ?? undefined,
        lastPlayedAt: now,
        deletedAt: null,
      },
      create: {
        accountId: session.accountId,
        profileId: session.profileId,
        sourceId: session.sourceId,
        itemId: session.itemId,
        itemType: session.itemType,
        resumePositionSeconds: positionSeconds,
        durationSeconds: durationSeconds ?? null,
        lastPlayedAt: now,
      },
    });
  }

  private async requireOwnedSession(
    account: Account,
    sessionId: string,
  ): Promise<PlaybackSession> {
    const session = await this.prisma.playbackSession.findFirst({
      where: { id: sessionId, accountId: account.id },
    });
    if (!session) {
      throw new NotFoundException('Playback session not found for this account.');
    }
    return session;
  }
}

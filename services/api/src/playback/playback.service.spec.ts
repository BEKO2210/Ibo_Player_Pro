import { HttpException, HttpStatus, NotFoundException } from '@nestjs/common';
import type { Account, Entitlement, PlaybackSession, Profile, Source } from '@prisma/client';
import { PlaybackService } from './playback.service';
import type { PrismaService } from '../prisma/prisma.service';
import type { EntitlementService } from '../entitlement/entitlement.service';

describe('PlaybackService', () => {
  const now = new Date('2026-04-13T12:00:00.000Z');
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
  const profile: Profile = {
    id: 'p1',
    accountId: account.id,
    name: 'Main',
    avatarKey: null,
    isKids: false,
    ageLimit: null,
    isDefault: true,
    createdAt: now,
    updatedAt: now,
    deletedAt: null,
  };
  const source: Source = {
    id: 's1',
    accountId: account.id,
    profileId: null,
    name: 'Provider',
    kind: 'm3u_plus_epg',
    isActive: true,
    validationStatus: 'valid',
    lastValidatedAt: null,
    itemCountEstimate: null,
    createdAt: now,
    updatedAt: now,
    deletedAt: null,
  };

  function makeEnt(state: Entitlement['state']): Entitlement {
    return {
      id: 'e1',
      accountId: account.id,
      state,
      trialStartedAt: null,
      trialEndsAt: null,
      activatedAt: state === 'lifetime_family' || state === 'lifetime_single' ? now : null,
      expiresAt: null,
      revokedAt: null,
      revokeReason: null,
      sourcePurchaseId: null,
      createdAt: now,
      updatedAt: now,
    };
  }

  function makeSession(overrides: Partial<PlaybackSession> = {}): PlaybackSession {
    return {
      id: 'sess1',
      accountId: account.id,
      profileId: profile.id,
      deviceId: null,
      sourceId: source.id,
      itemId: 'vod-1',
      itemType: 'vod',
      sessionStartedAt: now,
      lastHeartbeatAt: null,
      stoppedAt: null,
      latestPositionSeconds: 0,
      state: 'starting',
      errorCode: null,
      createdAt: now,
      updatedAt: now,
      ...overrides,
    };
  }

  function mockPrisma(opts: {
    sessionFound?: PlaybackSession | null;
    profileFound?: Profile | null;
    sourceFound?: Source | null;
    createdSession?: PlaybackSession;
    updatedSession?: PlaybackSession;
  } = {}): {
    profile: { findFirst: jest.Mock };
    source: { findFirst: jest.Mock };
    playbackSession: { findFirst: jest.Mock; create: jest.Mock; update: jest.Mock };
    continueWatching: { upsert: jest.Mock; deleteMany: jest.Mock };
    watchHistory: { create: jest.Mock };
    $transaction: jest.Mock;
  } {
    const created = opts.createdSession ?? makeSession();
    const updated = opts.updatedSession ?? makeSession({ latestPositionSeconds: 300, state: 'playing' });
    const sessionFound = 'sessionFound' in opts ? opts.sessionFound : makeSession();
    const profileFound = 'profileFound' in opts ? opts.profileFound : profile;
    const sourceFound = 'sourceFound' in opts ? opts.sourceFound : source;

    const pb = {
      findFirst: jest.fn().mockResolvedValue(sessionFound),
      create: jest.fn().mockResolvedValue(created),
      update: jest.fn().mockResolvedValue(updated),
    };
    const cw = {
      upsert: jest.fn().mockResolvedValue({ id: 'cw1' }),
      deleteMany: jest.fn().mockResolvedValue({ count: 1 }),
    };
    const wh = { create: jest.fn().mockResolvedValue({ id: 'wh1' }) };

    return {
      profile: { findFirst: jest.fn().mockResolvedValue(profileFound) },
      source: { findFirst: jest.fn().mockResolvedValue(sourceFound) },
      playbackSession: pb,
      continueWatching: cw,
      watchHistory: wh,
      $transaction: jest.fn(async (cb: (tx: unknown) => unknown) =>
        cb({
          playbackSession: pb,
          continueWatching: cw,
          watchHistory: wh,
        }),
      ),
    };
  }

  function mockEnt(state: Entitlement['state']): jest.Mocked<Pick<EntitlementService, 'getOrInitialize'>> {
    return {
      getOrInitialize: jest.fn().mockResolvedValue(makeEnt(state)),
    } as jest.Mocked<Pick<EntitlementService, 'getOrInitialize'>>;
  }

  describe('start', () => {
    it('creates a session when entitlement allows playback', async () => {
      const prisma = mockPrisma();
      const svc = new PlaybackService(
        prisma as unknown as PrismaService,
        mockEnt('lifetime_family') as unknown as EntitlementService,
      );

      const session = await svc.start(account, {
        profileId: profile.id,
        sourceId: source.id,
        itemId: 'vod-1',
        itemType: 'vod',
      });

      expect(prisma.playbackSession.create).toHaveBeenCalled();
      expect(session.itemType).toBe('vod');
    });

    it('returns 402 ENTITLEMENT_REQUIRED when entitlement cannot play', async () => {
      const prisma = mockPrisma();
      const svc = new PlaybackService(
        prisma as unknown as PrismaService,
        mockEnt('expired') as unknown as EntitlementService,
      );
      try {
        await svc.start(account, {
          profileId: profile.id,
          sourceId: source.id,
          itemId: 'vod-1',
          itemType: 'vod',
        });
        fail('expected HttpException');
      } catch (e) {
        expect(e).toBeInstanceOf(HttpException);
        expect((e as HttpException).getStatus()).toBe(HttpStatus.PAYMENT_REQUIRED);
      }
      expect(prisma.playbackSession.create).not.toHaveBeenCalled();
    });

    it('throws 404 when profile is not owned by caller', async () => {
      const prisma = mockPrisma({ profileFound: null });
      const svc = new PlaybackService(
        prisma as unknown as PrismaService,
        mockEnt('lifetime_family') as unknown as EntitlementService,
      );
      await expect(
        svc.start(account, {
          profileId: 'nope',
          sourceId: source.id,
          itemId: 'vod-1',
          itemType: 'vod',
        }),
      ).rejects.toBeInstanceOf(NotFoundException);
    });
  });

  describe('heartbeat', () => {
    it('upserts continue-watching for non-live items and updates position', async () => {
      const session = makeSession({ itemType: 'vod' });
      const prisma = mockPrisma({ sessionFound: session });
      const svc = new PlaybackService(
        prisma as unknown as PrismaService,
        mockEnt('lifetime_family') as unknown as EntitlementService,
      );

      await svc.heartbeat(account, {
        sessionId: session.id,
        positionSeconds: 300,
        state: 'playing',
        durationSeconds: 3600,
      });

      expect(prisma.playbackSession.update).toHaveBeenCalledWith(
        expect.objectContaining({
          where: { id: session.id },
          data: expect.objectContaining({
            latestPositionSeconds: 300,
            state: 'playing',
          }),
        }),
      );
      expect(prisma.continueWatching.upsert).toHaveBeenCalledTimes(1);
    });

    it('skips continue-watching upsert for live items', async () => {
      const session = makeSession({ itemType: 'live' });
      const prisma = mockPrisma({ sessionFound: session });
      const svc = new PlaybackService(
        prisma as unknown as PrismaService,
        mockEnt('lifetime_family') as unknown as EntitlementService,
      );
      await svc.heartbeat(account, {
        sessionId: session.id,
        positionSeconds: 42,
        state: 'playing',
      });
      expect(prisma.playbackSession.update).toHaveBeenCalled();
      expect(prisma.continueWatching.upsert).not.toHaveBeenCalled();
    });

    it('throws 404 when the session is not owned', async () => {
      const prisma = mockPrisma({ sessionFound: null });
      const svc = new PlaybackService(
        prisma as unknown as PrismaService,
        mockEnt('lifetime_family') as unknown as EntitlementService,
      );
      await expect(
        svc.heartbeat(account, { sessionId: 'nope', positionSeconds: 0, state: 'playing' }),
      ).rejects.toBeInstanceOf(NotFoundException);
    });
  });

  describe('stop', () => {
    it('records watch_history and upserts continue-watching when not completed', async () => {
      const session = makeSession({ itemType: 'vod' });
      const prisma = mockPrisma({ sessionFound: session });
      const svc = new PlaybackService(
        prisma as unknown as PrismaService,
        mockEnt('lifetime_family') as unknown as EntitlementService,
      );
      await svc.stop(account, {
        sessionId: session.id,
        finalPositionSeconds: 900,
        durationSeconds: 3600,
        completed: false,
      });
      expect(prisma.watchHistory.create).toHaveBeenCalledWith(
        expect.objectContaining({
          data: expect.objectContaining({
            watchedSeconds: 900,
            completed: false,
          }),
        }),
      );
      expect(prisma.continueWatching.upsert).toHaveBeenCalled();
      expect(prisma.continueWatching.deleteMany).not.toHaveBeenCalled();
    });

    it('deletes continue-watching row when completed=true', async () => {
      const session = makeSession({ itemType: 'vod' });
      const prisma = mockPrisma({ sessionFound: session });
      const svc = new PlaybackService(
        prisma as unknown as PrismaService,
        mockEnt('lifetime_family') as unknown as EntitlementService,
      );
      await svc.stop(account, {
        sessionId: session.id,
        finalPositionSeconds: 3600,
        completed: true,
      });
      expect(prisma.continueWatching.deleteMany).toHaveBeenCalled();
      expect(prisma.continueWatching.upsert).not.toHaveBeenCalled();
    });
  });
});

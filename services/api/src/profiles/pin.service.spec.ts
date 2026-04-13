import * as argon2 from 'argon2';
import type { ConfigService } from '@nestjs/config';
import type { ProfilePin } from '@prisma/client';
import { PinService } from './pin.service';
import type { PrismaService } from '../prisma/prisma.service';
import type { AppConfig } from '../config/configuration';

describe('PinService', () => {
  const profileId = 'p1';
  const now = new Date('2026-04-13T12:00:00.000Z');

  function mockPrisma(opts: { stored?: ProfilePin | null } = {}): {
    profilePin: {
      findUnique: jest.Mock;
      upsert: jest.Mock;
      update: jest.Mock;
      deleteMany: jest.Mock;
    };
  } {
    return {
      profilePin: {
        findUnique: jest.fn().mockResolvedValue(opts.stored ?? null),
        upsert: jest.fn().mockResolvedValue(opts.stored ?? makePin()),
        update: jest.fn().mockResolvedValue(opts.stored ?? makePin()),
        deleteMany: jest.fn().mockResolvedValue({ count: opts.stored ? 1 : 0 }),
      },
    };
  }

  function mockConfig(maxAttempts = 5, lockoutMs = 900_000): ConfigService<AppConfig, true> {
    return {
      get: jest.fn((k: string) => {
        if (k === 'pin.maxFailedAttempts') return maxAttempts;
        if (k === 'pin.lockoutDurationMs') return lockoutMs;
        return undefined;
      }),
    } as unknown as ConfigService<AppConfig, true>;
  }

  function makePin(overrides: Partial<ProfilePin> = {}): ProfilePin {
    return {
      profileId,
      pinHash: 'placeholder',
      pinAlgo: 'argon2id',
      pinUpdatedAt: now,
      failedAttemptCount: 0,
      lockUntil: null,
      createdAt: now,
      updatedAt: now,
      ...overrides,
    };
  }

  describe('setPin', () => {
    it('hashes with argon2id and resets lockout state', async () => {
      const prisma = mockPrisma();
      const svc = new PinService(prisma as unknown as PrismaService, mockConfig());

      await svc.setPin(profileId, '1234');

      expect(prisma.profilePin.upsert).toHaveBeenCalledTimes(1);
      const args = prisma.profilePin.upsert.mock.calls[0][0];
      expect(args.where).toEqual({ profileId });
      expect(args.create.pinHash).toMatch(/^\$argon2id\$/);
      expect(args.update.pinHash).toMatch(/^\$argon2id\$/);
      expect(args.update.failedAttemptCount).toBe(0);
      expect(args.update.lockUntil).toBeNull();
    });
  });

  describe('verify', () => {
    it('returns no_pin when no row exists', async () => {
      const prisma = mockPrisma({ stored: null });
      const svc = new PinService(prisma as unknown as PrismaService, mockConfig());
      await expect(svc.verify(profileId, '1234')).resolves.toEqual({
        ok: false,
        reason: 'no_pin',
      });
    });

    it('returns ok=true on correct PIN and resets counter when needed', async () => {
      const correct = '4321';
      const hash = await argon2.hash(correct, { type: argon2.argon2id });
      const stored = makePin({ pinHash: hash, failedAttemptCount: 2, lockUntil: null });
      const prisma = mockPrisma({ stored });
      const svc = new PinService(prisma as unknown as PrismaService, mockConfig());

      const out = await svc.verify(profileId, correct);

      expect(out).toEqual({ ok: true });
      // counter was non-zero so update() should be called to reset.
      expect(prisma.profilePin.update).toHaveBeenCalledWith({
        where: { profileId },
        data: { failedAttemptCount: 0, lockUntil: null },
      });
    });

    it('increments failed-attempt counter on mismatch and locks at the threshold', async () => {
      const hash = await argon2.hash('correct-pin', { type: argon2.argon2id });
      const stored = makePin({ pinHash: hash, failedAttemptCount: 4, lockUntil: null });
      const prisma = mockPrisma({ stored });
      const svc = new PinService(prisma as unknown as PrismaService, mockConfig(5, 900_000));

      const out = await svc.verify(profileId, 'wrong');

      expect(out.ok).toBe(false);
      if (out.ok === false) expect(out.reason).toBe('mismatch');
      const updateArgs = prisma.profilePin.update.mock.calls[0][0];
      expect(updateArgs.data.failedAttemptCount).toBe(0); // resets at limit
      expect(updateArgs.data.lockUntil).toBeInstanceOf(Date);
    });

    it('returns locked while lock_until is in the future', async () => {
      const hash = await argon2.hash('correct-pin', { type: argon2.argon2id });
      const future = new Date(Date.now() + 60_000);
      const stored = makePin({ pinHash: hash, lockUntil: future });
      const prisma = mockPrisma({ stored });
      const svc = new PinService(prisma as unknown as PrismaService, mockConfig());

      const out = await svc.verify(profileId, 'correct-pin');

      expect(out.ok).toBe(false);
      if (out.ok === false && out.reason === 'locked') {
        expect(out.lockedUntil).toEqual(future);
      } else {
        fail('expected locked outcome');
      }
      expect(prisma.profilePin.update).not.toHaveBeenCalled();
    });

    it('allows verify again after lock_until has passed', async () => {
      const correct = 'good-pin';
      const hash = await argon2.hash(correct, { type: argon2.argon2id });
      const past = new Date(Date.now() - 60_000);
      const stored = makePin({ pinHash: hash, failedAttemptCount: 0, lockUntil: past });
      const prisma = mockPrisma({ stored });
      const svc = new PinService(prisma as unknown as PrismaService, mockConfig());

      const out = await svc.verify(profileId, correct);
      expect(out.ok).toBe(true);
    });
  });

  describe('hasPin / clearPin', () => {
    it('hasPin returns true/false based on row presence', async () => {
      const prismaWith = mockPrisma({ stored: makePin() });
      const svcWith = new PinService(prismaWith as unknown as PrismaService, mockConfig());
      await expect(svcWith.hasPin(profileId)).resolves.toBe(true);

      const prismaWithout = mockPrisma({ stored: null });
      const svcWithout = new PinService(
        prismaWithout as unknown as PrismaService,
        mockConfig(),
      );
      await expect(svcWithout.hasPin(profileId)).resolves.toBe(false);
    });

    it('clearPin deletes the row', async () => {
      const prisma = mockPrisma({ stored: makePin() });
      const svc = new PinService(prisma as unknown as PrismaService, mockConfig());
      await svc.clearPin(profileId);
      expect(prisma.profilePin.deleteMany).toHaveBeenCalledWith({
        where: { profileId },
      });
    });
  });
});

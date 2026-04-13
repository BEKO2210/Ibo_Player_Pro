import { HttpException, HttpStatus, NotFoundException } from '@nestjs/common';
import type { Account, Device, Entitlement } from '@prisma/client';
import {
  DevicesService,
  generateDeviceToken,
  hashDeviceToken,
} from './devices.service';
import type { PrismaService } from '../prisma/prisma.service';
import type { EntitlementService } from '../entitlement/entitlement.service';

describe('DevicesService', () => {
  const now = new Date('2026-04-13T12:00:00.000Z');
  const account: Account = {
    id: 'a1111111-1111-1111-1111-111111111111',
    firebaseUid: 'firebase-uid-123',
    email: 'user@example.com',
    emailVerified: true,
    locale: 'en',
    trialConsumed: false,
    status: 'active',
    createdAt: now,
    updatedAt: now,
    deletedAt: null,
  };

  function makeEntitlement(state: Entitlement['state']): Entitlement {
    return {
      id: 'e1111111-1111-1111-1111-111111111111',
      accountId: account.id,
      state,
      trialStartedAt: null,
      trialEndsAt: null,
      activatedAt: state === 'lifetime_single' || state === 'lifetime_family' ? now : null,
      expiresAt: null,
      revokedAt: null,
      revokeReason: null,
      sourcePurchaseId: null,
      createdAt: now,
      updatedAt: now,
    };
  }

  function makeDevice(overrides: Partial<Device> = {}): Device {
    return {
      id: overrides.id ?? 'd1111111-1111-1111-1111-111111111111',
      accountId: account.id,
      deviceTokenHash: overrides.deviceTokenHash ?? 'hash',
      deviceName: 'Living Room TV',
      platform: 'android_tv',
      appVersion: null,
      osVersion: null,
      lastIp: null,
      lastSeenAt: null,
      revokedAt: null,
      createdAt: now,
      updatedAt: now,
      deletedAt: null,
      ...overrides,
    };
  }

  function mockPrisma(opts: {
    activeCount?: number;
    createdDevice?: Device;
    findFirst?: Device | null;
    findUnique?: Device | null;
    findMany?: Device[];
    updated?: Device;
  } = {}): {
    device: {
      count: jest.Mock;
      create: jest.Mock;
      findFirst: jest.Mock;
      findUnique: jest.Mock;
      findMany: jest.Mock;
      update: jest.Mock;
    };
  } {
    return {
      device: {
        count: jest.fn().mockResolvedValue(opts.activeCount ?? 0),
        create: jest.fn().mockResolvedValue(opts.createdDevice ?? makeDevice()),
        findFirst: jest.fn().mockResolvedValue(opts.findFirst ?? null),
        findUnique: jest.fn().mockResolvedValue(opts.findUnique ?? null),
        findMany: jest.fn().mockResolvedValue(opts.findMany ?? []),
        update: jest.fn().mockResolvedValue(opts.updated ?? makeDevice({ revokedAt: now })),
      },
    };
  }

  function mockEntitlement(state: Entitlement['state']): {
    getOrInitialize: jest.Mock;
  } {
    return {
      getOrInitialize: jest.fn().mockResolvedValue(makeEntitlement(state)),
    };
  }

  describe('register', () => {
    it('stores the sha256 hash and returns the plaintext token once', async () => {
      const prisma = mockPrisma({ activeCount: 0, createdDevice: makeDevice() });
      const entitlement = mockEntitlement('trial');
      const svc = new DevicesService(
        prisma as unknown as PrismaService,
        entitlement as unknown as EntitlementService,
      );

      const { device, deviceToken } = await svc.register(account, {
        name: 'Living Room TV',
        platform: 'android_tv',
      });

      expect(deviceToken).toMatch(/^[A-Za-z0-9_-]{43}$/);
      expect(prisma.device.create).toHaveBeenCalledTimes(1);
      const createArgs = prisma.device.create.mock.calls[0][0].data;
      expect(createArgs.deviceTokenHash).toBe(hashDeviceToken(deviceToken));
      expect(createArgs.deviceTokenHash).not.toBe(deviceToken);
      expect(createArgs.accountId).toBe(account.id);
      expect(createArgs.platform).toBe('android_tv');
      expect(device.id).toBeDefined();
    });

    it('returns 409 SLOT_FULL on trial at active=1', async () => {
      const prisma = mockPrisma({ activeCount: 1 });
      const entitlement = mockEntitlement('trial');
      const svc = new DevicesService(
        prisma as unknown as PrismaService,
        entitlement as unknown as EntitlementService,
      );

      await expectHttp(
        () => svc.register(account, { name: 'a', platform: 'android_tv' }),
        HttpStatus.CONFLICT,
        'SLOT_FULL',
      );
      expect(prisma.device.create).not.toHaveBeenCalled();
    });

    it('returns 409 SLOT_FULL on lifetime_single at active=1', async () => {
      const prisma = mockPrisma({ activeCount: 1 });
      const entitlement = mockEntitlement('lifetime_single');
      const svc = new DevicesService(
        prisma as unknown as PrismaService,
        entitlement as unknown as EntitlementService,
      );

      await expectHttp(
        () => svc.register(account, { name: 'a', platform: 'android_tv' }),
        HttpStatus.CONFLICT,
        'SLOT_FULL',
      );
    });

    it('allows up to 5 devices on lifetime_family and refuses the 6th', async () => {
      const familyAllowed = mockPrisma({ activeCount: 4 });
      const entitlement = mockEntitlement('lifetime_family');
      const svc = new DevicesService(
        familyAllowed as unknown as PrismaService,
        entitlement as unknown as EntitlementService,
      );

      await expect(
        svc.register(account, { name: 'a', platform: 'android_tv' }),
      ).resolves.toBeDefined();
      expect(familyAllowed.device.create).toHaveBeenCalled();

      const familyCapped = mockPrisma({ activeCount: 5 });
      const svc2 = new DevicesService(
        familyCapped as unknown as PrismaService,
        mockEntitlement('lifetime_family') as unknown as EntitlementService,
      );
      await expectHttp(
        () => svc2.register(account, { name: 'a', platform: 'android_tv' }),
        HttpStatus.CONFLICT,
        'SLOT_FULL',
      );
    });

    it.each(['none', 'expired', 'revoked'] as const)(
      'returns 402 ENTITLEMENT_REQUIRED on %s entitlement',
      async (state) => {
        const prisma = mockPrisma();
        const entitlement = mockEntitlement(state);
        const svc = new DevicesService(
          prisma as unknown as PrismaService,
          entitlement as unknown as EntitlementService,
        );
        await expectHttp(
          () => svc.register(account, { name: 'a', platform: 'android_tv' }),
          HttpStatus.PAYMENT_REQUIRED,
          'ENTITLEMENT_REQUIRED',
        );
        expect(prisma.device.count).not.toHaveBeenCalled();
        expect(prisma.device.create).not.toHaveBeenCalled();
      },
    );
  });

  describe('listForAccount', () => {
    it('marks isCurrent when id matches and isRevoked when revokedAt is set', async () => {
      const d1 = makeDevice({ id: 'd1' });
      const d2 = makeDevice({ id: 'd2', revokedAt: now });
      const prisma = mockPrisma({ findMany: [d1, d2] });
      const entitlement = mockEntitlement('trial');
      const svc = new DevicesService(
        prisma as unknown as PrismaService,
        entitlement as unknown as EntitlementService,
      );

      const list = await svc.listForAccount(account.id, 'd1');

      expect(list).toHaveLength(2);
      expect(list[0]).toMatchObject({ id: 'd1', isCurrent: true, isRevoked: false });
      expect(list[1]).toMatchObject({ id: 'd2', isCurrent: false, isRevoked: true });
    });
  });

  describe('revoke', () => {
    it('soft-revokes a device the caller owns', async () => {
      const existing = makeDevice({ id: 'd1', revokedAt: null });
      const updated = { ...existing, revokedAt: now };
      const prisma = mockPrisma({ findFirst: existing, updated });
      const entitlement = mockEntitlement('trial');
      const svc = new DevicesService(
        prisma as unknown as PrismaService,
        entitlement as unknown as EntitlementService,
      );

      const result = await svc.revoke(account.id, 'd1');

      expect(prisma.device.update).toHaveBeenCalledWith({
        where: { id: 'd1' },
        data: { revokedAt: expect.any(Date) },
      });
      expect(result.revokedAt).toEqual(now);
    });

    it('is a no-op if already revoked', async () => {
      const alreadyRevoked = makeDevice({ id: 'd1', revokedAt: now });
      const prisma = mockPrisma({ findFirst: alreadyRevoked });
      const svc = new DevicesService(
        prisma as unknown as PrismaService,
        mockEntitlement('trial') as unknown as EntitlementService,
      );

      const result = await svc.revoke(account.id, 'd1');

      expect(result).toBe(alreadyRevoked);
      expect(prisma.device.update).not.toHaveBeenCalled();
    });

    it('throws 404 if device does not belong to the caller', async () => {
      const prisma = mockPrisma({ findFirst: null });
      const svc = new DevicesService(
        prisma as unknown as PrismaService,
        mockEntitlement('trial') as unknown as EntitlementService,
      );

      await expect(svc.revoke(account.id, 'd1')).rejects.toBeInstanceOf(
        NotFoundException,
      );
    });
  });

  describe('findByToken / hashing', () => {
    it('looks up by sha256 hash of the plaintext token', async () => {
      const token = generateDeviceToken();
      const hash = hashDeviceToken(token);
      const prisma = mockPrisma({ findUnique: makeDevice({ deviceTokenHash: hash }) });
      const svc = new DevicesService(
        prisma as unknown as PrismaService,
        mockEntitlement('trial') as unknown as EntitlementService,
      );

      const found = await svc.findByToken(token);

      expect(prisma.device.findUnique).toHaveBeenCalledWith({
        where: { deviceTokenHash: hash },
      });
      expect(found).not.toBeNull();
    });

    it('returns null for empty/unknown tokens', async () => {
      const prisma = mockPrisma({ findUnique: null });
      const svc = new DevicesService(
        prisma as unknown as PrismaService,
        mockEntitlement('trial') as unknown as EntitlementService,
      );
      await expect(svc.findByToken('')).resolves.toBeNull();
      await expect(svc.findByToken('nope')).resolves.toBeNull();
    });
  });
});

async function expectHttp(
  run: () => Promise<unknown>,
  status: number,
  code: string,
): Promise<void> {
  try {
    await run();
    fail(`Expected HttpException with status ${status}`);
  } catch (err) {
    expect(err).toBeInstanceOf(HttpException);
    const ex = err as HttpException;
    expect(ex.getStatus()).toBe(status);
    const body = ex.getResponse() as { error: { code: string } };
    expect(body.error.code).toBe(code);
  }
}

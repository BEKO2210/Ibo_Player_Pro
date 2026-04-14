import { HttpException, HttpStatus, NotFoundException } from '@nestjs/common';
import type { Account, Entitlement, Profile } from '@prisma/client';
import { ProfileService } from './profiles.service';
import type { PrismaService } from '../prisma/prisma.service';
import type { EntitlementService } from '../entitlement/entitlement.service';
import type { PinService } from './pin.service';

describe('ProfileService', () => {
  const now = new Date('2026-04-13T12:00:00.000Z');
  const account: Account = {
    id: 'a1',
    firebaseUid: 'f',
    email: 'u@e.com',
    emailVerified: true,
    locale: 'en',
    trialConsumed: false,
    status: 'active',
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
      activatedAt: null,
      expiresAt: null,
      revokedAt: null,
      revokeReason: null,
      sourcePurchaseId: null,
      createdAt: now,
      updatedAt: now,
    };
  }

  function makeProfile(over: Partial<Profile> = {}): Profile {
    return {
      id: 'pp1',
      accountId: account.id,
      name: 'Main',
      avatarKey: null,
      isKids: false,
      ageLimit: null,
      isDefault: true,
      createdAt: now,
      updatedAt: now,
      deletedAt: null,
      ...over,
    };
  }

  function mockPrisma(opts: {
    activeCount?: number;
    create?: Profile;
    update?: Profile;
    findFirst?: Profile | null;
    remainingAfterDelete?: number;
    promoted?: Profile | null;
  } = {}): {
    profile: {
      count: jest.Mock;
      create: jest.Mock;
      update: jest.Mock;
      updateMany: jest.Mock;
      findFirst: jest.Mock;
      findMany: jest.Mock;
    };
    $transaction: jest.Mock;
  } {
    const created = opts.create ?? makeProfile();
    const updated = opts.update ?? makeProfile();
    const profile = {
      count: jest.fn().mockResolvedValue(opts.activeCount ?? 0),
      create: jest.fn().mockResolvedValue(created),
      update: jest.fn().mockResolvedValue(updated),
      updateMany: jest.fn().mockResolvedValue({ count: 0 }),
      findFirst: jest
        .fn()
        .mockResolvedValue('findFirst' in opts ? opts.findFirst : created),
      findMany: jest.fn().mockResolvedValue([]),
    };

    return {
      profile,
      $transaction: jest.fn(async (cb: (tx: unknown) => unknown) => {
        return cb({
          profile: {
            ...profile,
            count: jest.fn().mockResolvedValue(opts.remainingAfterDelete ?? 0),
            findFirst: jest
              .fn()
              .mockResolvedValueOnce(opts.promoted ?? null)
              .mockResolvedValue(opts.promoted ?? null),
          },
        });
      }),
    };
  }

  function mockEnt(state: Entitlement['state']): jest.Mocked<Pick<EntitlementService, 'getOrInitialize'>> {
    return {
      getOrInitialize: jest.fn().mockResolvedValue(makeEnt(state)),
    } as jest.Mocked<Pick<EntitlementService, 'getOrInitialize'>>;
  }

  function mockPin(): jest.Mocked<Pick<PinService, 'setPin' | 'clearPin' | 'hasPin'>> {
    return {
      setPin: jest.fn().mockResolvedValue(undefined),
      clearPin: jest.fn().mockResolvedValue(undefined),
      hasPin: jest.fn().mockResolvedValue(false),
    } as jest.Mocked<Pick<PinService, 'setPin' | 'clearPin' | 'hasPin'>>;
  }

  describe('create', () => {
    it('creates a profile with default kids age 12 when isKids=true and no ageLimit', async () => {
      const prisma = mockPrisma({ activeCount: 0 });
      const ent = mockEnt('lifetime_family');
      const pin = mockPin();
      const svc = new ProfileService(
        prisma as unknown as PrismaService,
        ent as unknown as EntitlementService,
        pin as unknown as PinService,
      );

      await svc.create(account, { name: 'Lily', isKids: true });

      const callArgs = prisma.profile.create.mock.calls[0][0];
      expect(callArgs.data.isKids).toBe(true);
      expect(callArgs.data.ageLimit).toBe(12);
      expect(callArgs.data.isDefault).toBe(true);
    });

    it('uses provided ageLimit verbatim', async () => {
      const prisma = mockPrisma({ activeCount: 1 });
      const ent = mockEnt('lifetime_family');
      const svc = new ProfileService(
        prisma as unknown as PrismaService,
        ent as unknown as EntitlementService,
        mockPin() as unknown as PinService,
      );
      await svc.create(account, { name: 'Teen', isKids: true, ageLimit: 18 });
      expect(prisma.profile.create.mock.calls[0][0].data.ageLimit).toBe(18);
    });

    it('marks first profile as default automatically', async () => {
      const prisma = mockPrisma({ activeCount: 0 });
      const ent = mockEnt('lifetime_family');
      const svc = new ProfileService(
        prisma as unknown as PrismaService,
        ent as unknown as EntitlementService,
        mockPin() as unknown as PinService,
      );
      await svc.create(account, { name: 'First', isKids: false });
      expect(prisma.profile.create.mock.calls[0][0].data.isDefault).toBe(true);
    });

    it('does not mark second profile default unless asked', async () => {
      const prisma = mockPrisma({ activeCount: 1 });
      const ent = mockEnt('lifetime_family');
      const svc = new ProfileService(
        prisma as unknown as PrismaService,
        ent as unknown as EntitlementService,
        mockPin() as unknown as PinService,
      );
      await svc.create(account, { name: 'Second', isKids: false });
      expect(prisma.profile.create.mock.calls[0][0].data.isDefault).toBe(false);
    });

    it('returns 402 ENTITLEMENT_REQUIRED on cap=0 entitlement', async () => {
      const prisma = mockPrisma();
      const ent = mockEnt('expired');
      const svc = new ProfileService(
        prisma as unknown as PrismaService,
        ent as unknown as EntitlementService,
        mockPin() as unknown as PinService,
      );

      try {
        await svc.create(account, { name: 'X', isKids: false });
        fail('expected HttpException');
      } catch (e) {
        expect(e).toBeInstanceOf(HttpException);
        expect((e as HttpException).getStatus()).toBe(HttpStatus.PAYMENT_REQUIRED);
        const body = (e as HttpException).getResponse() as {
          error: { code: string };
        };
        expect(body.error.code).toBe('ENTITLEMENT_REQUIRED');
      }
      expect(prisma.profile.create).not.toHaveBeenCalled();
    });

    it('returns 409 SLOT_FULL when at cap', async () => {
      const prisma = mockPrisma({ activeCount: 5 });
      const ent = mockEnt('lifetime_family');
      const svc = new ProfileService(
        prisma as unknown as PrismaService,
        ent as unknown as EntitlementService,
        mockPin() as unknown as PinService,
      );
      try {
        await svc.create(account, { name: 'Sixth', isKids: false });
        fail('expected HttpException');
      } catch (e) {
        expect(e).toBeInstanceOf(HttpException);
        expect((e as HttpException).getStatus()).toBe(HttpStatus.CONFLICT);
        const body = (e as HttpException).getResponse() as { error: { code: string } };
        expect(body.error.code).toBe('SLOT_FULL');
      }
    });

    it('returns 409 SLOT_FULL on lifetime_single at active=1', async () => {
      const prisma = mockPrisma({ activeCount: 1 });
      const ent = mockEnt('lifetime_single');
      const svc = new ProfileService(
        prisma as unknown as PrismaService,
        ent as unknown as EntitlementService,
        mockPin() as unknown as PinService,
      );
      await expect(svc.create(account, { name: 'X', isKids: false })).rejects.toBeInstanceOf(
        HttpException,
      );
    });

    it('sets PIN when provided', async () => {
      const prisma = mockPrisma({ activeCount: 0 });
      const ent = mockEnt('lifetime_family');
      const pin = mockPin();
      const svc = new ProfileService(
        prisma as unknown as PrismaService,
        ent as unknown as EntitlementService,
        pin as unknown as PinService,
      );
      await svc.create(account, { name: 'Pinned', isKids: false, pin: '1234' });
      expect(pin.setPin).toHaveBeenCalledWith(expect.any(String), '1234');
    });
  });

  describe('softDelete', () => {
    it('refuses to delete the last remaining profile', async () => {
      const last = makeProfile({ id: 'last', isDefault: true });
      const prisma = mockPrisma({ findFirst: last, remainingAfterDelete: 0 });
      const svc = new ProfileService(
        prisma as unknown as PrismaService,
        mockEnt('lifetime_family') as unknown as EntitlementService,
        mockPin() as unknown as PinService,
      );

      try {
        await svc.softDelete(account, last.id);
        fail('expected HttpException');
      } catch (e) {
        expect(e).toBeInstanceOf(HttpException);
        expect((e as HttpException).getStatus()).toBe(HttpStatus.CONFLICT);
        const body = (e as HttpException).getResponse() as { error: { code: string } };
        expect(body.error.code).toBe('VALIDATION_ERROR');
      }
    });

    it('throws 404 when profile is not owned by the caller', async () => {
      const prisma = mockPrisma({ findFirst: null });
      const svc = new ProfileService(
        prisma as unknown as PrismaService,
        mockEnt('lifetime_family') as unknown as EntitlementService,
        mockPin() as unknown as PinService,
      );
      await expect(svc.softDelete(account, 'nope')).rejects.toBeInstanceOf(NotFoundException);
    });
  });
});

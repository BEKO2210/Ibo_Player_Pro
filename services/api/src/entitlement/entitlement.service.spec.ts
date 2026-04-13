import { HttpException, HttpStatus } from '@nestjs/common';
import type { Account, Entitlement } from '@prisma/client';
import { EntitlementService } from './entitlement.service';
import type { PrismaService } from '../prisma/prisma.service';

describe('EntitlementService', () => {
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

  const entitlementNone: Entitlement = {
    id: 'e1111111-1111-1111-1111-111111111111',
    accountId: account.id,
    state: 'none',
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

  function mockPrisma(opts: {
    upsertEnt?: Entitlement;
    updateEnt?: Entitlement;
    updateAccount?: Account;
  } = {}): {
    entitlement: { upsert: jest.Mock; update: jest.Mock };
    account: { update: jest.Mock; findUniqueOrThrow: jest.Mock };
    $transaction: jest.Mock;
  } {
    return {
      entitlement: {
        upsert: jest.fn().mockResolvedValue(opts.upsertEnt ?? entitlementNone),
        update: jest.fn().mockResolvedValue(opts.updateEnt ?? entitlementNone),
      },
      account: {
        update: jest.fn().mockResolvedValue(opts.updateAccount ?? account),
        findUniqueOrThrow: jest.fn().mockResolvedValue(account),
      },
      $transaction: jest.fn(async (ops: unknown[] | ((tx: unknown) => unknown)) => {
        if (typeof ops === 'function') {
          // Interactive tx used by applyEvent
          return ops({
            account: { findUniqueOrThrow: jest.fn().mockResolvedValue(account) },
            entitlement: {
              upsert: jest.fn().mockResolvedValue(opts.upsertEnt ?? entitlementNone),
              update: jest.fn().mockResolvedValue(opts.updateEnt ?? entitlementNone),
            },
          });
        }
        // Array form used by startTrial
        return [opts.updateAccount ?? account, opts.updateEnt ?? entitlementNone];
      }),
    };
  }

  describe('getOrInitialize', () => {
    it('upserts a fresh entitlement row and returns it', async () => {
      const prisma = mockPrisma();
      const svc = new EntitlementService(prisma as unknown as PrismaService);

      const ent = await svc.getOrInitialize(account.id);

      expect(prisma.entitlement.upsert).toHaveBeenCalledWith({
        where: { accountId: account.id },
        update: {},
        create: { accountId: account.id, state: 'none' },
      });
      expect(ent).toEqual(entitlementNone);
    });

    it('auto-transitions trial -> expired when trial_ends_at is in the past', async () => {
      const past = new Date(Date.now() - 86_400_000);
      const trialExpired: Entitlement = {
        ...entitlementNone,
        state: 'trial',
        trialStartedAt: new Date(past.getTime() - 14 * 86_400_000),
        trialEndsAt: past,
      };
      const updated: Entitlement = {
        ...trialExpired,
        state: 'expired',
        expiresAt: past,
      };
      const prisma = mockPrisma({ upsertEnt: trialExpired, updateEnt: updated });
      const svc = new EntitlementService(prisma as unknown as PrismaService);

      const ent = await svc.getOrInitialize(account.id);

      expect(prisma.entitlement.update).toHaveBeenCalledWith({
        where: { id: trialExpired.id },
        data: { state: 'expired', expiresAt: past },
      });
      expect(ent.state).toBe('expired');
      expect(ent).toBe(updated);
    });

    it('does NOT auto-expire a trial whose end is still in the future', async () => {
      const future = new Date(Date.now() + 86_400_000);
      const trialActive: Entitlement = {
        ...entitlementNone,
        state: 'trial',
        trialStartedAt: new Date(),
        trialEndsAt: future,
      };
      const prisma = mockPrisma({ upsertEnt: trialActive });
      const svc = new EntitlementService(prisma as unknown as PrismaService);

      await svc.getOrInitialize(account.id);

      expect(prisma.entitlement.update).not.toHaveBeenCalled();
    });
  });

  describe('startTrial', () => {
    it('flips trial_consumed and moves entitlement to trial atomically', async () => {
      const trialEnt: Entitlement = {
        ...entitlementNone,
        state: 'trial',
        trialStartedAt: now,
        trialEndsAt: new Date(now.getTime() + 14 * 86_400_000),
      };
      const prisma = mockPrisma({ updateEnt: trialEnt });
      const svc = new EntitlementService(prisma as unknown as PrismaService);

      const ent = await svc.startTrial(account);

      expect(prisma.$transaction).toHaveBeenCalledTimes(1);
      expect(prisma.account.update).toHaveBeenCalledWith({
        where: { id: account.id },
        data: { trialConsumed: true },
      });
      expect(prisma.entitlement.update).toHaveBeenCalled();
      expect(ent.state).toBe('trial');
    });

    it('returns 402 ENTITLEMENT_REQUIRED if trial already consumed', async () => {
      const consumed = { ...account, trialConsumed: true };
      const prisma = mockPrisma();
      const svc = new EntitlementService(prisma as unknown as PrismaService);

      try {
        await svc.startTrial(consumed);
        fail('expected HttpException');
      } catch (err) {
        expect(err).toBeInstanceOf(HttpException);
        const ex = err as HttpException;
        expect(ex.getStatus()).toBe(HttpStatus.PAYMENT_REQUIRED);
        const body = ex.getResponse() as {
          error: { code: string; message: string; details?: Record<string, unknown> };
        };
        expect(body.error.code).toBe('ENTITLEMENT_REQUIRED');
        expect(body.error.message).toMatch(/already been consumed/);
        expect(body.error.details?.code).toBe('TRIAL_ALREADY_CONSUMED');
      }

      expect(prisma.account.update).not.toHaveBeenCalled();
      expect(prisma.entitlement.update).not.toHaveBeenCalled();
    });

    it('returns 402 when entitlement is not in "none" state', async () => {
      const activeEnt: Entitlement = { ...entitlementNone, state: 'lifetime_single' };
      const prisma = mockPrisma({ upsertEnt: activeEnt });
      const svc = new EntitlementService(prisma as unknown as PrismaService);

      await expect(svc.startTrial(account)).rejects.toBeInstanceOf(HttpException);
      expect(prisma.account.update).not.toHaveBeenCalled();
    });
  });
});

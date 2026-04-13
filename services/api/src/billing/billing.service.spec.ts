import type {
  Account,
  Entitlement,
  Purchase,
} from '@prisma/client';
import type { ConfigService } from '@nestjs/config';
import type { AppConfig } from '../config/configuration';
import { BillingService } from './billing.service';
import type { PrismaService } from '../prisma/prisma.service';
import type { EntitlementService } from '../entitlement/entitlement.service';
import type {
  ProviderVerificationClient,
  VerifiedPurchase,
} from './providers/provider.interface';

describe('BillingService', () => {
  const now = new Date('2026-04-13T12:00:00.000Z');
  const account: Account = {
    id: 'a1111111-1111-1111-1111-111111111111',
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

  function makeEntitlement(state: Entitlement['state']): Entitlement {
    return {
      id: 'e1',
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

  function makePurchase(overrides: Partial<Purchase> = {}): Purchase {
    return {
      id: 'p1',
      accountId: account.id,
      provider: 'google_play',
      productId: 'premium_player_family',
      purchaseToken: 'token-123',
      orderId: 'GPA.xxxx',
      purchaseState: 'purchased',
      purchasedAt: now,
      acknowledgedAt: null,
      rawPayload: { purchaseTimeMillis: '1712000000000' } as object,
      createdAt: now,
      updatedAt: now,
      ...overrides,
    };
  }

  function verified(overrides: Partial<VerifiedPurchase> = {}): VerifiedPurchase {
    return {
      provider: 'google_play',
      productId: 'premium_player_family',
      purchaseToken: 'token-123',
      orderId: 'GPA.xxxx',
      state: 'purchased',
      purchasedAt: now,
      acknowledged: false,
      raw: { purchaseTimeMillis: '1712000000000' } as object as Record<string, unknown>,
      ...overrides,
    };
  }

  function mockProvider(result?: VerifiedPurchase): jest.Mocked<ProviderVerificationClient> {
    return {
      verify: jest.fn().mockResolvedValue(result ?? verified()),
      acknowledge: jest.fn().mockResolvedValue(undefined),
    } as jest.Mocked<ProviderVerificationClient>;
  }

  function mockConfig(): ConfigService<AppConfig, true> {
    const values: Record<string, unknown> = {
      'billing.productIdSingle': 'premium_player_single',
      'billing.productIdFamily': 'premium_player_family',
    };
    return {
      get: jest.fn((k: string) => values[k]),
    } as unknown as ConfigService<AppConfig, true>;
  }

  interface PrismaMock {
    purchase: {
      upsert: jest.Mock;
      findUnique: jest.Mock;
      findMany: jest.Mock;
      update: jest.Mock;
    };
    entitlement: {
      findUnique: jest.Mock;
      findUniqueOrThrow: jest.Mock;
      upsert: jest.Mock;
      update: jest.Mock;
    };
    account: {
      findUniqueOrThrow: jest.Mock;
    };
    $transaction: jest.Mock;
    $queryRaw: jest.Mock;
  }

  function mockPrisma(opts: {
    existingPurchase?: Purchase | null;
    upsertPurchase?: Purchase;
    entitlementBefore?: Entitlement;
    entitlementAfter?: Entitlement;
  } = {}): PrismaMock {
    const upsertPurchase = opts.upsertPurchase ?? makePurchase();
    const entitlementBefore = opts.entitlementBefore ?? makeEntitlement('none');
    const entitlementAfter = opts.entitlementAfter ?? makeEntitlement('lifetime_family');

    const prisma: PrismaMock = {
      purchase: {
        upsert: jest.fn().mockResolvedValue(upsertPurchase),
        findUnique: jest.fn().mockResolvedValue(opts.existingPurchase ?? null),
        findMany: jest.fn().mockResolvedValue([]),
        update: jest.fn().mockResolvedValue({ ...upsertPurchase, acknowledgedAt: now }),
      },
      entitlement: {
        findUnique: jest.fn().mockResolvedValue(entitlementBefore),
        findUniqueOrThrow: jest.fn().mockResolvedValue(entitlementAfter),
        upsert: jest.fn().mockResolvedValue(entitlementBefore),
        update: jest.fn().mockResolvedValue(entitlementAfter),
      },
      account: {
        findUniqueOrThrow: jest.fn().mockResolvedValue(account),
      },
      $transaction: jest.fn(async (cb: (tx: unknown) => unknown) => {
        const tx = {
          purchase: {
            upsert: prisma.purchase.upsert,
          },
          entitlement: {
            upsert: prisma.entitlement.upsert,
            update: prisma.entitlement.update,
            findUniqueOrThrow: prisma.entitlement.findUniqueOrThrow,
          },
          account: {
            findUniqueOrThrow: prisma.account.findUniqueOrThrow,
          },
          $queryRaw: prisma.$queryRaw,
        };
        return cb(tx);
      }),
      $queryRaw: jest.fn().mockResolvedValue([{ id: entitlementBefore.id }]),
    };
    return prisma;
  }

  function makeSvc(opts: {
    prisma?: PrismaMock;
    provider?: jest.Mocked<ProviderVerificationClient>;
  } = {}): {
    svc: BillingService;
    prisma: PrismaMock;
    provider: jest.Mocked<ProviderVerificationClient>;
  } {
    const prisma = opts.prisma ?? mockPrisma();
    const provider = opts.provider ?? mockProvider();
    const entitlement = {
      getOrInitialize: jest.fn().mockResolvedValue(makeEntitlement('none')),
    } as unknown as EntitlementService;
    const svc = new BillingService(
      prisma as unknown as PrismaService,
      entitlement,
      mockConfig(),
      provider,
    );
    return { svc, prisma, provider };
  }

  describe('mapEvent (indirect via verifyAndApply)', () => {
    it('family SKU -> PURCHASE_VERIFIED_FAMILY and drives entitlement.update', async () => {
      const { svc, prisma } = makeSvc();
      const res = await svc.verifyAndApply(account, {
        purchaseToken: 'token-123',
        productId: 'premium_player_family',
      });
      expect(res.event).toBe('PURCHASE_VERIFIED_FAMILY');
      expect(res.entitlement.state).toBe('lifetime_family');
      expect(prisma.entitlement.update).toHaveBeenCalled();
      expect(prisma.$queryRaw).toHaveBeenCalled(); // row-level lock
    });

    it('single SKU -> PURCHASE_VERIFIED_SINGLE', async () => {
      const verifiedSingle = verified({ productId: 'premium_player_single' });
      const provider = mockProvider(verifiedSingle);
      const prisma = mockPrisma({
        upsertPurchase: makePurchase({ productId: 'premium_player_single' }),
        entitlementAfter: makeEntitlement('lifetime_single'),
      });
      const { svc } = makeSvc({ provider, prisma });

      const res = await svc.verifyAndApply(account, {
        purchaseToken: 'token-123',
        productId: 'premium_player_single',
      });
      expect(res.event).toBe('PURCHASE_VERIFIED_SINGLE');
    });

    it('refunded state -> REFUND_OR_REVOKE_ACTIVE_PURCHASE', async () => {
      const provider = mockProvider(verified({ state: 'refunded' }));
      const prisma = mockPrisma({
        entitlementBefore: makeEntitlement('lifetime_family'),
        entitlementAfter: makeEntitlement('expired'),
      });
      const { svc } = makeSvc({ provider, prisma });
      const res = await svc.verifyAndApply(account, {
        purchaseToken: 'token-123',
        productId: 'premium_player_family',
      });
      expect(res.event).toBe('REFUND_OR_REVOKE_ACTIVE_PURCHASE');
    });

    it('pending state -> DUPLICATE_OR_REPLAY_EVENT (no-op)', async () => {
      const provider = mockProvider(verified({ state: 'pending' }));
      const prisma = mockPrisma();
      const { svc } = makeSvc({ provider, prisma });
      const res = await svc.verifyAndApply(account, {
        purchaseToken: 'token-123',
        productId: 'premium_player_family',
      });
      expect(res.event).toBe('DUPLICATE_OR_REPLAY_EVENT');
      expect(prisma.entitlement.update).not.toHaveBeenCalled();
    });

    it('unknown productId -> DUPLICATE_OR_REPLAY_EVENT (no-op)', async () => {
      const provider = mockProvider(verified({ productId: 'totally_unknown_sku' }));
      const prisma = mockPrisma();
      const { svc } = makeSvc({ provider, prisma });
      const res = await svc.verifyAndApply(account, {
        purchaseToken: 'token-123',
        productId: 'totally_unknown_sku',
      });
      expect(res.event).toBe('DUPLICATE_OR_REPLAY_EVENT');
      expect(prisma.entitlement.update).not.toHaveBeenCalled();
    });
  });

  describe('idempotency on replay', () => {
    it('does not re-apply entitlement when purchase is already persisted in the same state AND entitlement already reflects it', async () => {
      const existing = makePurchase();
      const prisma = mockPrisma({
        existingPurchase: existing,
        upsertPurchase: existing,
        entitlementBefore: makeEntitlement('lifetime_family'),
        entitlementAfter: makeEntitlement('lifetime_family'),
      });
      const { svc } = makeSvc({ prisma });

      const res = await svc.verifyAndApply(account, {
        purchaseToken: 'token-123',
        productId: 'premium_player_family',
      });
      expect(res.event).toBe('PURCHASE_VERIFIED_FAMILY');
      // Purchase upsert still happens (idempotent at the DB level), but the
      // entitlement update is skipped because it's a replay.
      expect(prisma.purchase.upsert).toHaveBeenCalledTimes(1);
      expect(prisma.entitlement.update).not.toHaveBeenCalled();
    });

    it('re-applies entitlement when provider state differs from persisted state', async () => {
      // Existing row says "purchased", but provider now says "refunded".
      const existing = makePurchase({ purchaseState: 'purchased' });
      const provider = mockProvider(verified({ state: 'refunded' }));
      const prisma = mockPrisma({
        existingPurchase: existing,
        upsertPurchase: { ...existing, purchaseState: 'refunded' },
        entitlementBefore: makeEntitlement('lifetime_family'),
        entitlementAfter: makeEntitlement('expired'),
      });
      const { svc } = makeSvc({ provider, prisma });
      const res = await svc.verifyAndApply(account, {
        purchaseToken: 'token-123',
        productId: 'premium_player_family',
      });
      expect(res.event).toBe('REFUND_OR_REVOKE_ACTIVE_PURCHASE');
      expect(prisma.entitlement.update).toHaveBeenCalled();
    });
  });

  describe('acknowledgement', () => {
    it('acks on first successful purchased verify and records acknowledgedAt', async () => {
      const { svc, prisma, provider } = makeSvc();
      const res = await svc.verifyAndApply(account, {
        purchaseToken: 'token-123',
        productId: 'premium_player_family',
      });
      expect(provider.acknowledge).toHaveBeenCalledTimes(1);
      expect(prisma.purchase.update).toHaveBeenCalledWith({
        where: { id: 'p1' },
        data: { acknowledgedAt: expect.any(Date) },
      });
      expect(res.acknowledgedNow).toBe(true);
    });

    it('does NOT ack when provider says already acknowledged', async () => {
      const provider = mockProvider(verified({ acknowledged: true }));
      const { svc } = makeSvc({ provider });
      const res = await svc.verifyAndApply(account, {
        purchaseToken: 'token-123',
        productId: 'premium_player_family',
      });
      expect(provider.acknowledge).not.toHaveBeenCalled();
      expect(res.acknowledgedNow).toBe(false);
    });

    it('does NOT ack for refunded/pending purchases', async () => {
      const provider = mockProvider(verified({ state: 'refunded' }));
      const prisma = mockPrisma({
        entitlementBefore: makeEntitlement('lifetime_family'),
        entitlementAfter: makeEntitlement('expired'),
      });
      const { svc } = makeSvc({ provider, prisma });
      await svc.verifyAndApply(account, {
        purchaseToken: 'token-123',
        productId: 'premium_player_family',
      });
      expect(provider.acknowledge).not.toHaveBeenCalled();
    });

    it('tolerates ack failures (worker will retry) without throwing', async () => {
      const provider = mockProvider();
      provider.acknowledge.mockRejectedValueOnce(new Error('network hiccup'));
      const { svc } = makeSvc({ provider });
      await expect(
        svc.verifyAndApply(account, {
          purchaseToken: 'token-123',
          productId: 'premium_player_family',
        }),
      ).resolves.toMatchObject({ acknowledgedNow: false });
    });
  });

  describe('restore', () => {
    it('returns current entitlement when the account has no purchases', async () => {
      const prisma = mockPrisma();
      prisma.purchase.findMany.mockResolvedValue([]);
      const { svc } = makeSvc({ prisma });
      const ent = await svc.restore(account);
      expect(ent).toBeDefined();
      expect(prisma.purchase.findMany).toHaveBeenCalled();
    });

    it('re-verifies each eligible purchase and applies the latest', async () => {
      const p1 = makePurchase({ id: 'p1' });
      const p2 = makePurchase({ id: 'p2', purchaseToken: 'token-B' });
      const prisma = mockPrisma({
        entitlementBefore: makeEntitlement('none'),
        entitlementAfter: makeEntitlement('lifetime_family'),
      });
      prisma.purchase.findMany.mockResolvedValue([p1, p2]);
      const provider = mockProvider();
      const { svc } = makeSvc({ prisma, provider });
      const ent = await svc.restore(account);
      expect(provider.verify).toHaveBeenCalledTimes(2);
      expect(ent.state).toBe('lifetime_family');
    });
  });
});

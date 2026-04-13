import type { Purchase } from '@prisma/client';
import type { ConfigService } from '@nestjs/config';
import type { AppConfig } from '@api/config/configuration';
import type { BillingService } from '@api/billing/billing.service';
import { BillingWorker } from './billing.worker';

describe('BillingWorker', () => {
  function makePurchase(id: string): Purchase {
    return {
      id,
      accountId: `acc-${id}`,
      provider: 'google_play',
      productId: 'premium_player_family',
      purchaseToken: `token-${id}`,
      orderId: null,
      purchaseState: 'purchased',
      purchasedAt: new Date(),
      acknowledgedAt: null,
      rawPayload: {} as object,
      createdAt: new Date(),
      updatedAt: new Date(),
    };
  }

  function mockBilling(): jest.Mocked<Pick<BillingService, 'findWorkBatch' | 'reverify'>> {
    return {
      findWorkBatch: jest.fn().mockResolvedValue([]),
      reverify: jest.fn().mockResolvedValue({
        entitlement: { state: 'lifetime_family' },
        purchase: makePurchase('x'),
        event: 'PURCHASE_VERIFIED_FAMILY',
        acknowledgedNow: false,
      }),
    } as jest.Mocked<Pick<BillingService, 'findWorkBatch' | 'reverify'>>;
  }

  function mockConfig(intervalMs = 15_000): ConfigService<AppConfig, true> {
    return {
      get: jest.fn((k: string) => (k === 'billing.workerPollIntervalMs' ? intervalMs : undefined)),
    } as unknown as ConfigService<AppConfig, true>;
  }

  beforeEach(() => {
    delete process.env.WORKER_RUN_ONCE;
  });

  it('runs a single tick when WORKER_RUN_ONCE=true and exits without scheduling', async () => {
    process.env.WORKER_RUN_ONCE = 'true';
    const billing = mockBilling();
    billing.findWorkBatch.mockResolvedValueOnce([makePurchase('a'), makePurchase('b')]);
    const worker = new BillingWorker(
      billing as unknown as BillingService,
      mockConfig(),
    );

    await worker.start();

    expect(billing.findWorkBatch).toHaveBeenCalledTimes(1);
    expect(billing.reverify).toHaveBeenCalledTimes(2);
  });

  it('no-ops gracefully when batch is empty', async () => {
    process.env.WORKER_RUN_ONCE = 'true';
    const billing = mockBilling();
    const worker = new BillingWorker(
      billing as unknown as BillingService,
      mockConfig(),
    );

    await worker.start();

    expect(billing.findWorkBatch).toHaveBeenCalled();
    expect(billing.reverify).not.toHaveBeenCalled();
  });

  it('continues processing the batch after a per-purchase failure', async () => {
    process.env.WORKER_RUN_ONCE = 'true';
    const billing = mockBilling();
    billing.findWorkBatch.mockResolvedValueOnce([
      makePurchase('a'),
      makePurchase('b'),
      makePurchase('c'),
    ]);
    billing.reverify
      .mockResolvedValueOnce({
        entitlement: { state: 'lifetime_single' } as never,
        purchase: makePurchase('a'),
        event: 'PURCHASE_VERIFIED_SINGLE',
        acknowledgedNow: true,
      })
      .mockRejectedValueOnce(new Error('network blip'))
      .mockResolvedValueOnce({
        entitlement: { state: 'lifetime_family' } as never,
        purchase: makePurchase('c'),
        event: 'PURCHASE_VERIFIED_FAMILY',
        acknowledgedNow: false,
      });

    const worker = new BillingWorker(
      billing as unknown as BillingService,
      mockConfig(),
    );
    await worker.start();

    expect(billing.reverify).toHaveBeenCalledTimes(3);
  });

  it('stops scheduling further ticks after shutdown', async () => {
    process.env.WORKER_RUN_ONCE = 'true';
    const billing = mockBilling();
    const worker = new BillingWorker(
      billing as unknown as BillingService,
      mockConfig(),
    );
    await worker.start();
    await worker.onApplicationShutdown();
    // After shutdown, calling tick-equivalent should not blow up.
    await expect(worker.onApplicationShutdown()).resolves.toBeUndefined();
  });
});

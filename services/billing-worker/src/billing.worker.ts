import { Injectable, Logger, OnApplicationShutdown } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import type { AppConfig } from '@api/config/configuration';
import { BillingService } from '@api/billing/billing.service';

/**
 * Polls the `purchases` table for rows that need re-verification:
 *   - `acknowledgementState = null` + `purchase_state = 'purchased'`
 *     (ack failed earlier; retry within Google's 3-day grace window)
 *   - `purchase_state = 'pending'`
 *     (user's purchase not yet fully processed by Google; re-check)
 *
 * All work goes through `BillingService.reverify()` → `applyVerified()`,
 * which is the exact same path used by `/v1/billing/verify` — so the
 * worker and API cannot diverge.
 *
 * Runs forever unless `WORKER_RUN_ONCE=true` is set (useful for CI /
 * one-shot reconciliation).
 */
@Injectable()
export class BillingWorker implements OnApplicationShutdown {
  private readonly logger = new Logger(BillingWorker.name);
  private timer: NodeJS.Timeout | null = null;
  private shuttingDown = false;
  private running = false;

  constructor(
    private readonly billing: BillingService,
    private readonly config: ConfigService<AppConfig, true>,
  ) {}

  async start(): Promise<void> {
    if (this.running) return;
    this.running = true;

    const runOnce = process.env.WORKER_RUN_ONCE === 'true';
    const interval = this.config.get('billing.workerPollIntervalMs', { infer: true });
    this.logger.log(
      `Billing worker starting (runOnce=${runOnce}, pollIntervalMs=${interval})`,
    );

    await this.tick();
    if (runOnce) {
      this.logger.log('WORKER_RUN_ONCE=true — exiting after first tick');
      return;
    }
    this.scheduleNext(interval);
  }

  private scheduleNext(intervalMs: number): void {
    if (this.shuttingDown) return;
    this.timer = setTimeout(() => {
      void this.tick().finally(() => this.scheduleNext(intervalMs));
    }, intervalMs);
  }

  private async tick(): Promise<void> {
    if (this.shuttingDown) return;
    const batch = await this.billing.findWorkBatch(50);
    if (batch.length === 0) {
      this.logger.debug('No purchases needing reconciliation');
      return;
    }
    this.logger.log(`Reconciling ${batch.length} purchase(s)`);
    for (const p of batch) {
      if (this.shuttingDown) break;
      try {
        const res = await this.billing.reverify(p);
        this.logger.log(
          `  purchase=${p.id} account=${p.accountId} -> ent=${res.entitlement.state} (event=${res.event}, ackedNow=${res.acknowledgedNow})`,
        );
      } catch (err) {
        this.logger.warn(
          `  purchase=${p.id} reverify failed: ${err instanceof Error ? err.message : String(err)}`,
        );
      }
    }
  }

  async onApplicationShutdown(): Promise<void> {
    this.shuttingDown = true;
    if (this.timer) {
      clearTimeout(this.timer);
      this.timer = null;
    }
    this.logger.log('Billing worker shutdown requested');
  }
}

import {
  HttpException,
  HttpStatus,
  Inject,
  Injectable,
  Logger,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import type { Account, Entitlement, Purchase } from '@prisma/client';
import { PrismaService } from '../prisma/prisma.service';
import { EntitlementService } from '../entitlement/entitlement.service';
import { buildErrorEnvelope, ErrorCode } from '../common/errors';
import type { AppConfig } from '../config/configuration';
import {
  PROVIDER_VERIFICATION_CLIENT,
  type ProviderVerificationClient,
  type VerifiedPurchase,
} from './providers/provider.interface';
import type { EntitlementEvent } from '../entitlement/entitlement.state-machine';

export interface BillingVerifyResult {
  entitlement: Entitlement;
  purchase: Purchase;
  event: EntitlementEvent;
  acknowledgedNow: boolean;
}

/**
 * BillingService is the single writer of purchase/entitlement transitions.
 *
 * Responsibilities:
 *  - verify a purchase token with the provider (Google Play)
 *  - idempotently persist a `purchases` row (unique on
 *    (provider, purchase_token))
 *  - acknowledge the purchase with the provider when first seen
 *  - translate the provider result into the correct
 *    EntitlementEvent and drive EntitlementService.applyEvent under a
 *    row-level SELECT ... FOR UPDATE lock on the entitlement row
 *
 * All transitions go through `applyVerified` so the billing-worker and the
 * `/v1/billing/*` API endpoints use the exact same code path.
 */
@Injectable()
export class BillingService {
  private readonly logger = new Logger(BillingService.name);

  constructor(
    private readonly prisma: PrismaService,
    private readonly entitlement: EntitlementService,
    private readonly config: ConfigService<AppConfig, true>,
    @Inject(PROVIDER_VERIFICATION_CLIENT)
    private readonly provider: ProviderVerificationClient,
  ) {}

  /**
   * Verify + persist + acknowledge + drive entitlement. Used by:
   *  - POST /v1/billing/verify (API endpoint, caller = account owner)
   *  - billing-worker polling loop (caller = system)
   */
  async verifyAndApply(
    account: Account,
    input: { purchaseToken: string; productId: string },
  ): Promise<BillingVerifyResult> {
    if (!input.purchaseToken || !input.productId) {
      throw new HttpException(
        buildErrorEnvelope(
          ErrorCode.VALIDATION_ERROR,
          'purchaseToken and productId are required',
        ),
        HttpStatus.BAD_REQUEST,
      );
    }

    const verified = await this.provider.verify({
      productId: input.productId,
      purchaseToken: input.purchaseToken,
    });
    return this.applyVerified(account.id, verified);
  }

  /**
   * Internal path shared with the worker: given a provider-verified result,
   * persist + acknowledge + drive entitlement atomically.
   *
   * Idempotent on (provider, purchase_token): replaying the same purchase
   * token returns the current entitlement without duplicating acknowledgement.
   */
  async applyVerified(
    accountId: string,
    verified: VerifiedPurchase,
  ): Promise<BillingVerifyResult> {
    const event = this.mapEvent(verified);
    const isReplay = await this.isReplay(accountId, verified, event);

    // Transaction scope:
    //   1. upsert purchases row (idempotent on provider+purchase_token)
    //   2. SELECT ... FOR UPDATE on entitlement row (single-writer)
    //   3. apply entitlement event
    const result = await this.prisma.$transaction(async (tx) => {
      const purchase = await tx.purchase.upsert({
        where: {
          provider_purchaseToken: {
            provider: verified.provider,
            purchaseToken: verified.purchaseToken,
          },
        },
        update: {
          productId: verified.productId,
          orderId: verified.orderId,
          purchaseState: verified.state,
          purchasedAt: verified.purchasedAt,
          rawPayload: verified.raw as object,
        },
        create: {
          accountId,
          provider: verified.provider,
          productId: verified.productId,
          purchaseToken: verified.purchaseToken,
          orderId: verified.orderId,
          purchaseState: verified.state,
          purchasedAt: verified.purchasedAt,
          rawPayload: verified.raw as object,
        },
      });

      // Lock the account's entitlement row to serialize writers.
      await tx.$queryRaw`SELECT id FROM entitlements WHERE account_id = ${accountId}::uuid FOR UPDATE`;

      const entitlement = isReplay
        ? await tx.entitlement.findUniqueOrThrow({ where: { accountId } })
        : await this.applyEventWithin(tx, accountId, event, purchase.id);

      return { purchase, entitlement };
    });

    // Acknowledge AFTER the DB write so we don't ack something we couldn't
    // persist. We only ack on the first successful verify (i.e. provider
    // says we haven't yet, AND it is a purchased state).
    let acknowledgedNow = false;
    if (
      verified.state === 'purchased' &&
      !verified.acknowledged
    ) {
      try {
        await this.provider.acknowledge({
          productId: verified.productId,
          purchaseToken: verified.purchaseToken,
        });
        await this.prisma.purchase.update({
          where: { id: result.purchase.id },
          data: { acknowledgedAt: new Date() },
        });
        acknowledgedNow = true;
      } catch (err) {
        // Non-fatal: Google has a 3-day grace window, worker will retry.
        this.logger.warn(
          `Acknowledge failed for purchase=${result.purchase.id}: ${
            err instanceof Error ? err.message : String(err)
          }. Worker will retry.`,
        );
      }
    }

    this.logger.log(
      `Billing: account=${accountId} purchase=${result.purchase.id} event=${event} replay=${isReplay} ackedNow=${acknowledgedNow}`,
    );

    return {
      entitlement: result.entitlement,
      purchase: result.purchase,
      event,
      acknowledgedNow,
    };
  }

  /**
   * Re-apply all known non-refunded purchases for the account.
   * Used by POST /v1/billing/restore. Each purchase is re-verified via
   * the provider (so server stays authoritative about refund state) and
   * re-applied to the entitlement row.
   */
  async restore(account: Account): Promise<Entitlement> {
    const purchases = await this.prisma.purchase.findMany({
      where: {
        accountId: account.id,
        // Skip already-refunded rows — they've already driven their terminal
        // transition. Providers don't un-refund.
        purchaseState: { in: ['purchased', 'pending'] },
      },
      orderBy: { createdAt: 'asc' },
    });

    if (purchases.length === 0) {
      this.logger.log(`Restore: no verifiable purchases for account=${account.id}`);
      return this.entitlement.getOrInitialize(account.id);
    }

    let latest: Entitlement | null = null;
    for (const p of purchases) {
      try {
        const verified = await this.provider.verify({
          productId: p.productId,
          purchaseToken: p.purchaseToken,
        });
        const result = await this.applyVerified(account.id, verified);
        latest = result.entitlement;
      } catch (err) {
        this.logger.warn(
          `Restore: verify failed for purchase=${p.id}: ${
            err instanceof Error ? err.message : String(err)
          }`,
        );
      }
    }

    return latest ?? this.entitlement.getOrInitialize(account.id);
  }

  /**
   * For the billing worker: returns pending or purchased-but-unacked
   * purchase rows that should be re-verified. Bounded batch size.
   */
  async findWorkBatch(limit = 50): Promise<Purchase[]> {
    return this.prisma.purchase.findMany({
      where: {
        OR: [
          { acknowledgedAt: null, purchaseState: 'purchased' },
          { purchaseState: 'pending' },
        ],
      },
      orderBy: { createdAt: 'asc' },
      take: limit,
    });
  }

  /** Process a single purchase row — used by the worker. */
  async reverify(purchase: Purchase): Promise<BillingVerifyResult> {
    const verified = await this.provider.verify({
      productId: purchase.productId,
      purchaseToken: purchase.purchaseToken,
    });
    return this.applyVerified(purchase.accountId, verified);
  }

  private mapEvent(verified: VerifiedPurchase): EntitlementEvent {
    const single = this.config.get('billing.productIdSingle', { infer: true });
    const family = this.config.get('billing.productIdFamily', { infer: true });

    if (verified.state === 'refunded' || verified.state === 'voided') {
      return 'REFUND_OR_REVOKE_ACTIVE_PURCHASE';
    }
    if (verified.state === 'pending') {
      // Pending = no-op for entitlement. Worker will re-verify later.
      return 'DUPLICATE_OR_REPLAY_EVENT';
    }
    if (verified.productId === family) {
      return 'PURCHASE_VERIFIED_FAMILY';
    }
    if (verified.productId === single) {
      return 'PURCHASE_VERIFIED_SINGLE';
    }
    // Unknown SKU — treat as duplicate/no-op to avoid corrupting entitlement.
    this.logger.warn(
      `Unknown productId "${verified.productId}"; treating as replay (no-op).`,
    );
    return 'DUPLICATE_OR_REPLAY_EVENT';
  }

  /**
   * A transition is a "replay" (no-op) when we've already persisted the same
   * purchase token in a state that matches what we just verified AND the
   * entitlement already reflects the resulting state. This stops duplicate
   * webhook deliveries from churning the entitlement row.
   */
  private async isReplay(
    accountId: string,
    verified: VerifiedPurchase,
    event: EntitlementEvent,
  ): Promise<boolean> {
    if (event === 'DUPLICATE_OR_REPLAY_EVENT') return true;

    const existing = await this.prisma.purchase.findUnique({
      where: {
        provider_purchaseToken: {
          provider: verified.provider,
          purchaseToken: verified.purchaseToken,
        },
      },
    });
    if (!existing) return false;

    if (existing.purchaseState !== verified.state) return false;

    // Same state as before — check the entitlement already reflects it.
    const ent = await this.prisma.entitlement.findUnique({ where: { accountId } });
    if (!ent) return false;

    const expectedState = this.expectedEntitlementState(event);
    if (!expectedState) return false;
    return ent.state === expectedState;
  }

  private expectedEntitlementState(
    event: EntitlementEvent,
  ): 'lifetime_single' | 'lifetime_family' | null {
    if (event === 'PURCHASE_VERIFIED_SINGLE') return 'lifetime_single';
    if (event === 'PURCHASE_VERIFIED_FAMILY') return 'lifetime_family';
    return null;
  }

  /** Shared with EntitlementService.applyEvent but scoped to the passed tx. */
  private async applyEventWithin(
    tx: Parameters<Parameters<PrismaService['$transaction']>[0]>[0],
    accountId: string,
    event: EntitlementEvent,
    purchaseId: string,
  ): Promise<Entitlement> {
    const account = await tx.account.findUniqueOrThrow({ where: { id: accountId } });
    const current = await tx.entitlement.upsert({
      where: { accountId },
      update: {},
      create: { accountId, state: 'none' },
    });
    const { transition } = await import('../entitlement/entitlement.state-machine');
    const result = transition(current.state, event, {
      trialConsumed: account.trialConsumed,
      trialEndsAt: current.trialEndsAt,
      purchaseId,
    });
    if (result.noop) return current;
    return tx.entitlement.update({
      where: { id: current.id },
      data: {
        state: result.mutation.state,
        activatedAt: result.mutation.activatedAt ?? current.activatedAt,
        expiresAt:
          result.mutation.expiresAt === undefined
            ? current.expiresAt
            : result.mutation.expiresAt,
        revokedAt:
          result.mutation.revokedAt === undefined
            ? current.revokedAt
            : result.mutation.revokedAt,
        revokeReason:
          result.mutation.revokeReason === undefined
            ? current.revokeReason
            : result.mutation.revokeReason,
        sourcePurchaseId:
          result.mutation.sourcePurchaseId === undefined
            ? current.sourcePurchaseId
            : result.mutation.sourcePurchaseId,
      },
    });
  }
}

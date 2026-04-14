import {
  HttpException,
  HttpStatus,
  Injectable,
  Logger,
} from '@nestjs/common';
import type { Account, Entitlement } from '@prisma/client';
import { PrismaService } from '../prisma/prisma.service';
import { buildErrorEnvelope, ErrorCode } from '../common/errors';
import {
  transition,
  TransitionError,
  type EntitlementEvent,
  type TransitionContext,
} from './entitlement.state-machine';

@Injectable()
export class EntitlementService {
  private readonly logger = new Logger(EntitlementService.name);

  constructor(private readonly prisma: PrismaService) {}

  /**
   * Return the account's entitlement row. If TRIAL_EXPIRED is reached on read,
   * transition it atomically (read-time transition per spec).
   */
  async getOrInitialize(accountId: string): Promise<Entitlement> {
    const existing = await this.prisma.entitlement.upsert({
      where: { accountId },
      update: {},
      create: { accountId, state: 'none' },
    });

    // Read-time trial expiry handling.
    if (
      existing.state === 'trial' &&
      existing.trialEndsAt &&
      existing.trialEndsAt.getTime() <= Date.now()
    ) {
      return this.expireTrial(existing);
    }
    return existing;
  }

  private async expireTrial(ent: Entitlement): Promise<Entitlement> {
    return this.prisma.entitlement.update({
      where: { id: ent.id },
      data: {
        state: 'expired',
        expiresAt: ent.trialEndsAt,
      },
    });
  }

  /**
   * Start a 14-day trial. Consume-once: if account.trial_consumed=true, or
   * entitlement is not in `none`, respond with ENTITLEMENT_REQUIRED.
   */
  async startTrial(account: Account): Promise<Entitlement> {
    const current = await this.getOrInitialize(account.id);

    try {
      const result = transition(current.state, 'TRIAL_STARTED', {
        trialConsumed: account.trialConsumed,
      });

      // Atomic: flip trial_consumed on account + mutate entitlement row.
      const [, ent] = await this.prisma.$transaction([
        this.prisma.account.update({
          where: { id: account.id },
          data: { trialConsumed: true },
        }),
        this.prisma.entitlement.update({
          where: { id: current.id },
          data: {
            state: result.mutation.state,
            trialStartedAt: result.mutation.trialStartedAt,
            trialEndsAt: result.mutation.trialEndsAt,
            activatedAt: result.mutation.activatedAt,
            expiresAt: result.mutation.expiresAt,
            revokedAt: result.mutation.revokedAt,
            revokeReason: result.mutation.revokeReason,
          },
        }),
      ]);
      this.logger.log(
        `Started 14-day trial for account=${account.id} (ends ${result.mutation.trialEndsAt?.toISOString()})`,
      );
      return ent;
    } catch (err) {
      if (err instanceof TransitionError) {
        throw new HttpException(
          buildErrorEnvelope(
            ErrorCode.ENTITLEMENT_REQUIRED,
            err.code === 'TRIAL_ALREADY_CONSUMED'
              ? 'Trial has already been consumed for this account.'
              : err.message,
            { code: err.code },
          ),
          HttpStatus.PAYMENT_REQUIRED,
        );
      }
      throw err;
    }
  }

  /**
   * Apply a billing/admin event to the entitlement row. Used by the
   * billing-worker (Run 9) and admin tools. Idempotent on
   * DUPLICATE_OR_REPLAY_EVENT.
   */
  async applyEvent(
    accountId: string,
    event: EntitlementEvent,
    ctx: Omit<TransitionContext, 'trialEndsAt'> = { trialConsumed: false },
  ): Promise<Entitlement> {
    return this.prisma.$transaction(async (tx) => {
      const account = await tx.account.findUniqueOrThrow({
        where: { id: accountId },
      });
      const current = await tx.entitlement.upsert({
        where: { accountId },
        update: {},
        create: { accountId, state: 'none' },
      });

      const result = transition(current.state, event, {
        ...ctx,
        trialConsumed: account.trialConsumed,
        trialEndsAt: current.trialEndsAt,
      });

      if (result.noop) return current;

      return tx.entitlement.update({
        where: { id: current.id },
        data: {
          state: result.mutation.state,
          trialStartedAt: result.mutation.trialStartedAt ?? current.trialStartedAt,
          trialEndsAt: result.mutation.trialEndsAt ?? current.trialEndsAt,
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
    });
  }
}

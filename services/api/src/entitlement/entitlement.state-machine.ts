import type { EntitlementState } from '@prisma/client';

/**
 * Deterministic entitlement state machine — pure TypeScript, no DB.
 * Mirrors docs/architecture/entitlement-state-machine.md (Run 5).
 *
 * Usage:
 *   const next = transition(current, 'TRIAL_STARTED', { trialConsumed: false });
 *   -> { nextState, mutation }  // or throws TransitionError
 *
 * `mutation` describes the entitlement-row patch the caller must persist
 * atomically together with any other side effects (trial_consumed flag on
 * the account, purchase link, audit row, etc).
 */

export type EntitlementEvent =
  | 'TRIAL_STARTED'
  | 'TRIAL_EXPIRED'
  | 'PURCHASE_VERIFIED_SINGLE'
  | 'PURCHASE_VERIFIED_FAMILY'
  | 'REFUND_OR_REVOKE_ACTIVE_PURCHASE'
  | 'ADMIN_REVOKE'
  | 'DUPLICATE_OR_REPLAY_EVENT';

export interface TransitionContext {
  /** Whether `accounts.trial_consumed` is already true. */
  trialConsumed: boolean;
  /** Optional purchase id to link as `entitlements.source_purchase_id`. */
  purchaseId?: string;
  /** Optional revoke reason (for ADMIN_REVOKE / refund). */
  revokeReason?: string;
  /** Current trial end (for TRIAL_EXPIRED guard; defaults to now()). */
  now?: Date;
  trialEndsAt?: Date | null;
  /** Hook for R-7 decision — if the account never consumed trial, refund falls back to `none` instead of `expired`. */
}

export interface EntitlementMutation {
  state?: EntitlementState;
  trialStartedAt?: Date | null;
  trialEndsAt?: Date | null;
  activatedAt?: Date | null;
  expiresAt?: Date | null;
  revokedAt?: Date | null;
  revokeReason?: string | null;
  sourcePurchaseId?: string | null;
  /** Side-effect: should the caller flip accounts.trial_consumed=true? */
  consumeTrialFlag?: boolean;
}

export interface TransitionResult {
  nextState: EntitlementState;
  mutation: EntitlementMutation;
  /** true when the event is a no-op (idempotency / replay). */
  noop: boolean;
}

export class TransitionError extends Error {
  constructor(
    public readonly code:
      | 'TRIAL_ALREADY_CONSUMED'
      | 'INVALID_TRANSITION'
      | 'GUARD_FAILED'
      | 'TRIAL_NOT_EXPIRED_YET',
    message: string,
  ) {
    super(message);
    this.name = 'TransitionError';
  }
}

export const TRIAL_DURATION_DAYS = 14;

export function trialEndFromStart(start: Date): Date {
  const end = new Date(start.getTime());
  end.setUTCDate(end.getUTCDate() + TRIAL_DURATION_DAYS);
  return end;
}

export function transition(
  current: EntitlementState,
  event: EntitlementEvent,
  ctx: TransitionContext,
): TransitionResult {
  // Idempotency / replay short-circuit is explicit.
  if (event === 'DUPLICATE_OR_REPLAY_EVENT') {
    return { nextState: current, mutation: {}, noop: true };
  }

  const now = ctx.now ?? new Date();

  switch (event) {
    case 'TRIAL_STARTED': {
      if (current !== 'none') {
        throw new TransitionError(
          'INVALID_TRANSITION',
          `Cannot start trial from state "${current}"; only "none" allows trial start.`,
        );
      }
      if (ctx.trialConsumed) {
        throw new TransitionError(
          'TRIAL_ALREADY_CONSUMED',
          'Trial has already been consumed for this account (consume-once rule R-3).',
        );
      }
      const trialEndsAt = trialEndFromStart(now);
      return {
        nextState: 'trial',
        mutation: {
          state: 'trial',
          trialStartedAt: now,
          trialEndsAt,
          activatedAt: null,
          expiresAt: null,
          revokedAt: null,
          revokeReason: null,
          consumeTrialFlag: true,
        },
        noop: false,
      };
    }

    case 'TRIAL_EXPIRED': {
      if (current !== 'trial') {
        throw new TransitionError(
          'INVALID_TRANSITION',
          `TRIAL_EXPIRED only applies to state "trial" (got "${current}").`,
        );
      }
      if (!ctx.trialEndsAt || now < ctx.trialEndsAt) {
        throw new TransitionError(
          'TRIAL_NOT_EXPIRED_YET',
          'Trial has not reached trial_ends_at yet.',
        );
      }
      return {
        nextState: 'expired',
        mutation: {
          state: 'expired',
          expiresAt: ctx.trialEndsAt,
        },
        noop: false,
      };
    }

    case 'PURCHASE_VERIFIED_SINGLE':
    case 'PURCHASE_VERIFIED_FAMILY': {
      const target: EntitlementState =
        event === 'PURCHASE_VERIFIED_SINGLE' ? 'lifetime_single' : 'lifetime_family';

      if (!ctx.purchaseId) {
        throw new TransitionError(
          'GUARD_FAILED',
          'purchaseId is required for PURCHASE_VERIFIED_* transitions.',
        );
      }

      // Accept from any state that the spec allows.
      const allowedFrom: EntitlementState[] =
        target === 'lifetime_family'
          ? ['none', 'trial', 'lifetime_single', 'expired', 'revoked']
          : ['none', 'trial', 'expired', 'revoked'];

      if (!allowedFrom.includes(current)) {
        throw new TransitionError(
          'INVALID_TRANSITION',
          `Cannot transition from "${current}" to "${target}".`,
        );
      }

      return {
        nextState: target,
        mutation: {
          state: target,
          activatedAt: now,
          expiresAt: null,
          revokedAt: null,
          revokeReason: null,
          sourcePurchaseId: ctx.purchaseId,
        },
        noop: false,
      };
    }

    case 'REFUND_OR_REVOKE_ACTIVE_PURCHASE': {
      if (current !== 'lifetime_single' && current !== 'lifetime_family') {
        throw new TransitionError(
          'INVALID_TRANSITION',
          `REFUND_OR_REVOKE_ACTIVE_PURCHASE requires active lifetime state (got "${current}").`,
        );
      }
      // R-7: fallback to `expired` if trial was consumed, otherwise `none`.
      const fallback: EntitlementState = ctx.trialConsumed ? 'expired' : 'none';
      return {
        nextState: fallback,
        mutation: {
          state: fallback,
          revokedAt: now,
          revokeReason: ctx.revokeReason ?? 'refund_or_revoke',
          // Intentionally keep sourcePurchaseId for audit; callers can clear later.
        },
        noop: false,
      };
    }

    case 'ADMIN_REVOKE': {
      if (current !== 'expired') {
        throw new TransitionError(
          'INVALID_TRANSITION',
          `ADMIN_REVOKE only applies to state "expired" (got "${current}"); per spec, explicit revocation flows through expired first.`,
        );
      }
      return {
        nextState: 'revoked',
        mutation: {
          state: 'revoked',
          revokedAt: now,
          revokeReason: ctx.revokeReason ?? 'admin_revoke',
        },
        noop: false,
      };
    }

    default: {
      const exhaustive: never = event;
      throw new TransitionError(
        'INVALID_TRANSITION',
        `Unknown event: ${String(exhaustive)}`,
      );
    }
  }
}

/**
 * Maximum active devices per entitlement state. Enforced by DevicesService.
 */
export function deviceCapFor(state: EntitlementState): number {
  switch (state) {
    case 'trial':
    case 'lifetime_single':
      return 1;
    case 'lifetime_family':
      return 5;
    case 'none':
    case 'expired':
    case 'revoked':
      return 0;
  }
}

/** True if the state currently allows playback / protected resource access. */
export function allowsPlayback(state: EntitlementState): boolean {
  return state === 'trial' || state === 'lifetime_single' || state === 'lifetime_family';
}

import {
  allowsPlayback,
  deviceCapFor,
  profileCapFor,
  trialEndFromStart,
  transition,
  TransitionError,
  TRIAL_DURATION_DAYS,
} from './entitlement.state-machine';

describe('entitlement state machine', () => {
  const now = new Date('2026-04-13T12:00:00.000Z');
  const later = new Date('2026-05-01T00:00:00.000Z');

  describe('TRIAL_STARTED', () => {
    it('transitions none -> trial with a 14-day window', () => {
      const r = transition('none', 'TRIAL_STARTED', { trialConsumed: false, now });
      expect(r.nextState).toBe('trial');
      expect(r.noop).toBe(false);
      expect(r.mutation.state).toBe('trial');
      expect(r.mutation.trialStartedAt).toEqual(now);
      expect(r.mutation.trialEndsAt).toEqual(trialEndFromStart(now));
      const diffDays =
        (r.mutation.trialEndsAt!.getTime() - now.getTime()) / (1000 * 60 * 60 * 24);
      expect(diffDays).toBe(TRIAL_DURATION_DAYS);
      expect(r.mutation.consumeTrialFlag).toBe(true);
    });

    it('throws TRIAL_ALREADY_CONSUMED when account already consumed trial', () => {
      expect(() =>
        transition('none', 'TRIAL_STARTED', { trialConsumed: true, now }),
      ).toThrow(TransitionError);
      try {
        transition('none', 'TRIAL_STARTED', { trialConsumed: true, now });
      } catch (e) {
        expect((e as TransitionError).code).toBe('TRIAL_ALREADY_CONSUMED');
      }
    });

    it.each(['trial', 'lifetime_single', 'lifetime_family', 'expired', 'revoked'] as const)(
      'refuses to start trial from %s',
      (from) => {
        expect(() =>
          transition(from, 'TRIAL_STARTED', { trialConsumed: false, now }),
        ).toThrow(/INVALID_TRANSITION|Cannot start trial/);
      },
    );
  });

  describe('TRIAL_EXPIRED', () => {
    it('trial -> expired when now >= trial_ends_at', () => {
      const trialEndsAt = new Date('2026-04-13T12:00:00.000Z');
      const r = transition('trial', 'TRIAL_EXPIRED', {
        trialConsumed: true,
        now: trialEndsAt,
        trialEndsAt,
      });
      expect(r.nextState).toBe('expired');
      expect(r.mutation.expiresAt).toEqual(trialEndsAt);
    });

    it('throws when trial has not expired yet', () => {
      expect(() =>
        transition('trial', 'TRIAL_EXPIRED', {
          trialConsumed: true,
          now,
          trialEndsAt: later,
        }),
      ).toThrow(/not reached trial_ends_at/);
    });

    it('rejects from non-trial states', () => {
      expect(() =>
        transition('none', 'TRIAL_EXPIRED', { trialConsumed: false, now }),
      ).toThrow(/only applies to state "trial"/);
    });
  });

  describe('PURCHASE_VERIFIED_SINGLE', () => {
    it.each(['none', 'trial', 'expired', 'revoked'] as const)(
      'transitions %s -> lifetime_single',
      (from) => {
        const r = transition(from, 'PURCHASE_VERIFIED_SINGLE', {
          trialConsumed: true,
          now,
          purchaseId: 'p1',
        });
        expect(r.nextState).toBe('lifetime_single');
        expect(r.mutation.activatedAt).toEqual(now);
        expect(r.mutation.sourcePurchaseId).toBe('p1');
        expect(r.mutation.expiresAt).toBeNull();
        expect(r.mutation.revokedAt).toBeNull();
      },
    );

    it('rejects lifetime_single -> lifetime_single (no downgrade/same-state)', () => {
      expect(() =>
        transition('lifetime_single', 'PURCHASE_VERIFIED_SINGLE', {
          trialConsumed: true,
          now,
          purchaseId: 'p1',
        }),
      ).toThrow(/Cannot transition from "lifetime_single" to "lifetime_single"/);
    });

    it('requires purchaseId', () => {
      expect(() =>
        transition('none', 'PURCHASE_VERIFIED_SINGLE', { trialConsumed: false, now }),
      ).toThrow(/purchaseId is required/);
    });
  });

  describe('PURCHASE_VERIFIED_FAMILY', () => {
    it('allows upgrade lifetime_single -> lifetime_family', () => {
      const r = transition('lifetime_single', 'PURCHASE_VERIFIED_FAMILY', {
        trialConsumed: true,
        now,
        purchaseId: 'p2',
      });
      expect(r.nextState).toBe('lifetime_family');
    });

    it.each(['none', 'trial', 'expired', 'revoked'] as const)(
      'transitions %s -> lifetime_family',
      (from) => {
        const r = transition(from, 'PURCHASE_VERIFIED_FAMILY', {
          trialConsumed: true,
          now,
          purchaseId: 'p2',
        });
        expect(r.nextState).toBe('lifetime_family');
      },
    );
  });

  describe('REFUND_OR_REVOKE_ACTIVE_PURCHASE', () => {
    it('lifetime_single -> expired when trial was consumed (R-7)', () => {
      const r = transition('lifetime_single', 'REFUND_OR_REVOKE_ACTIVE_PURCHASE', {
        trialConsumed: true,
        now,
      });
      expect(r.nextState).toBe('expired');
      expect(r.mutation.revokedAt).toEqual(now);
      expect(r.mutation.revokeReason).toBe('refund_or_revoke');
    });

    it('lifetime_single -> none when trial was never consumed (R-7 fallback)', () => {
      const r = transition('lifetime_single', 'REFUND_OR_REVOKE_ACTIVE_PURCHASE', {
        trialConsumed: false,
        now,
      });
      expect(r.nextState).toBe('none');
    });

    it('lifetime_family -> expired when trial was consumed', () => {
      const r = transition('lifetime_family', 'REFUND_OR_REVOKE_ACTIVE_PURCHASE', {
        trialConsumed: true,
        now,
      });
      expect(r.nextState).toBe('expired');
    });

    it('rejects from non-lifetime states', () => {
      expect(() =>
        transition('none', 'REFUND_OR_REVOKE_ACTIVE_PURCHASE', {
          trialConsumed: false,
          now,
        }),
      ).toThrow(/requires active lifetime state/);
    });
  });

  describe('ADMIN_REVOKE', () => {
    it('expired -> revoked', () => {
      const r = transition('expired', 'ADMIN_REVOKE', {
        trialConsumed: true,
        now,
        revokeReason: 'chargeback',
      });
      expect(r.nextState).toBe('revoked');
      expect(r.mutation.revokeReason).toBe('chargeback');
    });

    it('refuses to jump directly from lifetime_* to revoked', () => {
      expect(() =>
        transition('lifetime_single', 'ADMIN_REVOKE', { trialConsumed: true, now }),
      ).toThrow(/only applies to state "expired"/);
    });
  });

  describe('DUPLICATE_OR_REPLAY_EVENT', () => {
    it.each(['none', 'trial', 'lifetime_single', 'lifetime_family', 'expired', 'revoked'] as const)(
      'is a no-op from %s',
      (from) => {
        const r = transition(from, 'DUPLICATE_OR_REPLAY_EVENT', {
          trialConsumed: true,
          now,
        });
        expect(r.nextState).toBe(from);
        expect(r.noop).toBe(true);
        expect(r.mutation).toEqual({});
      },
    );
  });

  describe('derived helpers', () => {
    it('deviceCapFor matches spec table', () => {
      expect(deviceCapFor('none')).toBe(0);
      expect(deviceCapFor('trial')).toBe(1);
      expect(deviceCapFor('lifetime_single')).toBe(1);
      expect(deviceCapFor('lifetime_family')).toBe(5);
      expect(deviceCapFor('expired')).toBe(0);
      expect(deviceCapFor('revoked')).toBe(0);
    });

    it('profileCapFor matches spec table', () => {
      expect(profileCapFor('none')).toBe(0);
      expect(profileCapFor('trial')).toBe(1);
      expect(profileCapFor('lifetime_single')).toBe(1);
      expect(profileCapFor('lifetime_family')).toBe(5);
      expect(profileCapFor('expired')).toBe(0);
      expect(profileCapFor('revoked')).toBe(0);
    });

    it('allowsPlayback only for active states', () => {
      expect(allowsPlayback('trial')).toBe(true);
      expect(allowsPlayback('lifetime_single')).toBe(true);
      expect(allowsPlayback('lifetime_family')).toBe(true);
      expect(allowsPlayback('none')).toBe(false);
      expect(allowsPlayback('expired')).toBe(false);
      expect(allowsPlayback('revoked')).toBe(false);
    });
  });
});

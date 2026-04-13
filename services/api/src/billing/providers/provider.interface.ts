/**
 * Abstraction over billing provider token verification.
 *
 * Keeps BillingService decoupled from Google Play (or any future provider)
 * internals and allows tests to substitute a deterministic stub without
 * hitting the network.
 */

export type NormalizedPurchaseState =
  | 'purchased'
  | 'pending'
  | 'refunded'
  | 'voided';

export interface VerifiedPurchase {
  /** Provider identifier (stable); matches `purchases.provider`. */
  provider: 'google_play';
  /** Product SKU configured in the provider console. */
  productId: string;
  /** Provider-side purchase token — idempotency key in combination with provider. */
  purchaseToken: string;
  /** Provider-side order id (Google Play `orderId` / `obfuscatedAccountId`). */
  orderId: string | null;
  /** Normalized lifecycle state derived from provider fields. */
  state: NormalizedPurchaseState;
  /** When the purchase was originally acknowledged by the user / store. */
  purchasedAt: Date | null;
  /** True if the provider has already received our acknowledgement. */
  acknowledged: boolean;
  /** Full provider response for audit / replay. */
  raw: Record<string, unknown>;
}

export interface ProviderVerificationClient {
  verify(args: {
    productId: string;
    purchaseToken: string;
  }): Promise<VerifiedPurchase>;

  /**
   * Acknowledge a purchase with the provider (Google requires this within a
   * 3-day grace window; unacked purchases auto-refund).
   * Must be idempotent at the provider side — we call this at most once per
   * successful verification.
   */
  acknowledge(args: {
    productId: string;
    purchaseToken: string;
  }): Promise<void>;
}

/**
 * DI token for the ProviderVerificationClient. Bound to GooglePlayProvider
 * in BillingModule by default; tests override via
 *   `.overrideProvider(PROVIDER_VERIFICATION_CLIENT).useValue(stub)`.
 */
export const PROVIDER_VERIFICATION_CLIENT = Symbol('ProviderVerificationClient');

/**
 * Map normalized purchase state into the persisted `purchases.purchase_state`
 * column, which mirrors Google Play's lifecycle strings (we settled on the
 * same vocabulary in Run 3).
 */
export function purchaseStateColumn(state: NormalizedPurchaseState): string {
  return state;
}

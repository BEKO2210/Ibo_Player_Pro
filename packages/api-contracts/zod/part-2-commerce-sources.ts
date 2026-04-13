/**
 * Zod schemas — Part 2 (Commerce & Sources).
 * Mirrors packages/api-contracts/openapi/part-2-commerce-sources.yaml.
 */
import { z } from 'zod';
import { Iso8601, Uuid } from './common';

// ─── Entitlement ───────────────────────────────────────────────────────────

export const EntitlementState = z.enum([
  'none',
  'trial',
  'lifetime_single',
  'lifetime_family',
  'expired',
  'revoked',
]);
export type EntitlementState = z.infer<typeof EntitlementState>;

export const Entitlement = z.object({
  account_id: Uuid,
  state: EntitlementState,
  trial_started_at: Iso8601.nullable(),
  trial_expires_at: Iso8601.nullable(),
  lifetime_activated_at: Iso8601.nullable(),
  max_profiles: z.number().int().min(1).max(5),
  max_devices: z.number().int().min(1).max(5),
  revoked_reason: z.string().nullable(),
  source_purchase_id: Uuid.nullable(),
  updated_at: Iso8601,
});
export type Entitlement = z.infer<typeof Entitlement>;

// ─── Billing ───────────────────────────────────────────────────────────────

export const PurchaseRail = z.enum(['google_play', 'apple_iap', 'stripe']);
export const PurchaseState = z.enum([
  'pending',
  'verified',
  'acknowledged',
  'refunded',
  'revoked',
  'failed',
]);

export const Purchase = z.object({
  id: Uuid,
  rail: PurchaseRail,
  product_id: z.string(),
  order_id: z.string().nullable(),
  state: PurchaseState,
  amount_cents: z.number().int().nullable(),
  currency: z.string().length(3).nullable(),
  country_code: z.string().length(2).nullable(),
  verified_at: Iso8601.nullable(),
  acknowledged_at: Iso8601.nullable(),
  refunded_at: Iso8601.nullable(),
  created_at: Iso8601,
});
export type Purchase = z.infer<typeof Purchase>;

export const GooglePlayVerify = z.object({
  product_id: z.string().min(1),
  purchase_token: z.string().min(1),
  order_id: z.string().optional(),
});
export type GooglePlayVerify = z.infer<typeof GooglePlayVerify>;

export const RestoreRequest = z.object({
  purchase_tokens: z.array(z.string()).optional(),
});
export type RestoreRequest = z.infer<typeof RestoreRequest>;

export const BillingVerifyResponse = z.object({
  purchase: Purchase,
  entitlement: Entitlement,
});
export type BillingVerifyResponse = z.infer<typeof BillingVerifyResponse>;

export const BillingRestoreResponse = z.object({
  purchases: z.array(Purchase),
  entitlement: Entitlement,
});
export type BillingRestoreResponse = z.infer<typeof BillingRestoreResponse>;

// ─── Sources ───────────────────────────────────────────────────────────────

export const SourceKind = z.enum(['m3u', 'm3u8', 'xmltv']);
export type SourceKind = z.infer<typeof SourceKind>;

export const Source = z.object({
  id: Uuid,
  account_id: Uuid,
  label: z.string(),
  kind: SourceKind,
  url_masked: z.string().optional(),
  user_agent: z.string().nullable(),
  refresh_interval_seconds: z.number().int().min(900),
  last_fetched_at: Iso8601.nullable(),
  last_fetch_status: z.string().nullable(),
  last_fetch_error: z.string().nullable(),
  has_credentials: z.boolean(),
  created_at: Iso8601,
  updated_at: Iso8601,
});
export type Source = z.infer<typeof Source>;

const SourceCredentials = z
  .object({
    username: z.string().optional(),
    password: z.string().optional(),
  })
  .nullable();

export const SourceCreate = z.object({
  label: z.string().max(80),
  kind: SourceKind,
  url: z.string().url(),
  user_agent: z.string().optional(),
  refresh_interval_seconds: z.number().int().min(900).default(21600),
  credentials: SourceCredentials.optional(),
});
export type SourceCreate = z.infer<typeof SourceCreate>;

export const SourceUpdate = z
  .object({
    label: z.string().max(80),
    url: z.string().url(),
    user_agent: z.string(),
    refresh_interval_seconds: z.number().int().min(900),
    credentials: SourceCredentials,
  })
  .partial();
export type SourceUpdate = z.infer<typeof SourceUpdate>;

export const SourceRefreshResponse = z.object({ job_id: z.string() });
export type SourceRefreshResponse = z.infer<typeof SourceRefreshResponse>;

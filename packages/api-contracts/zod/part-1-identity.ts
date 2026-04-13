/**
 * Zod schemas — Part 1 (Identity).
 * Mirrors packages/api-contracts/openapi/part-1-identity.yaml.
 */
import { z } from 'zod';
import { Iso8601, Platform, Uuid } from './common';

// ─── Auth ──────────────────────────────────────────────────────────────────

export const AuthRegister = z.object({
  firebase_id_token: z.string(),
  display_name: z.string().max(80).optional(),
  locale: z.string().default('en'),
  country_code: z.string().length(2).optional(),
  marketing_opt_in: z.boolean().default(false),
});
export type AuthRegister = z.infer<typeof AuthRegister>;

export const AuthLogin = z.object({
  firebase_id_token: z.string(),
  install_id: z.string().min(1),
  platform: Platform,
});
export type AuthLogin = z.infer<typeof AuthLogin>;

export const AuthRefresh = z.object({ refresh_token: z.string() });
export type AuthRefresh = z.infer<typeof AuthRefresh>;

export const AuthTokens = z.object({
  access_token: z.string(),
  refresh_token: z.string(),
  expires_in: z.number().int().positive(),
  account: z.lazy(() => Account).optional(),
});
export type AuthTokens = z.infer<typeof AuthTokens>;

// ─── Account ───────────────────────────────────────────────────────────────

export const Account = z.object({
  id: Uuid,
  email: z.string().email(),
  display_name: z.string().nullable(),
  locale: z.string(),
  country_code: z.string().length(2).nullable(),
  marketing_opt_in: z.boolean(),
  created_at: Iso8601,
  updated_at: Iso8601,
});
export type Account = z.infer<typeof Account>;

export const AccountUpdate = z
  .object({
    display_name: z.string().max(80),
    locale: z.string(),
    country_code: z.string().length(2),
    marketing_opt_in: z.boolean(),
  })
  .partial();
export type AccountUpdate = z.infer<typeof AccountUpdate>;

// ─── Profiles ──────────────────────────────────────────────────────────────

export const AgeRating = z.union([
  z.literal(6),
  z.literal(12),
  z.literal(16),
  z.literal(18),
]);
export type AgeRating = z.infer<typeof AgeRating>;

export const Profile = z.object({
  id: Uuid,
  account_id: Uuid,
  name: z.string().max(40),
  avatar_key: z.string().nullable(),
  is_kids: z.boolean(),
  max_age_rating: AgeRating.nullable(),
  language: z.string(),
  position: z.number().int().min(0),
  has_pin: z.boolean(),
  created_at: Iso8601,
  updated_at: Iso8601,
});
export type Profile = z.infer<typeof Profile>;

export const ProfileCreate = z.object({
  name: z.string().min(1).max(40),
  avatar_key: z.string().optional(),
  is_kids: z.boolean().default(false),
  max_age_rating: AgeRating.optional(),
  language: z.string().default('en'),
});
export type ProfileCreate = z.infer<typeof ProfileCreate>;

export const ProfileUpdate = z
  .object({
    name: z.string().min(1).max(40),
    avatar_key: z.string(),
    is_kids: z.boolean(),
    max_age_rating: AgeRating.nullable(),
    language: z.string(),
    position: z.number().int().min(0),
  })
  .partial();
export type ProfileUpdate = z.infer<typeof ProfileUpdate>;

// ─── PIN ───────────────────────────────────────────────────────────────────

export const PinString = z.string().regex(/^[0-9]{4,6}$/);

export const PinSet = z.object({ pin: PinString });
export type PinSet = z.infer<typeof PinSet>;

export const PinVerify = z.object({ pin: PinString });
export type PinVerify = z.infer<typeof PinVerify>;

export const PinResult = z.object({
  verified: z.boolean(),
  remaining_attempts: z.number().int().min(0).optional(),
});
export type PinResult = z.infer<typeof PinResult>;

// ─── Devices ───────────────────────────────────────────────────────────────

export const Device = z.object({
  id: Uuid,
  device_label: z.string(),
  platform: Platform,
  app_version: z.string().nullable(),
  os_version: z.string().nullable(),
  model: z.string().nullable(),
  install_id: z.string(),
  last_seen_at: Iso8601,
  registered_at: Iso8601,
  revoked: z.boolean(),
});
export type Device = z.infer<typeof Device>;

export const DeviceRegister = z.object({
  device_label: z.string().max(60),
  platform: Platform,
  install_id: z.string().min(1),
  app_version: z.string().optional(),
  os_version: z.string().optional(),
  model: z.string().optional(),
});
export type DeviceRegister = z.infer<typeof DeviceRegister>;

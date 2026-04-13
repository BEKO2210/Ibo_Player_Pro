import { z } from 'zod';

export const entitlementStateSchema = z.enum([
  'none',
  'trial',
  'lifetime_single',
  'lifetime_family',
  'expired',
  'revoked',
]);

export const errorCodeSchema = z.enum([
  'UNAUTHORIZED',
  'SLOT_FULL',
  'PIN_INVALID',
  'ENTITLEMENT_REQUIRED',
  'VALIDATION_ERROR',
]);

export const devicePlatformSchema = z.enum([
  'android_tv',
  'android_mobile',
  'web',
  'ios',
  'tvos',
  'tizen',
  'webos',
  'unknown',
]);

export const entitlementSchema = z.object({
  state: entitlementStateSchema,
  trialStartedAt: z.string().datetime().nullable().optional(),
  trialEndsAt: z.string().datetime().nullable().optional(),
  activatedAt: z.string().datetime().nullable().optional(),
  expiresAt: z.string().datetime().nullable().optional(),
  revokedAt: z.string().datetime().nullable().optional(),
});

export const accountSchema = z.object({
  id: z.string().uuid(),
  email: z.string().email(),
  emailVerified: z.boolean(),
  locale: z.string().min(1),
  createdAt: z.string().datetime(),
});

/**
 * Unified request body for /auth/register|login|refresh. V1 auth is
 * Firebase-only: clients pass their Firebase ID token on every request.
 */
export const firebaseTokenRequestSchema = z.object({
  firebaseIdToken: z.string().min(20).max(4096),
  locale: z.string().max(16).optional(),
});

// Legacy aliases for existing consumers.
export const registerRequestSchema = firebaseTokenRequestSchema;
export const loginRequestSchema = firebaseTokenRequestSchema;
export const refreshRequestSchema = firebaseTokenRequestSchema;

export const accountSnapshotResponseSchema = z.object({
  account: accountSchema,
  entitlement: entitlementSchema,
});

/** Deprecated name — kept exported for incremental migration. */
export const authResponseSchema = accountSnapshotResponseSchema;

export const entitlementStatusResponseSchema = z.object({
  entitlement: entitlementSchema,
});

export const deviceSchema = z.object({
  id: z.string().uuid(),
  name: z.string().min(1),
  platform: devicePlatformSchema,
  appVersion: z.string().nullable().optional(),
  osVersion: z.string().nullable().optional(),
  isCurrent: z.boolean(),
  isRevoked: z.boolean(),
  lastSeenAt: z.string().datetime().nullable().optional(),
  revokedAt: z.string().datetime().nullable().optional(),
  createdAt: z.string().datetime(),
});

export const deviceListResponseSchema = z.object({
  devices: z.array(deviceSchema),
});

export const registerDeviceRequestSchema = z.object({
  name: z.string().min(1).max(120),
  platform: devicePlatformSchema,
  appVersion: z.string().max(64).optional(),
  osVersion: z.string().max(64).optional(),
  lastIp: z.string().ip().optional(),
});

export const registerDeviceResponseSchema = z.object({
  device: deviceSchema,
  deviceToken: z.string().min(20),
});

export const profileSchema = z.object({
  id: z.string().uuid(),
  name: z.string().min(1),
  isKids: z.boolean(),
  ageLimit: z.number().int().min(0).max(21).nullable().optional(),
  isDefault: z.boolean().optional(),
});

export const profileListResponseSchema = z.object({
  items: z.array(profileSchema),
  maxProfiles: z.number().int().min(1),
});

export const createProfileRequestSchema = z.object({
  name: z.string().min(1).max(50),
  isKids: z.boolean(),
  ageLimit: z.number().int().min(0).max(21).nullable().optional(),
  pin: z.string().min(4).max(10).nullable().optional(),
});

export const updateProfileRequestSchema = z
  .object({
    name: z.string().min(1).max(50).optional(),
    ageLimit: z.number().int().min(0).max(21).nullable().optional(),
    pin: z.string().min(4).max(10).nullable().optional(),
  })
  .refine((value) => Object.keys(value).length > 0, 'At least one field is required');

export const verifyPinRequestSchema = z.object({
  pin: z.string().min(4).max(10),
});

export const verifyPinResponseSchema = z.object({ ok: z.boolean() });

export const sourceKindSchema = z.enum(['m3u', 'xmltv', 'm3u_plus_epg']);

export const sourceSchema = z.object({
  id: z.string().uuid(),
  profileId: z.string().uuid().nullable().optional(),
  name: z.string().min(1),
  kind: sourceKindSchema,
  isActive: z.boolean(),
  validationStatus: z.enum(['pending', 'valid', 'invalid']),
  itemCountEstimate: z.number().int().nullable().optional(),
});

export const sourceListResponseSchema = z.object({
  items: z.array(sourceSchema),
});

export const createSourceRequestSchema = z.object({
  profileId: z.string().uuid().nullable().optional(),
  name: z.string().min(1).max(120),
  kind: sourceKindSchema,
  url: z.string().url(),
  username: z.string().nullable().optional(),
  password: z.string().nullable().optional(),
});

export const updateSourceRequestSchema = z
  .object({
    name: z.string().min(1).max(120).optional(),
    isActive: z.boolean().optional(),
  })
  .refine((value) => Object.keys(value).length > 0, 'At least one field is required');

export const playbackStateSchema = z.enum(['starting', 'playing', 'paused', 'buffering', 'stopped', 'error']);

export const playbackStartRequestSchema = z.object({
  profileId: z.string().uuid(),
  sourceId: z.string().uuid(),
  itemId: z.string().min(1),
  itemType: z.enum(['live', 'vod', 'series_episode']),
});

export const playbackSessionResponseSchema = z.object({
  sessionId: z.string().uuid(),
});

export const playbackHeartbeatRequestSchema = z.object({
  sessionId: z.string().uuid(),
  positionSeconds: z.number().int().min(0),
  state: playbackStateSchema,
});

export const playbackStopRequestSchema = z.object({
  sessionId: z.string().uuid(),
  finalPositionSeconds: z.number().int().min(0),
});

export const billingVerifyRequestSchema = z.object({
  purchaseToken: z.string().min(1),
  productId: z.string().min(1),
});

export const billingRestoreRequestSchema = z.object({}).passthrough();

export const errorEnvelopeSchema = z.object({
  error: z.object({
    code: errorCodeSchema,
    message: z.string().min(1),
    details: z.record(z.string(), z.unknown()).optional(),
    requestId: z.string().optional(),
  }),
});

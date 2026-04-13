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

export const deviceInfoSchema = z.object({
  platform: z.enum(['android_tv', 'android_mobile']),
  appVersion: z.string().min(1),
  osVersion: z.string().min(1).optional(),
  deviceName: z.string().min(1).optional(),
});

export const entitlementSchema = z.object({
  state: entitlementStateSchema,
  trialEndsAt: z.string().datetime().nullable().optional(),
  expiresAt: z.string().datetime().nullable().optional(),
});

export const accountSchema = z.object({
  id: z.string().uuid(),
  email: z.string().email(),
  locale: z.string().min(2),
});

export const registerRequestSchema = z.object({
  firebaseIdToken: z.string().min(1),
  locale: z.string().min(2),
  device: deviceInfoSchema,
});

export const loginRequestSchema = registerRequestSchema;

export const logoutRequestSchema = z.object({
  deviceToken: z.string().min(1),
});

export const refreshRequestSchema = z.object({
  refreshToken: z.string().min(1),
});

export const authResponseSchema = z.object({
  account: accountSchema,
  deviceToken: z.string().min(1),
  accessToken: z.string().min(1),
  refreshToken: z.string().min(1),
  entitlement: entitlementSchema,
});

export const tokenRefreshResponseSchema = z.object({
  accessToken: z.string().min(1),
  refreshToken: z.string().min(1),
});

export const entitlementStatusResponseSchema = z.object({
  entitlement: entitlementSchema,
});

export const deviceSchema = z.object({
  id: z.string().uuid(),
  name: z.string().min(1),
  platform: z.string().min(1),
  isCurrent: z.boolean(),
  isRevoked: z.boolean(),
  lastSeenAt: z.string().datetime().nullable().optional(),
});

export const deviceListResponseSchema = z.object({
  items: z.array(deviceSchema),
  maxActiveSlots: z.number().int().min(1),
});

export const renameDeviceRequestSchema = z.object({
  name: z.string().min(1).max(80),
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
  deviceToken: z.string().min(1),
});

export const billingRestoreRequestSchema = z.object({
  deviceToken: z.string().min(1),
});

export const errorEnvelopeSchema = z.object({
  error: z.object({
    code: errorCodeSchema,
    message: z.string().min(1),
    details: z.record(z.string(), z.unknown()).optional(),
    requestId: z.string().optional(),
  }),
});

/**
 * Shared Zod primitives used by all three parts.
 *
 * Hand-written to match openapi/part-{1,2,3}-*.yaml. Run 6 may swap this
 * for `openapi-zod-client` generated output; until then, the types here and
 * the YAML are the sole source of truth.
 *
 * NOTE: `zod` is NOT listed in package.json yet. Run 6 wires it. Until then,
 * the `.ts` files are spec-only reference; they will compile once the
 * dependency is installed.
 */
import { z } from 'zod';

/** RFC 9457 Problem Details envelope. Every error response uses this. */
export const ProblemDetails = z.object({
  type: z.string().default('about:blank'),
  title: z.string(),
  status: z.number().int().min(100).max(599),
  detail: z.string().optional(),
  instance: z.string().optional(),
  code: z.string(),
  errors: z
    .array(z.object({ path: z.string(), message: z.string() }))
    .optional(),
});
export type ProblemDetails = z.infer<typeof ProblemDetails>;

/** Canonical stable error codes. Keep in sync with README.md §Error codes. */
export const ErrorCode = z.enum([
  'validation_failed',
  'unauthorized',
  'forbidden',
  'not_found',
  'conflict',
  'entitlement_required',
  'trial_already_used',
  'profile_cap_reached',
  'device_cap_reached',
  'profile_name_taken',
  'pin_locked',
  'pin_mismatch',
  'source_invalid',
  'billing_verification_failed',
  'playback_session_ended',
  'concurrent_stream_cap',
  'internal_error',
]);
export type ErrorCode = z.infer<typeof ErrorCode>;

/** Shared asset-type enum used by activity + playback. */
export const AssetType = z.enum(['live', 'vod', 'recording']);
export type AssetType = z.infer<typeof AssetType>;

/** Shared platform enum used by device + auth. */
export const Platform = z.enum([
  'android_tv',
  'android_mobile',
  'tvos',
  'ios',
  'tizen',
  'webos',
  'web',
]);
export type Platform = z.infer<typeof Platform>;

export const Uuid = z.string().uuid();
export const Iso8601 = z.string().datetime({ offset: true });

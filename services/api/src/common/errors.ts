/**
 * Stable error codes matching `packages/api-contracts/openapi.yaml` ErrorCode.
 * Keep in sync — clients switch on these strings.
 */
export const ErrorCode = {
  UNAUTHORIZED: 'UNAUTHORIZED',
  SLOT_FULL: 'SLOT_FULL',
  PIN_INVALID: 'PIN_INVALID',
  ENTITLEMENT_REQUIRED: 'ENTITLEMENT_REQUIRED',
  VALIDATION_ERROR: 'VALIDATION_ERROR',
} as const;

export type ErrorCodeValue = (typeof ErrorCode)[keyof typeof ErrorCode];

export interface ErrorEnvelope {
  error: {
    code: ErrorCodeValue;
    message: string;
    details?: Record<string, unknown>;
    requestId?: string;
  };
}

export function buildErrorEnvelope(
  code: ErrorCodeValue,
  message: string,
  details?: Record<string, unknown>,
  requestId?: string,
): ErrorEnvelope {
  const err: ErrorEnvelope['error'] = { code, message };
  if (details) err.details = details;
  if (requestId) err.requestId = requestId;
  return { error: err };
}

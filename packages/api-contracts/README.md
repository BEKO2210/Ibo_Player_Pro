# API Contracts (Run 4)

This package is the single source of truth for V1 API contracts used by `services/api` and client apps.

## Files
- `openapi.yaml` — OpenAPI 3.1 endpoint + schema contract
- `src/zod.ts` — runtime-safe Zod mirrors of request/response payloads

## Contract rules
1. **OpenAPI and Zod must stay isomorphic**: fields, enums, nullability, and required/optional semantics must match.
2. **Server authoritative rules** remain contract-visible:
   - entitlement states: `none | trial | lifetime_single | lifetime_family | expired | revoked`
   - slot/profile limits are enforced server-side and surfaced by API responses
3. **Stable error envelope** for all non-2xx responses:

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Human-readable explanation",
    "details": {},
    "requestId": "req_123"
  }
}
```

## Stable error codes (V1)
- `UNAUTHORIZED`
- `SLOT_FULL`
- `PIN_INVALID`
- `ENTITLEMENT_REQUIRED`
- `VALIDATION_ERROR`

## Suggested usage (NestJS + clients)
- Validate incoming payloads with Zod at edge handlers or generated DTO adapters.
- Generate typed clients from `openapi.yaml` for Android and backend integration tests.
- Any contract change should update both files in one commit.

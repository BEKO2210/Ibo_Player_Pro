# @premium-player/api-contracts

> **V1 REST API contract.** OpenAPI 3.1 + Zod, split into 3 parts that mirror the data model split from Run 3.

## Layout

```
packages/api-contracts/
├── package.json
├── openapi/
│   ├── part-1-identity.yaml            # auth, account, profiles (+PIN), devices
│   ├── part-2-commerce-sources.yaml    # entitlement, billing, sources
│   └── part-3-epg-activity.yaml        # EPG, continue-watching, favorites, watch-history, playback
├── zod/
│   ├── common.ts                       # ProblemDetails, ErrorCode, AssetType, Platform, Uuid, Iso8601
│   ├── part-1-identity.ts
│   ├── part-2-commerce-sources.ts
│   ├── part-3-epg-activity.ts
│   └── index.ts                        # barrel re-export
└── README.md (you are here)
```

Each OpenAPI part is an independently valid 3.1 document with its own `components` (ProblemDetails is intentionally duplicated so every part validates stand-alone). Run 6 bundles them into one served doc with:

```bash
npx @redocly/cli bundle openapi/part-1-identity.yaml openapi/part-2-commerce-sources.yaml openapi/part-3-epg-activity.yaml --output openapi.bundled.yaml
```

## Parts

| Part | YAML | Zod | Covers |
|---|---|---|---|
| **1 — Identity** | `openapi/part-1-identity.yaml` | `zod/part-1-identity.ts` | `/v1/auth/*`, `/v1/me`, `/v1/profiles*`, `/v1/devices*` |
| **2 — Commerce & Sources** | `openapi/part-2-commerce-sources.yaml` | `zod/part-2-commerce-sources.ts` | `/v1/entitlement*`, `/v1/billing/*`, `/v1/sources*` |
| **3 — EPG & Activity** | `openapi/part-3-epg-activity.yaml` | `zod/part-3-epg-activity.ts` | `/v1/epg/*`, `/v1/continue-watching*`, `/v1/favorites*`, `/v1/watch-history`, `/v1/playback/*` |

## Auth model

All endpoints (except `/v1/auth/register`, `/v1/auth/login`, `/v1/auth/refresh`) require a Bearer token whose JWT is a **Firebase ID token**. The server verifies it with Firebase Admin (Run 7) and maps `firebase_uid` → `accounts.id`.

Activity + playback endpoints additionally require the `X-Profile-Id` header to disambiguate which profile's data to read/write.

## Consuming this package

### Backend (NestJS, `services/api`)
- Import Zod schemas and wrap NestJS DTOs with a `zod-validation-pipe` (wired in Run 6).
- Serve the bundled OpenAPI at `/docs` with Swagger UI.
- Return every error as `application/problem+json` — see §Error codes.

### Android TV client (`apps/android-tv`)
- Run 11 adds an openapi-generator Gradle task that consumes `openapi/part-*.yaml` and emits Kotlin + Retrofit models.
- UI error surfaces key off `ProblemDetails.code` (stable) — not `title`/`detail` (localized).

## Error codes (stable)

Every error response conforms to RFC 9457 `application/problem+json` with a `code` field:

| HTTP | `code` | When |
|---|---|---|
| 400 | `validation_failed` | Body/query failed Zod validation |
| 401 | `unauthorized` | Missing/invalid/expired bearer token |
| 403 | `forbidden` | Token valid but caller lacks permission |
| 402 | `entitlement_required` | No active trial/lifetime |
| 402 | `profile_cap_reached` | Would exceed `entitlement.max_profiles` |
| 402 | `device_cap_reached` | Would exceed `entitlement.max_devices` |
| 402 | `concurrent_stream_cap` | Too many active playback sessions |
| 404 | `not_found` | Resource missing or not owned |
| 409 | `conflict` | Duplicate resource |
| 409 | `profile_name_taken` | Name collides with existing profile |
| 409 | `trial_already_used` | Account previously started a trial |
| 409 | `playback_session_ended` | Heartbeat after end |
| 422 | `source_invalid` | Source URL unparseable / auth-blocked on first fetch |
| 422 | `billing_verification_failed` | Google Play verification returned `false` |
| 423 | `pin_locked` | PIN locked after 5 failed attempts (15 min) |
| 423 | `pin_mismatch` | Wrong PIN (counted toward lockout) |
| 500 | `internal_error` | Server-side unexpected error |

The canonical list lives as a Zod enum in `zod/common.ts` — keep this table and that enum in sync.

## Acceptance checklist vs. Run 4

- [x] Every write path from `docs/product/user-flows.md` has a matching endpoint (auth, trial, purchase, restore, profile CRUD + PIN, device register/revoke, source CRUD + refresh, continue-watching, favorites, watch-history, playback start/heartbeat/end)
- [x] Every user-facing table in `docs/architecture/data-model*.md` has at least one read path
- [x] All 6 `EntitlementState` values representable (Part 2)
- [x] `application/problem+json` envelope on every error response (RFC 9457)
- [x] Global `BearerFirebase` security scheme
- [x] Hand-written Zod mirrors every schema component
- [x] Error codes documented in one table, also exported from `zod/common.ts`
- [x] Split into 3 parts matching the data-model split

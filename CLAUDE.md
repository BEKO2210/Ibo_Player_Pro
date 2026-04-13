# Premium TV Player — Project Guide for Claude Code

> **This file is the single source of truth.** Every new Claude session starts by reading this file top-to-bottom, then executes the block under **▶️ Next Run**. After finishing the run, Claude MUST update this file (tick the run, write the next "Next Run" block, append to Run Log) and commit.

---

## 🎯 Current State

- **Phase:** B — Backend V1
- **Last completed run:** Run 9 — Billing worker
- **Current branch:** `claude/fix-api-timeout-vFqPP`
- **Push target:** same branch (`-u origin claude/fix-api-timeout-vFqPP`)
- **Logo status:** ✅ received in Run 6 — `assets/logo/logo-no_background.png` (transparent PNG, blue gradient play-button with signal waves). Dark/light variants optional follow-up.
- **applicationId:** ⏳ to be decided in **Run 11**

---

## ▶️ Next Run (Run 10): Profile + Source Modules

### Goal
Implement the household profile model (5 profiles per account, kids flag, Argon2id PIN hash, age filter) and the user-managed source catalog (M3U / XMLTV / M3U+EPG with per-source encrypted credentials and parser stubs). Both modules sit on top of the entitlement caps from Run 8.

### Deliverables
- [ ] `ProfileService` enforcing the 5-profile cap (1 for `trial`/`lifetime_single`, 5 for `lifetime_family`), kids flag + age limit (0-21), single default profile per account
- [ ] PIN flow with Argon2id hashing (`profile_pins` table, lockout after 5 failed attempts, `lock_until` honored)
- [ ] Endpoints (per OpenAPI):
  - `GET    /v1/profiles`
  - `POST   /v1/profiles`              (`409` when cap reached)
  - `PUT    /v1/profiles/{id}`         (rename, age limit, PIN replace)
  - `DELETE /v1/profiles/{id}`         (soft delete; 404 on miss; refuses to delete the last default)
  - `POST   /v1/profiles/{id}/verify-pin`
- [ ] `SourceService` covering M3U / XMLTV / M3U+EPG kinds + AES-256-GCM envelope encryption for URL/username/password/headers (`source_credentials` table; KMS key id stored, plaintext never persisted)
- [ ] Endpoints:
  - `GET    /v1/sources`               (list account or profile-scoped)
  - `POST   /v1/sources`               (creates, returns row with `validation_status='pending'`)
  - `PUT    /v1/sources/{id}`          (rename, toggle isActive)
  - `DELETE /v1/sources/{id}`          (soft delete)
- [ ] `packages/parsers/` stubs for M3U + XMLTV — pure functions that take a string and return normalized rows; real network fetch is parked for Run 15 (EPG worker / source-management UI)
- [ ] Unit tests:
  - profile cap enforcement under each entitlement state
  - PIN hash + verify (Argon2id) including the lockout window
  - kids flag + age limit validation
  - source create/update with encryption round-trip (encrypt → persist → decrypt)
  - parser stubs accept fixture inputs and return expected normalized rows
- [ ] Update `services/api/README.md` profile + source sections; extend `.env.example` with `SOURCE_ENCRYPTION_KEY` (32-byte hex)

### Acceptance criteria
- Creating a 6th profile on `lifetime_family` returns `409` with `SLOT_FULL` (or a profile-specific code; document choice)
- Creating a 2nd profile on `trial`/`lifetime_single` returns `409`
- Wrong PIN 5× locks the profile for the configured window; `verify-pin` returns `423`/`409` with `PIN_INVALID` afterwards
- Source credentials never appear in plaintext in `pg_dump` of the schema
- M3U + XMLTV parser stubs round-trip a small fixture file
- All Jest unit tests pass; existing 89 tests still green

### After this run — update CLAUDE.md
1. Tick Run 10 in the roadmap
2. Set "Last completed run" to `Run 10 — Profile + Source modules`
3. Write the new "Next Run" block for **Run 11: Android TV bootstrap (Compose / Compose-TV / applicationId decision)**
4. Append entry to **Run Log**
5. Commit: `api: add profile + source modules with PIN, kids, encryption (Run 10)` and push

---

## 📋 Locked Product Decisions

These are FINAL. Do not re-litigate in future runs unless the user explicitly asks.

| Area | Decision |
|---|---|
| Product name (working) | **Premium TV Player** |
| Positioning | Neutral premium player for user-authorized sources (not a content provider) |
| Platform order | 1) Android TV → 2) Android Mobile → 3) Admin Web → 4) tvOS/iOS → 5) Samsung Tizen → 6) LG webOS |
| Primary language | English (i18n-ready from day one) |
| Monetization | 14-day **server-side** trial → Lifetime Single (€19.99–24.99) + Lifetime Family (€39.99–49.99) |
| Entitlement model | **Account-based, NOT MAC-based.** 1 account → up to 5 profiles → up to 5 server-managed device slots (family plan) |
| Auth | Firebase Authentication (email/password) + own entitlement layer on own API |
| Purchase rails | Google Play Billing (one-time products), server-verified, refund/revoke aware |
| Kids safety | Dedicated kids profile + PIN gate + age filter |
| Cloud sync | Watch history, continue watching, favorites, profiles (synced via own API) |
| Sources | App ships **empty**; user adds M3U / M3U8 / XMLTV URLs themselves |
| Recording / Timeshift | EPG + recording **schedule** in V1; actual recording V1.5; true timeshift V2+ |
| Design direction | Dark, premium, between Apple and Netflix — large heroes, elegant typography, clean focus states |
| License | **Proprietary / All Rights Reserved** (no OSS license) |

---

## 🏗 Architecture & Stack

### Android TV (V1 client)
- Kotlin + Jetpack Compose + **Compose for TV**
- Media3 / ExoPlayer
- Hilt (DI), Coroutines + Flow
- Room (local cache), DataStore (prefs)
- Google Play Billing Library
- Firebase Auth SDK
- Retrofit or Ktor client → own API

### Backend (V1)
- TypeScript + **NestJS**
- PostgreSQL (primary store) + **Prisma** ORM
- Redis (sessions, rate limiting, caches)
- Docker + docker-compose for local dev
- REST API + OpenAPI 3.1 contract
- Firebase Admin SDK (verify ID tokens) + own user/entitlement tables

### Workers (separate processes)
- `billing-worker` — Play Billing server verification, purchase ack, refund handling
- `epg-worker` — XMLTV fetch + cache
- `recording-worker` (later) — scheduled recording jobs

### Shared packages
- `packages/domain` — shared TS types/models
- `packages/api-contracts` — OpenAPI + Zod
- `packages/parsers` — M3U + XMLTV parsers
- `packages/i18n` — shared string keys
- `packages/ui-tokens` — design tokens (colors, spacing, typography, motion)
- `packages/entitlement-engine` — pure logic for trial/active/expired/revoked

---

## 📁 Repo Layout

```
premium-player/            (repo root = /home/user/Ibo_Player_Pro)
  apps/
    android-tv/            # V1 focus
    android-mobile/        # V2
    admin-web/             # V2
    apple-tv/              # V3
    samsung-tv/            # V3
    lg-tv/                 # V3
  services/
    api/                   # NestJS — V1
    entitlement-service/
    billing-worker/
    epg-worker/
    recording-worker/
  packages/
    domain/
    api-contracts/
    parsers/
    i18n/
    ui-tokens/
    entitlement-engine/
  infra/
    docker/
    postgres/
    redis/
    ci/
  docs/
    product/
    architecture/
    ux/
    launch/
  assets/
    logo/                  # populated in Run 6 when user uploads
  CLAUDE.md                # ← you are here
  LICENSE
  README.md
  .gitignore
  .editorconfig
```

---

## 🗺 Full Roadmap (20 Runs)

### Phase A — Foundation & Specs
- [x] **Run 1** — Repo skeleton + CLAUDE.md + LICENSE + .gitignore + .editorconfig + README
- [x] **Run 2** — PRD + user flows (`docs/product/`)
- [x] **Run 3** — Data model: SQL schemas + ER diagram (`docs/architecture/data-model.md`)
- [x] **Run 4** — API contracts: OpenAPI 3.1 + Zod (`packages/api-contracts/`)
- [x] **Run 5** — Entitlement state machine + billing event handling (`docs/architecture/entitlement-state-machine.md`)

### Phase B — Backend V1
- [x] **Run 6** — NestJS bootstrap (`services/api/`): Prisma, Postgres, Redis, docker-compose, health endpoint, env setup. **→ Claude asks user for logo upload into `assets/logo/` here.**
- [x] **Run 7** — Auth module: Firebase Admin token verify, user sync, register/login/refresh
- [x] **Run 8** — Entitlement module: trial start, status, device register/list/revoke
- [x] **Run 9** — Billing worker: Play Billing verification, ack, lifetime flip, refund handler
- [ ] **Run 10** — Profile + Source modules: 5-profile cap, PIN hash, kids flag; source CRUD + parser stubs

### Phase C — Android TV Client
- [ ] **Run 11** — `apps/android-tv/` Gradle/Compose/Compose-TV bootstrap. **→ applicationId is decided in this run.** Leanback intent, TV manifest, Hilt, Navigation-Compose, ui-tokens wiring
- [ ] **Run 12** — Design system in Compose: dark theme, typography, colors, focus states, motion, reusable Card/Row/Hero
- [ ] **Run 13** — Onboarding/Auth screens: Welcome → Signup/Login → Trial activation → Profile picker. Firebase Auth + API client
- [ ] **Run 14** — Home screen: Hero carousel, rows, Continue Watching, Favorites. Logo wired in if not already
- [ ] **Run 15** — Source management UI + EPG browse view
- [ ] **Run 16** — Playback (Media3/ExoPlayer): Live, VOD, Resume, subtitles, audio-track picker, heartbeat sync
- [ ] **Run 17** — Billing flow in app: Play Billing Client, purchase trigger, Restore Purchase, entitlement UI states
- [ ] **Run 18** — Parental controls: PIN gate, age filter, device list / logout / unpair

### Phase D — Polish & Ship-Ready
- [ ] **Run 19** — i18n finalization (all strings keyed, en default, fallback), error states, diagnostics screen
- [ ] **Run 20** — E2E smoke test script (backend + app against local docker stack), release build config (R8/Proguard), store-listing asset checklist, handover doc

### Buffer Runs (21+, optional)
- Recording / scheduler
- Admin web portal
- Android Mobile client
- CI/CD pipeline (GitHub Actions)

---

## 🔁 Per-Run Protocol (Claude MUST follow)

1. **Read** CLAUDE.md completely — especially **Current State** and **▶️ Next Run**
2. **Execute** only the Deliverables listed in the Next Run block. No scope creep.
3. **Verify** against Acceptance criteria before finishing
4. **Update CLAUDE.md**:
   - Tick the just-finished run in the Full Roadmap
   - Update **Current State** (Phase, Last completed run)
   - Replace the **▶️ Next Run** block with the next run's Goal / Deliverables / Acceptance criteria / After-this-run note
   - Append a new entry to the **Run Log** below (date, title, 2–5 bullet summary)
5. **Commit** with message format: `<area>: <short summary> (Run N)`
   Examples: `docs: add PRD and user flows (Run 2)`, `api: scaffold NestJS service (Run 6)`, `tv: add home screen (Run 14)`
6. **Push** to `claude/premium-tv-player-plan-WG2tC` with `git push -u origin claude/premium-tv-player-plan-WG2tC` (retry on network error: 2s, 4s, 8s, 16s)
7. **Do NOT** open a Pull Request unless the user explicitly asks

### Guardrails
- **Never** introduce MAC-address based device binding. Always server-managed device slots tied to account.
- **Never** store entitlement/trial state only on the client. Server is authoritative.
- **Never** add an OSS license. This repo is proprietary.
- **Never** scope-creep: if a task isn't in the current Next Run deliverables, note it under **Parking Lot** below instead of doing it.

---

## 🔐 Licensing

Proprietary. All Rights Reserved. See `LICENSE`. Not open source. Do not distribute.

---

## 🖼 Logo

- **Status:** ✅ received in Run 6
- **File:** `assets/logo/logo-no_background.png` (transparent PNG, ~208 KB)
- **Design:** Blue gradient play-button with signal/sound waves, on transparent background. Optimized for dark UI.
- **Optional follow-ups:** SVG vector variant, explicit dark/light variants — can be added any time; not blocking.
- **Next use:** wired into Android TV home screen in Run 14 (splash/launcher in earlier runs if needed).

---

## 🅿️ Parking Lot

(Ideas or deferred items captured during any run that are NOT in the current scope. Claude adds here instead of acting on them.)

- **~~OpenAPI auth-response reconciliation~~ (resolved in Run 8):** chose option (a) — Firebase-only auth for V1. `AuthResponse` replaced with `AccountSnapshotResponse` (`{ account, entitlement }`). Device slots are a separate explicit flow via `/devices/register` with an `X-Device-Token` header. OpenAPI + Zod contracts updated.
- **Logo variants (from Run 6):** SVG vector version and explicit dark/light PNG variants would help for the Android TV splash / launcher and light-theme surfaces. Not blocking Run 12/14 but nice to have.
- **Entitlement scheduler (from Run 8):** currently trial→expired happens read-time (on `getOrInitialize`). For analytics and timely push notifications we may want a scheduled job that runs every 5–15 minutes and marks `trial`→`expired`. Not blocking — defer until Run 9 (worker infra exists) or Run 10.
- **Device rename + list-by-device-token (from Run 8):** OpenAPI had `PUT /devices/{id}` rename; not yet implemented (out of Run 8 scope). Add when the Android TV device-management UI lands (Run 18).

---

## 📝 Run Log

### Run 1 — 2026-04-12 — Repo skeleton + CLAUDE.md + LICENSE
- Created monorepo folder tree (`apps/`, `services/`, `packages/`, `infra/`, `docs/`, `assets/logo/`) with `.gitkeep` placeholders
- Wrote proprietary `LICENSE` (All Rights Reserved)
- Wrote `.gitignore` (Node + Kotlin + Android + iOS + Docker + secrets) and `.editorconfig`
- Replaced stub `README.md` with a short landing pointing to `CLAUDE.md`
- Created this `CLAUDE.md` with locked product decisions, full 20-run roadmap, per-run protocol, and run log

### Run 2 — 2026-04-12 — PRD + user flows
- Wrote `docs/product/PRD.md`: vision, personas, V1 in/out scope, monetization table (Trial / Lifetime Single / Lifetime Family), product principles, success metrics, risks, glossary
- Wrote `docs/product/user-flows.md`: 17 canonical flows with mermaid diagrams — Onboarding, Signup, Login, Trial activation, Purchase, Restore, Profile picker, Profile CRUD, Add source, Home, Kids PIN gate, Device management, Playback + Resume, Logout, Expired/Revoked handling, Error surfaces, Happy path
- All flows consistent with locked decisions (server-authoritative trial/entitlement, account-based device slots, no MAC binding, 5 profiles / 5 device slots for Family)


### Run 3 — 2026-04-13 — Data model
- Wrote `docs/architecture/data-model.md` with full V1 relational model narrative covering accounts, entitlements, profiles, sources, EPG, playback, and audit responsibilities
- Added PostgreSQL DDL for 15 required tables plus enums, constraints, foreign keys, and indexing strategy for account/profile/device/source/EPG-time access paths
- Added GitHub-renderable mermaid ER diagram and implementation notes for encryption-at-rest, Argon2id PIN hashing, soft deletes, UTC timestamps, and UUID PKs
- Confirmed entitlement states and family caps are representable via schema + app-layer enforcement


### Run 4 — 2026-04-13 — API contracts (OpenAPI + Zod)
- Added `packages/api-contracts/openapi.yaml` with V1 endpoints for auth, entitlement, devices, profiles, sources, playback, and billing
- Added `packages/api-contracts/src/zod.ts` with runtime schemas mirroring OpenAPI requests/responses and core enums
- Added `packages/api-contracts/README.md` documenting contract rules, stable error envelope, and usage expectations
- Kept entitlement states and error codes aligned with locked product decisions and user-flow endpoints


### Run 5 — 2026-04-13 — Entitlement state machine + billing events
- Added `docs/architecture/entitlement-state-machine.md` with canonical entitlement states, deterministic transition table, and billing event mapping
- Defined trial lifecycle rules (consume-once), refund/revoke fallback policy, and explicit device/profile caps by entitlement state
- Documented idempotency keys, replay handling, and worker/API concurrency conflict resolution
- Added GitHub-renderable mermaid state diagram and error semantics aligned to stable API error codes

### Run 6 — 2026-04-13 — NestJS bootstrap + infra
- Scaffolded `services/api/` NestJS 10 project (TypeScript strict, ES2022, nest-cli, ESLint + Prettier)
- Added `ConfigModule` with Zod-validated env schema and typed `AppConfig`; wrote `.env.example`
- Added global `PrismaModule`/`PrismaService` (connect/disconnect + ping) and `RedisModule`/`RedisService` (ioredis + ping)
- Added `GET /health` via `@nestjs/terminus` reporting service, database, and redis status
- Added V1 Prisma schema at `services/api/prisma/schema.prisma` mirroring Run 3 data model (15 tables, enums, indexes, soft deletes)
- Added local Docker stack at `infra/docker/docker-compose.yml` (Postgres 16 + Redis 7 with healthchecks) and `infra/postgres/init/01-extensions.sql` to enable `pgcrypto` + `citext`
- Added `services/api/README.md` with quickstart, script table, env reference, layout, and troubleshooting
- Requested logo upload from user into `assets/logo/` (received as follow-up: `logo-no_background.png`)

### Run 9 — 2026-04-13 — Billing worker (Google Play verify + ack + restore)
- Added `services/api/src/billing/` — provider interface, `GooglePlayProvider` (uses `google-auth-library` to sign service-account JWTs against the `androidpublisher` scope, calls `purchases.products.get` + `:acknowledge`), `BillingService` as the single writer of purchase/entitlement transitions
- `verifyAndApply` → `applyVerified`: idempotent purchase upsert (unique on `provider+purchase_token`), `SELECT ... FOR UPDATE` row-lock on entitlement before mutation, then state-machine driven `EntitlementService.applyEvent` equivalent inline; acknowledge happens AFTER DB write (worker retries within Google's 3-day grace)
- SKU mapping: `BILLING_PRODUCT_ID_SINGLE` → `PURCHASE_VERIFIED_SINGLE`, `BILLING_PRODUCT_ID_FAMILY` → `PURCHASE_VERIFIED_FAMILY`, refunded/voided → `REFUND_OR_REVOKE_ACTIVE_PURCHASE`, pending/unknown SKU → `DUPLICATE_OR_REPLAY_EVENT` (no-op)
- Replay detection: same purchase token + same persisted state + entitlement already reflects target ⇒ skip mutation (defends against duplicate webhook delivery)
- Endpoints (`AuthGuard`-protected): `POST /v1/billing/verify`, `POST /v1/billing/restore` — restore re-verifies all non-refunded `purchases` rows for the account
- New service: `services/billing-worker/` — standalone Node process; `createApplicationContext` (no HTTP), reuses the API's modules via `@api/*` TS path alias so worker and `/v1/billing/verify` go through the exact same `BillingService.applyVerified`. Polls `purchases` for `acknowledgedAt IS NULL && state='purchased'` or `state='pending'`, processes batches of 50, per-row failure isolation, graceful shutdown via `OnApplicationShutdown`
- Worker config: `BILLING_WORKER_POLL_INTERVAL_MS` (default 15s), `WORKER_RUN_ONCE=true` for one-shot runs (CI / on-demand reconciliation)
- Env: `BILLING_ANDROID_PACKAGE_NAME`, `BILLING_PRODUCT_ID_SINGLE/FAMILY`, `BILLING_WORKER_POLL_INTERVAL_MS` added to `.env.example`
- 17 new unit tests (89 total): 13 for BillingService (SKU mapping, replay idempotency, ack on first verify / not on already-acked / not on refunded / ack-failure tolerance, restore with 0/N purchases) + 4 for BillingWorker (run-once, empty batch, per-purchase failure isolation, shutdown)
- **Verified end-to-end live:** API boots with `/v1/billing/verify` and `/v1/billing/restore` mapped under `/v1/billing`; both protected (401 stable ErrorEnvelope without Bearer); worker boots against real Postgres 16 + Redis 7, polls `purchases` table, finds no work, exits cleanly under `WORKER_RUN_ONCE=true`

### Run 8 — 2026-04-13 — Entitlement + Device module
- Added `entitlement.state-machine.ts` — pure, deterministic transition function mirroring `docs/architecture/entitlement-state-machine.md` exactly: `TRIAL_STARTED` (guards R-1/R-3), `TRIAL_EXPIRED` (guards `now >= trial_ends_at`), `PURCHASE_VERIFIED_SINGLE/FAMILY` (supports upgrade path), `REFUND_OR_REVOKE_ACTIVE_PURCHASE` (R-7 fallback to `expired` or `none`), `ADMIN_REVOKE`, `DUPLICATE_OR_REPLAY_EVENT` no-op; derived helpers `deviceCapFor()` and `allowsPlayback()`
- Added `EntitlementService` with `getOrInitialize` (auto-expires stale trials on read), `startTrial` (atomic `account.trial_consumed=true` + entitlement mutation), and `applyEvent` (single-writer row update suitable for Run 9 billing worker)
- Added `POST /v1/entitlement/trial/start` (returns `402 ENTITLEMENT_REQUIRED` with `TRIAL_ALREADY_CONSUMED` details when re-started) and `GET /v1/entitlement/status`
- Added `DevicesService` — slot-cap enforcement derived from entitlement (1 trial/single, 5 family, 0 none/expired/revoked), `generateDeviceToken()` (256-bit base64url) + `hashDeviceToken()` (sha256); plaintext token returned only at registration time
- Added `POST /v1/devices/register` (`201` with plaintext `deviceToken` once; `409 SLOT_FULL` on cap; `402 ENTITLEMENT_REQUIRED` on insufficient entitlement), `GET /v1/devices`, `POST /v1/devices/:id/revoke` (soft revoke via `revoked_at`)
- Added `DeviceGuard` validating `X-Device-Token` header against non-revoked account-owned device; attaches `req.device`, fire-and-forget `last_seen_at` touch; must run after `AuthGuard`
- **OpenAPI + Zod reconciliation (resolved Parking Lot item):** chose Firebase-only auth for V1 — dropped `accessToken`/`refreshToken`/`deviceToken` from `AuthResponse`, replaced with `AccountSnapshotResponse`. Added `FirebaseBearer` + `DeviceToken` security schemes. Added `/entitlement/trial/start`, `/devices/register`, aligned `/devices/{id}/revoke` path
- Added 41 unit tests (state machine 20, entitlement service 6, devices service 9, device guard 6) — total suite now 72 tests green
- **Verified end-to-end live:** boots against real Postgres 16 + Redis 7, all routes mapped, `/health` returns full status, protected endpoints return stable `UNAUTHORIZED` ErrorEnvelope, validation errors return `VALIDATION_ERROR` with `details.issues`, Firebase Admin lazy-initializes and actually rejects bogus tokens

### Run 7 — 2026-04-13 — Auth module (Firebase verify + account sync)
- Added `FirebaseModule`/`FirebaseService` — lazy `firebase-admin` init, credentials via `FIREBASE_SERVICE_ACCOUNT_JSON` blob or discrete `FIREBASE_PROJECT_ID`/`CLIENT_EMAIL`/`PRIVATE_KEY` trio; PEM `\n` sequences auto-decoded; credential requirement enforced in non-test envs via Zod `superRefine`
- Added `AuthGuard` — verifies `Authorization: Bearer <firebase_id_token>`, attaches `request.firebaseToken` + `request.account`; returns stable `UNAUTHORIZED` `ErrorEnvelope` on any failure (missing header, non-Bearer scheme, empty/expired/invalid token)
- Added `AccountsService` — idempotent upsert keyed on `firebase_uid`; creates empty `entitlement` (state=`none`) on first verify; locale only updated when caller explicitly provides one; emails lowercased; clamp to 16 chars / fallback `en`
- Added `AuthService` + `AuthController` with `POST /v1/auth/{register,login,refresh}` — all return `AccountSnapshot` (account + entitlement); `refresh` uses `checkRevoked=true`
- Added `AllExceptionsFilter` — normalizes thrown errors into the OpenAPI `ErrorEnvelope` shape (`{ error: { code, message, details?, requestId? } }`); recognizes existing envelopes and passes them through
- Added global `/v1` prefix (with `/health` excluded for infra probes)
- Added first Prisma migration `20260413120000_init` covering all 15 V1 tables (enums, FKs, unique constraints, check constraints, indexes) — matches `schema.prisma` and Run 3 DDL; pgcrypto + citext `CREATE EXTENSION IF NOT EXISTS` emitted for safety
- Added unit tests for `AccountsService` (5 cases) and `AuthGuard` (6 cases), both with Firebase + Prisma mocked
- Extended `.env.example` with both Firebase credential options; updated `services/api/README.md` with auth + env + migration sections
- **Deviation logged in Parking Lot:** auth endpoints return `AccountSnapshot`, not the OpenAPI `AuthResponse` — reconciliation deferred to Run 8 when device slots + token issuance land

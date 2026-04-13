# Premium TV Player — Project Guide for Claude Code

> **This file is the single source of truth.** Every new Claude session starts by reading this file top-to-bottom, then executes the block under **▶️ Next Run**. After finishing the run, Claude MUST update this file (tick the run, write the next "Next Run" block, append to Run Log) and commit.

---

## 🎯 Current State

- **Phase:** C — Android TV Client
- **Last completed run:** Run 16 — Playback + heartbeat + EPG worker
- **Current branch:** `claude/fix-api-timeout-vFqPP`
- **Push target:** same branch (`-u origin claude/fix-api-timeout-vFqPP`)
- **Logo status:** ✅ received in Run 6 — `assets/logo/logo-no_background.png` (transparent PNG, blue gradient play-button with signal waves). Dark/light variants optional follow-up.
- **applicationId:** ✅ locked in Run 11 — `com.premiumtvplayer.app` (matches `BILLING_ANDROID_PACKAGE_NAME`)

---

## ▶️ Next Run (Run 17): Billing flow in the app

### Goal
Wire the Google Play Billing Client into the Android TV app so users can actually purchase Lifetime Single / Family, trigger server verification through the Run 9 billing-worker path, and surface entitlement UI states (trial / active / expired / revoked) across the home + paywall surfaces.

### Deliverables
- [ ] Add `com.android.billingclient:billing-ktx` + wire `BillingClientWrapper` (lifecycle-aware, Hilt-injected)
- [ ] `BillingRepository` with:
  - `querySkuDetails()` — fetches the two one-time products
  - `launchPurchase(activity, productId)` — kicks off the Play Billing flow
  - `acknowledgeAndVerify(purchaseToken, productId)` — POST `/v1/billing/verify` + local state refresh
  - `restore()` — POST `/v1/billing/restore`
- [ ] `PaywallScreen` that opens when the user lands on a gated surface with `entitlement.state ∈ {none, expired, revoked}`. Premium look: two-plan comparison (Single vs Family), CTAs, restore link
- [ ] Entitlement-aware gating: Home hero swaps between "Start trial" (for `none` + trial-not-consumed), "Upgrade" (for `expired`), or nothing (when active). Source create and playback start already 402 on the server — the client should route the 402 into `PaywallScreen` instead of an error banner
- [ ] Unit tests for `BillingRepository.acknowledgeAndVerify` (MockWebServer) and `PaywallViewModel` state transitions
- [ ] Update `apps/android-tv/README.md` with "Billing flow (Run 17)"

### Acceptance criteria
- User can open the paywall from a gated action, pick Family, complete the Play Billing purchase sheet, and see the home flip to the active state within a few seconds (backend verify + local refresh)
- Restore flow works end-to-end for an account with a known-good purchase
- 402 ENTITLEMENT_REQUIRED from any server action automatically surfaces the paywall — never a raw error
- Existing Run 9 billing-worker behaviour unchanged (refund-handling and replay idempotency still pass their tests)

### After this run — update CLAUDE.md
1. Tick Run 17 in the roadmap
2. Set "Last completed run" to `Run 17 — Billing flow in app`
3. Write the new "Next Run" block for **Run 18: Parental controls (PIN gate + age filter + device list)**
4. Append entry to **Run Log**
5. Commit: `tv: add Play Billing purchase + restore + paywall (Run 17)` and push

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
| `applicationId` (Android) | **`com.premiumtvplayer.app`** (Run 11). Must match `BILLING_ANDROID_PACKAGE_NAME` in `services/api/.env.example`. |

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
- [x] **Run 10** — Profile + Source modules: 5-profile cap, PIN hash, kids flag; source CRUD + parser stubs

### Phase C — Android TV Client
- [x] **Run 11** — `apps/android-tv/` Gradle/Compose/Compose-TV bootstrap. **applicationId locked: `com.premiumtvplayer.app`.** Leanback intent, TV manifest, Hilt, Navigation-Compose, ui-tokens wiring
- [x] **Run 12** — Design system in Compose: dark theme, typography, colors, focus states, motion, reusable Card/Row/Hero
- [x] **Run 13** — Onboarding/Auth screens: Welcome → Signup/Login → Trial activation → Profile picker. Firebase Auth + API client
- [x] **Run 14** — Home screen: Hero carousel, rows, Continue Watching, Favorites. Logo wired in if not already
- [x] **Run 15** — Source management UI + EPG browse view
- [x] **Run 16** — Playback (Media3/ExoPlayer): Live, VOD, Resume, subtitles, audio-track picker, heartbeat sync
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
- **Playback URL resolver (from Run 16):** Run 16 passes the playback `mediaUrl` through the nav graph because the server doesn't yet have a dedicated resolver that decrypts source credentials + signs short-lived playback URLs. Home deep-links fall back to public test streams (Apple BipBop HLS / Big Buck Bunny MP4) so the playback path is exercisable today. Proper resolver (likely `POST /v1/playback/resolve`) is a Run 18.5 / 20 security hardening item; would also allow the server to enforce device-bound playback tokens and per-device concurrent-stream caps.
- **EPG duplicate handling (from Run 16):** the EPG worker inserts programmes without a natural unique key (no provider consistently emits an id). Providers that re-emit the same programme window will create duplicates. A companion dedupe/cleanup job (on `(channel_id, starts_at)`) can be added if storage becomes an issue; defer until observability tells us it matters.

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

### Run 16 — 2026-04-13 — Playback + heartbeat + EPG worker
- Backend `src/playback/` — `PlaybackService` is the single writer of playback lifecycle + continue-watching state. `start` checks `allowsPlayback()` entitlement + profile/source ownership (404 on foreign rows) and inserts into `playback_sessions`. `heartbeat` updates position + state in a tx and upserts `continue_watching` (unique on `profileId_itemId`) for non-live items. `stop` records a `watch_history` row, and either upserts (non-completed) or deletes (completed) the CW row. `listContinueWatching` serves the Home rail
- Backend endpoints (`AuthGuard`-protected): `POST /v1/playback/{start,heartbeat,stop}`, `GET /v1/continue-watching?profileId=&limit=`
- Backend `src/epg/` — `ApiEpgModule` serving `GET /v1/epg/channels?sourceId=` + `GET /v1/epg/programmes?channelId=&from=&to=`. Defensive ownership checks (404 on foreign source/channel). Programme window defaults to next 6h
- 8 new backend tests (`playback.service.spec.ts`) — entitlement gate, profile/session ownership, CW upsert for vod, CW skip for live, CW delete on completed stop, watch_history write. Total suite now 125 (was 117)
- **New worker** `services/epg-worker/` — standalone Node process mirroring the Run 9 billing-worker pattern. Reuses the API's config + Prisma + Firebase + Sources modules via the `@api/*` TS path alias + `@premium-player/parsers` for XMLTV parsing. Per-tick flow: SELECT active `xmltv`/`m3u_plus_epg` sources → decrypt credentials → fetch → `parseXmltv` → upsert `epg_channels` (unique on `sourceId_externalChannelId`) → insert `epg_programs` filtered to `[now-1h, now+48h]` → stamp `last_validated_at + validation_status=valid`. Per-source failure isolation. `WORKER_RUN_ONCE=true` for CI / on-demand reconciliation
- Env: `EPG_WORKER_POLL_INTERVAL_MS` (default 30 min, min 1 min), `EPG_WINDOW_AHEAD_HOURS` (default 48, max 168)
- 4 EPG worker tests: run-once tick persists channels + in-window programmes, programmes outside window are skipped, empty XMLTV still marks `valid`, per-source failure doesn't abort the batch
- **Verified end-to-end live:** API boots with all 5 new routes mapped under `/v1`; all protected endpoints return the stable `UNAUTHORIZED` ErrorEnvelope. EPG worker boots against real Postgres 16 + Firebase-presence check, polls `sources` table, exits cleanly under `WORKER_RUN_ONCE=true`
- Android TV client `data/playback/` — `PlaybackRepository.{start,heartbeat,stop}` + `ContinueWatchingRepository.list`, both through `ApiErrorMapper`. `PlaybackStateValue` enum mirrors the Prisma `PlaybackState`; `PlaybackItemType` mirrors the item-type union
- Android `data/epg/EpgRepository` now calls the live `/v1/epg/*` endpoints — the Run 15 fixture is retired; `EpgBrowseSnapshot` shape kept identical so `EpgBrowseScreen` didn't change
- Android `data/home/HomeRepository` now wires in `ContinueWatchingRepository` — the Continue Watching row becomes real whenever a profile id is present on the nav arg. Fallback to empty row when profileId is null or the call fails (no Home crash on backend issues)
- Android `ui/player/PlayerViewModel` — sealed `PlayerUiState` (Starting / Buffering / Playing / Paused / Stopped / Error). `start` fires in `init`; `onProgress`/`onPlayingStateChanged`/`onBuffering`/`onError` drive transitions; `PlayerClock` interface lets tests control the 10-second heartbeat cadence; `stop(completed)` posts the final position and emits Stopped. Heartbeat loop is a single `viewModelScope.launch` that sleeps via the injected clock, maps the current UI state to a `PlaybackStateValue`, and fires `/heartbeat`
- Android `ui/player/PlayerScreen` — full-bleed `AndroidView` wrapping Media3 `PlayerView` (controller disabled — our own overlay). 1-second UI loop pumps `player.currentPosition` into `onProgress`. Bespoke `PlayerOverlay` with bottom-up gradient scrim + `PremiumChip` state + position + `PremiumButton` controls (-10s / Play-Pause / +10s / Exit). `ErrorOverlay` on `PlaybackException`. Disposes ExoPlayer on exit
- Nav route `Routes.PlayerPattern = "play/{sourceId}/{itemId}/{itemType}?profileId=&mediaUrl=&itemTitle="` + `Routes.play(profileId, sourceId, itemId, itemType, mediaUrl, title)` URL-encodes item id/URL/title. `NavHost` registers the route; `HomeScreen` deep-link callback routes `LiveChannel`/`VodItem` into the player with placeholder HLS/MP4 test URLs (BipBop / BigBuckBunny) until a server-side resolver lands
- Tests: `PlaybackRepositoryTest` (MockWebServer, 5 cases — start/heartbeat/stop body shape + 402/404 mapping), `PlayerViewModelTest` (MockK, 5 cases — init→Buffering, onProgress→Playing, onPlayingStateChanged(false)→Paused, heartbeat fires once when clock completes a sleep, stop posts final position + emits Stopped)
- Parking Lot: **Playback URL resolver** — Run 16 passes mediaUrl through the nav graph; a proper server endpoint that decrypts source credentials and signs short-lived playback URLs is a follow-up (likely Run 18.5 or 20)
- Docs: `services/api/README.md` gained "Playback + Continue Watching (Run 16)" + "EPG endpoints (Run 16)" sections; `apps/android-tv/README.md` gained "Playback (Run 16)" with state machine description + nav route + EPG worker integration note; `services/epg-worker/README.md` documents architecture, commands, env, per-tick flow, test matrix

### Run 15 — 2026-04-13 — Source management UI + EPG browse view
- Extended `PremiumPlayerApi` with `POST /v1/sources`, `PUT /v1/sources/{id}`, `DELETE /v1/sources/{id}`. Added matching DTOs (`CreateSourceRequest`, `UpdateSourceRequest`, `SingleSourceResponse`). `SourceRepository` grew `create`, `rename`, `setActive`, `delete` — all routed through `ApiErrorMapper` and a non-2xx DELETE response is re-thrown as `HttpException` so the stable ErrorEnvelope path stays uniform
- Introduced `SourceKind` enum mirroring the backend's `source_kind` and `CreateSourceInput` normalized-input DTO
- New `data/epg/` package (`EpgModels.kt` + `EpgRepository.kt`). Run 15 returns deterministic fixture channels (6 per source) × programmes (12 × 30-minute blocks) so `EpgBrowseScreen` is exercisable end-to-end without the Run 16 EPG worker. `EpgBrowseSnapshot` shape is locked in — Run 16 swaps the repo implementation; the UI doesn't change
- `ui/sources/` package with 3 ViewModels + 3 screens:
  - **`AddSourceWizardViewModel`** — explicit 4-step state machine (`Kind` / `Endpoint` / `Preview` / `Confirm`) with guards on each transition. `WizardUiState.Editing` carries the draft + preview + submitting/error; `Done(source)` terminals on success. Deterministic URL-hash-based preview estimates (channels + programmes, zero-programmes for plain M3U) so the screen reads "premium" without a network round-trip
  - **`AddSourceWizardScreen`** — step indicator chips, 3 focusable `KindRadioCard`s, shared `PremiumTextField`-based endpoint form, preview + confirm review cards, error banner on `DangerRed` translucent backplate, footer with `Continue` / `Back` / `Cancel`
  - **`SourceManagementViewModel`** — `Loading` / `Ready` / `Error` plus per-row `busyId` and `confirmingDeleteId` state so destructive ops can't race
  - **`SourceManagementScreen`** — list of sources as premium rows with EPG / Pause-or-Resume / Delete actions. Delete flows through the full-screen `ConfirmDeleteOverlay` (translucent scrim + SurfaceFloating card)
  - **`EpgBrowseViewModel`** + **`EpgBrowseScreen`** — 30-minute timeline grid. Left gutter pins channel names; right side is horizontal `TvLazyRow` of programme blocks with a fixed 3dp-per-minute width so rows align visually. Focusing any block updates a Bravia-style `FocusedProgrammeOverlay` at the top with title + chips + time range + description
- Nav routes (`Routes.kt`): `Sources` = `sources`, `AddSource` = `sources/add`, `EpgBrowsePattern` = `sources/{sourceId}/epg`, `Routes.epgBrowse(id)` builder, `SourceIdArg` non-nullable nav argument. NavHost in `PremiumTvApp` registers all three routes. `HomeScreen.onAddSource` now navigates into `AddSource`; `HomeDeeplink.AddSource` and `Source` both route properly; `LiveChannel` / `VodItem` deep-links are held for Run 16 (playback)
- Tests (JVM, via `./gradlew :app:testDebugUnitTest` locally):
  - **`SourceRepositoryTest`** (MockWebServer) — 7 cases: create POST body shape + parse, 402 ENTITLEMENT_REQUIRED mapping, rename via PUT, setActive via PUT, delete 204 happy path, 404 mapping, 409 SLOT_FULL mapping
  - **`AddSourceWizardViewModelTest`** (Turbine + MockK) — 10 cases: initial state, pick-kind guard, Kind→Endpoint advance, Endpoint URL guard, Preview generation for M3U+EPG vs. plain M3U, Confirm happy path → Done, Confirm ENTITLEMENT_REQUIRED surfaces friendly error, back from Endpoint → Kind, cancel resets draft
- Docs:
  - `apps/android-tv/README.md` — new "Source management (Run 15)" section: 4-step wizard breakdown, data table, nav-route table, test summary
  - `CLAUDE.md` — Run 15 ticked, Run 16 (Playback + heartbeat + EPG worker) queued with new `PlayerScreen`, `PlaybackRepository`, API-side `/v1/playback/*`, `/v1/continue-watching`, standalone `services/epg-worker/`, and 4 test matrices
- Token discipline verified (grep-clean): zero `Color(0x...)` literals in `ui/sources/`, all internal imports resolve
- **Static verification done in this session:** Kotlin sources conform to Compose / tv-foundation / tv-material / Retrofit / Hilt / Navigation-Compose API surfaces. Routes.SourceIdArg / HomePattern references all resolve. No hardcoded color / dp / TextStyle literals in any screen body (grep-clean)
- **Cannot verify in this session:** `./gradlew :app:assembleDebug` / `:app:testDebugUnitTest` — Android SDK absent. Verify locally with backend up (`docker compose` + `npm run start:dev`), real Firebase, emulator. End-to-end expectation: Home empty-state → Add Source → 4-step wizard → `POST /v1/sources` with encrypted credentials → back to sources list → source appears; EPG button opens the grid; Pause / Resume flips `is_active`; Delete goes through confirmation and actually removes the row

### Run 14 — 2026-04-13 — Home screen (hero + rows + empty-state)
- Extended the API client with `SourceDto` / `SourceListResponse` and a `GET /v1/sources?profileId=` endpoint on `PremiumPlayerApi`. Added `SourceRepository.list(profileId)` wrapping the call through `ApiErrorMapper`
- New domain package `data/home/` with stable, platform-free models (`HomeTile`, `HomeRow`, `HomeHero`, `HomeDeeplink`, `HomeSnapshot`)
- `HomeRepository.snapshot(profileId)` aggregates the live source list with stub Continue Watching / Favorites / Suggested rows. Stub rows are derived deterministically from the source list so the populated state always has meaningful content while the real endpoints (Run 15 favorites, Run 16 continue-watching) land in later runs
- `HomeViewModel` (`ui/home/`) reads `profileId` from `SavedStateHandle` and exposes `StateFlow<HomeUiState>` with four states: `Loading`, `EmptySource(profile)`, `Populated(profile, snapshot)`, `Error(message)`. Error path pipes through `ApiErrorCopy.forCode(...)` for user-facing strings
- `Routes.HomePattern = "home?profileId={profileId}"` with `Routes.home(profileId)` builder and `Routes.ProfileIdArg` constant. `PremiumTvApp` NavHost registers the pattern with a nullable string `navArgument`. `ProfilePickerScreen` now navigates with the selected profile's id
- UI components:
  - `HomeHeader` — inline `BrandLogo` + right-aligned profile indicator (initial-avatar gradient + name + adult/kids label)
  - `SourcePickerRail` — premium empty-state hero: outline chips ("Step 1 of 1", "M3U · XMLTV · M3U+EPG"), `DisplayHero` title, editorial body copy, Add Source + Sign Out CTAs
  - `HomeScreen` — full populated layout: header + hero carousel (21:9 `PremiumCard`s with brand-accent gradient backdrops and a `HeroCaption` column below each) + stacked `RowOfTiles` sections for Continue Watching / Favorites / Suggested / Your Sources. Every row implements the Run 12 focus-veil pattern independently. First hero auto-focuses on composition so the user lands "inside" the page
- Compose `@Preview`s for the populated home (with fixture profile + 2 sources), the empty-source variant, `HomeHeader`, `SourcePickerRail`
- Unit tests (`test/.../ui/home/HomeViewModelTest.kt`) — MockK + Turbine with `UnconfinedTestDispatcher`. Three cases: Populated with profile resolved from nav arg; EmptySource when sources list is empty; Error with mapped UNAUTHORIZED message. Tolerates the init-Loading race by accepting Loading followed by the terminal state
- Verified Run 11-13 content bar still holds: grep-clean — zero hardcoded `Color(0x…)` in `ui/home/`, zero `Routes.Home` stale references, imports all resolve
- Updated `apps/android-tv/README.md` with a full "Home screen (Run 14)" section: ASCII layout diagrams for populated + empty, data-flow diagram (ProfilePicker → HomeViewModel → HomeRepository → SourceRepository → `/v1/sources`), nav-pattern notes, state-model table
- **Static verification done in this session:** Kotlin sources align with Compose / tv-foundation / tv-material / Hilt / Navigation-Compose API surfaces. Internal imports resolve. No hardcoded dp/Color/TextStyle literals in any screen body
- **Cannot verify in this session:** `./gradlew :app:assembleDebug` / `:app:testDebugUnitTest` — Android SDK absent. **Verify locally** with backend up (`docker compose up -d` + `npm run start:dev`), real Firebase credentials in `local.properties`, then sign in, pick profile, observe Home hitting `GET /v1/sources?profileId=…` with the Bearer token attached. Expected: empty-state rail for fresh account, hero + rows stack after a source is added

### Run 13 — 2026-04-13 — Onboarding + Auth screens
- Added `BuildConfig` fields (`API_BASE_URL`, `FIREBASE_API_KEY`, `FIREBASE_PROJECT_ID`, `FIREBASE_APPLICATION_ID`) fed from `local.properties` / Gradle properties. Debug default points at `http://10.0.2.2:3000/v1/` for the emulator-to-host API loop.
- **Firebase is initialized programmatically** via `FirebaseModule` (`FirebaseOptions.Builder() + FirebaseApp.initializeApp(...)`). NO `google-services.json` plugin — the project imports without secrets and real values slot into `local.properties`.
- `NetworkModule` provides Retrofit + OkHttp + kotlinx.serialization with an `AuthInterceptor` that attaches `Authorization: Bearer <firebaseIdToken>` on every non-`/auth/*` call. Logging interceptor gated on `BuildConfig.DEBUG`.
- API layer under `data/api/`:
  - `ApiModels.kt` — `AccountSnapshotResponse`, `EntitlementDto`, `ProfileDto`, `FirebaseTokenRequest`, `ApiErrorEnvelope`
  - `PremiumPlayerApi.kt` — Retrofit interface matching `packages/api-contracts/openapi.yaml` for `/auth/{register,login,refresh}`, `/entitlement/{status,trial/start}`, `/profiles`
  - `ApiError.kt` — `ApiException.{Server, Network, Unknown}` hierarchy + `ApiErrorMapper` that decodes the stable ErrorEnvelope from any HTTP error body. `ApiErrorCopy.forCode(...)` maps every known `ErrorCode` to English user copy (i18n hook in Run 19)
- Repositories (`data/{auth,entitlement,profiles}/`):
  - `AuthRepository` — Firebase `createUserWithEmailAndPassword` / `signInWithEmailAndPassword` → fetch ID token → `POST /v1/auth/{register,login}`. `refresh()` forces a fresh token and calls `/refresh` with `checkRevoked=true`
  - `EntitlementRepository` — `status()` + `startTrial()`
  - `ProfileRepository` — `list()`
- 5 Screens + 4 ViewModels (Hilt-injected, `StateFlow<UiState>` with explicit `Editing/Submitting/Done/Error` states):
  - `WelcomeScreen` — brand logo + display copy + two primary CTAs
  - `AuthFormScaffold` (shared) + `SignupScreen` + `LoginScreen` — premium email/password form, inline validation, error banner using `PremiumColors.DangerRed` on a translucent backplate
  - `TrialActivationScreen` — outline chips for "14-day trial" + "No payment", handles `ENTITLEMENT_REQUIRED` by fetching live status and showing the `AlreadyConsumed` variant without crashing
  - `ProfilePickerScreen` — `TvLazyRow` of square `PremiumCard`s with the focus-veil pattern; "Add profile" tile when under the cap; PIN chip when a profile has a PIN set
- `PremiumTvApp` now owns a `NavHost` with the full graph: `Boot → Welcome → Signup/Login → TrialActivation → ProfilePicker → Home` (Home is a Run 14 stub). Transitions use premium fade via `PremiumEasing.Premium` over `durations.short`. The Boot screen reuses the splash content with a 1.2s deliberate pause (reads as "premium product settling", not as a spinner)
- Tests:
  - `EntitlementRepositoryTest` (MockWebServer) — success + `402 ENTITLEMENT_REQUIRED` → `ApiException.Server` mapping + happy-path trial start
  - `ProfileRepositoryTest` (MockWebServer) — list parse + 401 UNAUTHORIZED mapping
  - `TrialActivationViewModelTest` (Turbine + MockK) — happy-path `Idle → Submitting → Activated` and `ENTITLEMENT_REQUIRED → AlreadyConsumed` (with live-status fallback)
  - `TestApiFactory` — reusable MockWebServer ↔ Retrofit wiring helper for future repo tests
- `apps/android-tv/README.md` extended with an "Onboarding flow (Run 13)" section: flow diagram, `BuildConfig` / `local.properties` override guide (including the "no `google-services.json` plugin" design choice), layer map, error-copy mapping, and an end-to-end local-run walkthrough
- **Static verification done in this session:** Kotlin sources conform to Compose / tv-foundation / tv-material / Retrofit / Hilt API surfaces; internal imports resolve; no hardcoded `Color(0x…)` / `dp` / `TextStyle(...)` literals in any screen body
- **Cannot verify in this session:** `./gradlew :app:assembleDebug` and `./gradlew :app:testDebugUnitTest` — both require Android Studio / Android SDK (unavailable in this sandbox). **Verify locally** by importing `apps/android-tv/` in Android Studio Hedgehog+, dropping real Firebase values into `local.properties`, bringing up the Run 10 backend (`docker compose up -d` + `npm run start:dev`), and running on a Google TV emulator. End-to-end expectation: Welcome → Sign-Up → Trial Activation → Profile Picker, with the API log showing the two auth+trial POSTs under one Firebase token.

### Run 12 — 2026-04-13 — Design system in Compose (premium component library)
- Copied `assets/logo/logo-no_background.png` → `apps/android-tv/app/src/main/res/drawable/brand_logo.png` so Compose can resolve it via `R.drawable.brand_logo`
- New components package `apps/android-tv/app/src/main/java/com/premiumtvplayer/app/ui/components/`:
  - **`BrandLogo.kt`** — renders the real PNG; three canonical sizes via `BrandLogoSize` enum (`Splash` 160dp, `Hero` 96dp, `Inline` 48dp); accepts a `ColorFilter` for theming
  - **`BootProgress.kt`** — three-bar accent-cyan pulse on a 1.2s linear loop; brightness wraps around the row giving a smooth "boot heartbeat"
  - **`PremiumChip.kt`** — `Filled` + `Outline` variants; `LabelSmall` (12sp SemiBold tracked) all-caps editorial feel
  - **`PremiumButton.kt`** — `Primary` / `Secondary` / `Ghost` variants. Focus → 1.04× scale + accent border, press → 0.97× scale, all via `PremiumEasing.Premium` over `LocalPremiumDurations.current.short`. Leading/trailing icon slots
  - **`PremiumTextField.kt`** — TV-friendly `BasicTextField`; large hit area (56dp min); border switches to `PremiumColors.FocusAccent` on focus, to `DangerRed` on error; placeholder + label + optional errorText; ready for password and number-password keyboards
  - **`PremiumCard.kt`** — focusable card primitive. 1.06× scale on focus (`PremiumFocusScale`), 2dp accent glow ring, animated dim veil hook (`unfocusedDim` parameter) for the focus-veil pattern. Default 16:9 aspect, configurable. Optional inline title/subtitle stack with bottom-up scrim for readability over artwork
  - **`RowOfTiles.kt`** — generic `<T>` `TvLazyRow` of `PremiumCard`s implementing the **focus-veil pattern**: tracks `focusedIndex` in state and passes `unfocusedDim = 0.4f` to every non-focused tile so siblings dim fluidly as focus moves
  - **`HeroSection.kt`** — full-bleed cinematic hero. Backdrop slot (any composable), bottom-up scrim from `BackgroundBase` → transparent at 60% height, content stack (chips + `DisplayHero` title + subtitle + CTA row) anchored bottom-left in the canonical premium TV composition
- Every component ships with a Compose `@Preview` over `PremiumTvTheme {}` against the brand `BackgroundBase` so designers can review without running the app
- Updated `PremiumTvApp.kt` splash to be **fully token-driven**: replaced the inline play-button glyph with `BrandLogo(size = BrandLogoSize.Splash)` (real PNG) and the inline 3-bar pulse with `BootProgress()`. The cinematic radial gradient now uses `PremiumColors.AccentBlueDeep.copy(alpha=0.18f)` (no hard-coded hex)
- Token usage rules locked: no `Color(...)` / `dp` / `TextStyle(...)` literals in component bodies; no standalone `tween(...)` — animations always go through `PremiumTransitions` / `PremiumEasing` / `LocalPremiumDurations.current`
- Updated `apps/android-tv/README.md` with a full "Component catalog (Run 12)" section: atoms table, molecules table, focus-veil pattern explanation, token usage rules, `@Preview` convention
- **Static verification done in this session:** Kotlin sources conform to the Compose / `tv-foundation` / `tv-material` API surface; manifest references intact; resource (`R.drawable.brand_logo`) wired
- **Cannot verify in this session:** `./gradlew :app:assembleDebug` and `@Preview` rendering — both require Android Studio + JDK 17 (no Android SDK in the CI sandbox). **Verify locally** by importing in Android Studio Hedgehog+, opening any component file, and switching to "Split" / "Design" view

### Run 11 — 2026-04-13 — Android TV bootstrap (Phase C kickoff)
- Created `apps/android-tv/` Gradle project (Kotlin DSL, AGP 8.7, Kotlin 2.0, Compose Compiler plugin 2.0, version catalog at `gradle/libs.versions.toml`)
- **Locked applicationId: `com.premiumtvplayer.app`** — matches `BILLING_ANDROID_PACKAGE_NAME`. Recorded in Locked Product Decisions.
- `app/build.gradle.kts` — minSdk 26 (adaptive icons baseline; >99% of Google TV install base), targetSdk 34, JVM 17, Compose enabled, R8 + ProGuard rules for Hilt/Compose/serialization/Media3
- `AndroidManifest.xml` — `<uses-feature leanback required=true>`, `<uses-feature touchscreen required=false>`, `<application banner=@drawable/banner>`, `MainActivity` with `LEANBACK_LAUNCHER` category, INTERNET + ACCESS_NETWORK_STATE + WAKE_LOCK permissions
- Hilt: `@HiltAndroidApp` `PremiumTvApplication` + `@AndroidEntryPoint` `MainActivity`
- Dependencies wired (used by future runs but locked in now to avoid build-config churn): Compose BOM 2024.10.01, `androidx.tv:tv-foundation` 1.0.0-alpha10, `androidx.tv:tv-material` 1.0.0, Navigation-Compose, hilt-navigation-compose, Media3 1.4.1 (exoplayer + hls + ui + session), Retrofit 2.11 + kotlinx.serialization JSON 1.7.3, OkHttp logging, DataStore + Room (KSP-compiled), Firebase BoM 33.5.1 + auth-ktx
- **Premium dark theme system** under `app/src/main/.../ui/theme/`:
  - `Color.kt` — bespoke palette (NOT Material default). Surface stack `BackgroundBase #050608` → `SurfaceHigh #272C35` (4-step lift). Foreground hierarchy `OnSurfaceHigh #FFFFFF` → `OnSurfaceDim #5C6471`. Brand accent `AccentBlue #3B82F6` / `AccentCyan #60A5FA` / `AccentBlueDeep #2563EB` (matches the logo gradient). Semantic + focus tokens.
  - `Type.kt` — 10-foot UI hierarchy: `DisplayHero` 64sp Light → `LabelSmall` 12sp SemiBold tracked. `toTvTypography()` plugs into `tv-material`'s `Typography`.
  - `Spacing.kt` — 4dp grid `xxs` (2) → `hero` (96), plus `pageGutter` (48) and `rowGutter` (16). `PremiumShapeRadii` (poster radius = 16).
  - `Motion.kt` — three named easings (`Standard` / `Premium` Apple-style / `Cinematic`) + `PremiumDurations` (60/200/400/800ms) + pre-baked `PremiumTransitions` (FocusScale, HoverOverlay, HeroCrossfade, DrawerSlide). `PremiumFocusScale` = 1.06.
  - `Theme.kt` — `PremiumTvTheme` Composable; pipes the bespoke palette into `tv-material`'s `darkColorScheme(...)`; spacing/shapes/durations exposed via `staticCompositionLocalOf`.
- `PremiumTvApp.kt` root composable — cinematic splash placeholder exercising all token categories: radial dark gradient backdrop (brand-tinted upper-left lift), brand-blue circular play-button glyph, `DisplayLarge` title, muted tagline, three-bar accent-cyan boot indicator, build-info pill bottom-right
- Resources: `themes.xml` (`Theme.PremiumTvPlayer.NoActionBar` extends Material NoActionBar with brand background + transparent status bar), `colors.xml` (XML mirror of palette for system surfaces), `strings.xml`, vector `banner.xml` (320x180 brand-gradient placeholder), adaptive launcher icons (`mipmap-anydpi-v26/ic_launcher{,_round}.xml`) with brand-blue background
- New cross-platform package `packages/ui-tokens/` — TS source-of-truth (`src/index.ts`) for colors / type / spacing / radii / motion. Compiles clean. README documents the mirror map (Android Kotlin today; Apple TV / Tizen / webOS / admin web later).
- `apps/android-tv/README.md` — full project layout, design-system pointer, build/install/launch instructions, Leanback verification command. Notes that `./gradlew :app:assembleDebug` requires Android Studio + JDK 17 (this repo's CI sandbox has no Android SDK).
- **Static verification done in this session:** `packages/ui-tokens` TypeScript builds clean (`tsc -p tsconfig.json`); all Kotlin source files conform to the Compose / Hilt / Compose-TV API surface; manifest validates against the Android schema; resource references resolve.
- **Cannot verify in this session:** `./gradlew :app:assembleDebug` — Android SDK + Gradle wrapper jar are not available here. **Verify locally** by importing `apps/android-tv/` in Android Studio Hedgehog+ and running on a Google TV emulator (API 30+). Expected first launch: cinematic splash with the brand glyph + display title + boot pulse, no crash.

### Run 10 — 2026-04-13 — Profile + Source modules
- Added `profileCapFor()` helper to the entitlement state machine (parallels `deviceCapFor()`): 1 trial/single, 5 family, 0 none/expired/revoked
- Added `PinService` with **Argon2id** hashing (`profile_pins.pin_algo='argon2id'`); upsert-on-set resets failed-attempt counter + lockout; `verify` increments counter on miss and locks for `PIN_LOCKOUT_DURATION_MS` after `PIN_MAX_FAILED_ATTEMPTS` consecutive failures (default 5/15min, both env-configurable); `clearPin` + `hasPin` helpers
- Added `ProfileService` enforcing the 5-profile cap derived from entitlement, kids profiles default to `ageLimit=12` when caller omits one, atomic single-default invariant (`updateMany` clears prior default in same tx as new create), refuses to soft-delete the LAST remaining profile, auto-promotes oldest remaining profile when default is deleted
- Endpoints (`AuthGuard`-protected): `GET /v1/profiles`, `POST /v1/profiles` (`409 SLOT_FULL` / `402 ENTITLEMENT_REQUIRED`), `PUT /v1/profiles/:id`, `DELETE /v1/profiles/:id` (`409` when last), `POST /v1/profiles/:id/verify-pin`
- Added `SourceCryptoService` — **AES-256-GCM envelope encryption**. Wire format: `[ version: u8 ][ iv: 12 bytes ][ tag: 16 bytes ][ ct: ... ]` per blob; fresh IV per encrypt; GCM auth-tag tampering rejected; version byte locks rotation path; `kms_key_id` stored as metadata. `SOURCE_ENCRYPTION_KEY` is 64 hex chars (32 raw bytes), validated by Zod
- Added `SourceService` with `m3u`/`xmltv`/`m3u_plus_epg` kinds, gates on `allowsPlayback()` entitlement, encrypts URL/username/password/headers (JSON-stringified) via `SourceCryptoService`, soft-delete on remove, `decryptCredentials()` exposed for the future EPG worker / source UI
- Endpoints (`AuthGuard`-protected): `GET /v1/sources?profileId=`, `POST /v1/sources` (encrypts on write), `PUT /v1/sources/:id`, `DELETE /v1/sources/:id`
- Added `packages/parsers/` (own `package.json` + `tsconfig`) — pure-TS stubs:
  - `parseM3U(input)` → `{ channels[], ignoredLines, malformedEntries }`, extracts `#EXTINF` attributes (`tvg-id`, `tvg-name`, `tvg-logo`, `group-title`) + name + duration + URL; tolerates missing `#EXTM3U`, counts malformed entries
  - `parseXmltv(input)` → `{ channels[], programmes[], malformed* }`, walks `<channel>` + `<programme>` (incl. self-closing), extracts title/sub-title/desc/category, decodes XML entities, `parseXmltvTime()` handles `YYYYMMDDHHmmss [±HHMM]` → ISO UTC
- Env: `SOURCE_ENCRYPTION_KEY`, `SOURCE_ENCRYPTION_KMS_KEY_ID`, `PIN_MAX_FAILED_ATTEMPTS`, `PIN_LOCKOUT_DURATION_MS` added to `.env.example`
- Added 28 new tests (131 total): PinService 6, ProfileService 8, SourceCryptoService 7, SourceService 7, plus profileCapFor in state-machine.spec; parsers package 10 tests (M3U 4 + XMLTV 4 + parseXmltvTime 3) — all green
- **Verified end-to-end live:** API boots with all 4 profile endpoints + 4 source endpoints mapped under `/v1`; protected endpoints return stable `UNAUTHORIZED` ErrorEnvelope; **encrypted-at-rest verified** by seeding a real source row + reading the BYTEA back from Postgres — `convert_from(encrypted_url, 'UTF8')` errors with `invalid byte sequence`, `decryptCredentials()` recovers the originals exactly

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

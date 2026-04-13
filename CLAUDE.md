# Premium TV Player — Project Guide for Claude Code

> **This file is the single source of truth.** Every new Claude session starts by reading this file top-to-bottom, then executes the block under **▶️ Next Run**. After finishing the run, Claude MUST update this file (tick the run, write the next "Next Run" block, append to Run Log) and commit.

---

## 🎯 Current State

- **Phase:** A — Foundation & Specs
- **Last completed run:** Run 3 — Data model (split into 3 parts)
- **Current branch:** `claude/split-aber-three-parts-jBX8l`
- **Push target:** same branch (`-u origin claude/split-aber-three-parts-jBX8l`)
- **Logo status:** ⏳ pending — Claude will ask the user in **Run 6**
- **applicationId:** ⏳ to be decided in **Run 11**

---

## ▶️ Next Run (Run 4): API Contracts (OpenAPI + Zod)

### Goal
Produce the V1 REST API contract as a single OpenAPI 3.1 document plus runtime-validated Zod schemas that both the NestJS backend (Run 6+) and the Android TV client (Run 11+) will generate types from. Pure contract — no implementation, no server code.

### Deliverables
- [ ] `packages/api-contracts/package.json` minimal (name, private, no deps yet — Run 6 wires them)
- [ ] `packages/api-contracts/openapi.yaml` — OpenAPI 3.1 covering V1 surface:
  - Auth: `POST /v1/auth/register`, `POST /v1/auth/login`, `POST /v1/auth/refresh`, `POST /v1/auth/logout`
  - Account: `GET /v1/me`, `PATCH /v1/me`, `DELETE /v1/me`
  - Entitlement: `GET /v1/entitlement`, `POST /v1/entitlement/trial`
  - Devices: `GET /v1/devices`, `POST /v1/devices`, `DELETE /v1/devices/:id`
  - Profiles: `GET /v1/profiles`, `POST /v1/profiles`, `PATCH /v1/profiles/:id`, `DELETE /v1/profiles/:id`, `POST /v1/profiles/:id/pin`, `POST /v1/profiles/:id/pin/verify`
  - Sources: `GET /v1/sources`, `POST /v1/sources`, `PATCH /v1/sources/:id`, `DELETE /v1/sources/:id`, `POST /v1/sources/:id/refresh`
  - EPG: `GET /v1/epg/channels`, `GET /v1/epg/programs?channel_id&from&to`
  - Activity: `GET /v1/continue-watching`, `PUT /v1/continue-watching`, `GET /v1/favorites`, `PUT /v1/favorites/:asset_ref`, `DELETE /v1/favorites/:asset_ref`, `POST /v1/watch-history`
  - Playback: `POST /v1/playback/sessions`, `POST /v1/playback/sessions/:id/heartbeat`, `POST /v1/playback/sessions/:id/end`
  - Billing: `POST /v1/billing/google-play/verify`, `POST /v1/billing/restore`
- [ ] Reusable schema components mirroring the data model (Account, Profile, Device, Entitlement, Purchase, Source, EpgChannel, EpgProgram, ContinueWatchingItem, FavoriteItem, PlaybackSession, ProblemDetails)
- [ ] RFC-9457 `application/problem+json` error envelope; standard error codes table
- [ ] Bearer auth (Firebase ID token) as the global security scheme
- [ ] `packages/api-contracts/zod/*.ts` — Zod schemas matching every component (no generation yet, hand-written; Run 6 can swap to `openapi-zod-client` later)
- [ ] `packages/api-contracts/README.md` — how to consume (server validate, client types)

### Acceptance criteria
- Every write path from `docs/product/user-flows.md` has a matching endpoint
- Every table in `docs/architecture/data-model*.md` that users touch has at least one read path
- All 6 entitlement states are representable in the `Entitlement` schema
- Error envelope is consistent across all endpoints (RFC 9457)
- `openapi.yaml` validates against the OpenAPI 3.1 JSON Schema (no linter errors)
- Zod schemas compile as standalone TS (tsc `--noEmit` would pass in isolation)
- A frontend dev can build the onboarding + home + playback flows against this contract alone

### After this run — update CLAUDE.md
1. Tick Run 4 in the roadmap
2. Set "Last completed run" to `Run 4 — API contracts`
3. Write the new "Next Run" block for **Run 5: Entitlement state machine + billing event handling**
4. Append entry to **Run Log**
5. Commit: `contracts: add OpenAPI 3.1 + Zod (Run 4)` and push to the same branch

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
- [x] **Run 3** — Data model: SQL schemas + ER diagram (`docs/architecture/data-model.md`, split into 3 parts)
- [ ] **Run 4** — API contracts: OpenAPI 3.1 + Zod (`packages/api-contracts/`)
- [ ] **Run 5** — Entitlement state machine + billing event handling (`docs/architecture/entitlement-state-machine.md`)

### Phase B — Backend V1
- [ ] **Run 6** — NestJS bootstrap (`services/api/`): Prisma, Postgres, Redis, docker-compose, health endpoint, env setup. **→ Claude asks user for logo upload into `assets/logo/` here.**
- [ ] **Run 7** — Auth module: Firebase Admin token verify, user sync, register/login/refresh
- [ ] **Run 8** — Entitlement module: trial start, status, device register/list/revoke
- [ ] **Run 9** — Billing worker: Play Billing verification, ack, lifetime flip, refund handler
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
6. **Push** to `claude/split-aber-three-parts-jBX8l` with `git push -u origin claude/split-aber-three-parts-jBX8l` (retry on network error: 2s, 4s, 8s, 16s)
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

- **Status:** pending upload
- **Target path:** `assets/logo/` (PNG + SVG preferred; provide a dark and a light variant if possible)
- **Requested in:** Run 6

---

## 🅿️ Parking Lot

(Ideas or deferred items captured during any run that are NOT in the current scope. Claude adds here instead of acting on them.)

- _(empty)_

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

### Run 3 — 2026-04-13 — Data model (split into 3 parts)
- Wrote `docs/architecture/data-model.md` as an **index** with design principles, entitlement-state recap, and a consolidated mermaid `erDiagram` covering all 15 tables
- Wrote `docs/architecture/data-model-part-1-identity.md`: narrative + PostgreSQL DDL + indexes + flow mapping for `accounts`, `profiles`, `profile_pins`, `devices`
- Wrote `docs/architecture/data-model-part-2-commerce-sources.md`: `entitlement_state` + `source_kind` enums, DDL + indexes + flow mapping for `entitlements`, `purchases`, `sources`, `source_credentials`
- Wrote `docs/architecture/data-model-part-3-epg-activity.md`: DDL + indexes + flow mapping for `epg_channels`, `epg_programs`, `watch_history`, `continue_watching`, `favorites`, `playback_sessions`, `audit_log` plus cross-cutting notes (AES-256-GCM encryption at rest, argon2id PINs, soft-delete, UUID/bigserial PKs, Family cap enforcement, Prisma handoff)
- Split performed on user request; `data-model.md` remains the canonical entry point referenced by later runs

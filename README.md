<p align="center">
  <img src="https://raw.githubusercontent.com/BEKO2210/Ibo_Player_Pro/main/assets/logo/Logo_ani.gif" alt="Premium TV Player" width="520"/>
</p>

<h1 align="center">Premium TV Player</h1>

<p align="center">
  <strong>A premium, account-based TV player for the living room.</strong><br/>
  Android TV first &middot; server-authoritative entitlements &middot; 14-day trial &middot;
  5 profiles &middot; family plan &middot; cloud-synced.
</p>

<p align="center">
  <a href="./CLAUDE.md"><img alt="status" src="https://img.shields.io/badge/phase-D%20·%20polish%20%26%20ship--ready-1f6feb?style=flat-square"/></a>
  <img alt="platform" src="https://img.shields.io/badge/platform-Android%20TV-050608?style=flat-square&logo=android&logoColor=3B82F6"/>
  <img alt="backend" src="https://img.shields.io/badge/backend-NestJS%20·%20Postgres%2016%20·%20Redis%207-272C35?style=flat-square"/>
  <img alt="tests" src="https://img.shields.io/badge/tests-143%20green-2EA043?style=flat-square"/>
  <img alt="license" src="https://img.shields.io/badge/license-Proprietary-DC2626?style=flat-square"/>
</p>

---

## What this is

**Premium TV Player** is a neutral, premium-feel TV player that ships **empty** — users
add their own M3U / M3U8 / XMLTV sources. We don't host content. We host the
account, the entitlement, the parental controls, the device slots, the watch
history. Everything that turns a player into a *product*.

The bar we hold ourselves to is the on-device experience of **Sony Bravia**,
**Apple TV**, and **Netflix** — large heroes, elegant typography, slow scrims,
buttery focus animations, no Material defaults bleeding through.

> Server is the source of truth. Trial state, entitlement state, device slots,
> profile caps — all enforced server-side. The client is a renderer.

---

## Highlights

| | |
|---|---|
| **Auth** | Firebase email/password &rarr; own account/entitlement layer |
| **Trial** | 14-day server-side, consume-once, enforced on every session resume |
| **Monetization** | Lifetime Single (€19.99–24.99) &middot; Lifetime Family (€39.99–49.99) via Google Play Billing — server-verified, refund/revoke aware |
| **Profiles** | Up to 5 per account &middot; Argon2id PIN gate &middot; kids profile + age filter (MPAA / TV / BBFC / FSK) |
| **Devices** | Account-based slots (1 / 1 / 5 by tier) &middot; sha-256 device tokens &middot; **never** MAC-bound |
| **Sources** | M3U / M3U8 / XMLTV / M3U+EPG &middot; AES-256-GCM envelope encryption at rest |
| **EPG** | Standalone XMLTV worker &middot; 48h forward window &middot; Bravia-style focused-programme overlay |
| **Playback** | Media3 / ExoPlayer &middot; 10 s heartbeat &middot; resume + continue-watching synced server-side |
| **Diagnostics** | Hidden long-press on the boot pill &rarr; live `/health`, build info, in-memory error log (cap 50) |
| **i18n** | English baseline + German seed &middot; `UserErrorMessage` sealed type for locale-aware error rendering |

---

## Architecture

```
                              ┌────────────────────────┐
                              │   Google Play Billing  │
                              └─────────┬──────────────┘
                                        │ purchase token
   ┌─────────────────────┐              ▼
   │   Android TV app    │      ┌───────────────────┐         ┌──────────────┐
   │  Compose + Media3   │◀────▶│   NestJS  /v1     │◀───────▶│ Postgres 16  │
   │  Hilt · TV-Material │      │  Auth · Entitle   │         │  + pgcrypto  │
   └──────────┬──────────┘      │  Devices · Source │         └──────────────┘
              │                 │  Profile · Play   │         ┌──────────────┐
              │ ID-token        │  Billing · EPG    │◀───────▶│   Redis 7    │
              ▼                 └─┬─────────────┬───┘         └──────────────┘
   ┌─────────────────────┐        │             │
   │  Firebase Auth      │        ▼             ▼
   └─────────────────────┘  ┌──────────┐  ┌──────────────┐
                            │ billing- │  │  epg-worker  │
                            │  worker  │  │   (XMLTV)    │
                            └──────────┘  └──────────────┘
```

Two truths the architecture protects:

1. **Entitlement is server-authoritative.** Trial start, refund handling, slot caps,
   PIN lockout — all decided by the API. The app never trusts a local timestamp.
2. **Account &gt; device.** A user can move between TVs without losing access. Slots
   are issued and revoked centrally; tokens are sha-256-hashed at rest.

See [`docs/architecture/`](./docs/architecture/) for the full data model,
entitlement state machine, and event flow.

---

## Tech stack

**Backend** &nbsp;`TypeScript` `NestJS 10` `Prisma 5.22` `PostgreSQL 16` `Redis 7` `Zod` `Firebase Admin` `OpenAPI 3.1`
**Workers** &nbsp;`Node 22` `google-auth-library` (Play verify) &middot; standalone XMLTV reconciliation
**Android TV** &nbsp;`Kotlin 2.0` `Jetpack Compose` `Compose for TV` `Media3/ExoPlayer 1.4` `Hilt` `Retrofit` `kotlinx.serialization` `Play Billing 7.1` `Firebase Auth`
**Shared** &nbsp;`packages/parsers` (M3U + XMLTV) &middot; `packages/api-contracts` (OpenAPI + Zod) &middot; `packages/ui-tokens` (cross-platform design tokens) &middot; `packages/entitlement-engine`
**Infra** &nbsp;`Docker Compose` &middot; `docker compose up -d` brings up Postgres + Redis with healthchecks

---

## Repo layout

```
premium-player/
├── apps/
│   ├── android-tv/                ← V1 client (Compose for TV, Media3)
│   ├── android-mobile/            ← V2
│   ├── admin-web/                 ← V2
│   └── apple-tv/ samsung-tv/ lg-tv/   ← V3
├── services/
│   ├── api/                       ← NestJS · single source of truth
│   ├── billing-worker/            ← Play Billing reconciliation
│   └── epg-worker/                ← XMLTV reconciliation
├── packages/
│   ├── api-contracts/             ← OpenAPI 3.1 + Zod
│   ├── parsers/                   ← M3U + XMLTV (pure TS)
│   ├── ui-tokens/                 ← color · type · spacing · motion
│   ├── entitlement-engine/        ← pure transition function
│   ├── domain/  i18n/             ← shared models + string keys
├── infra/
│   ├── docker/                    ← compose · postgres init · redis
│   └── ci/
├── docs/
│   ├── product/                   ← PRD, user flows
│   ├── architecture/              ← data model, state machine
│   └── ux/                        ← design notes
└── assets/logo/                   ← brand
```

---

## Quick start

### 1. Backend (Postgres + Redis + API)

```bash
# bring up data plane
docker compose -f infra/docker/docker-compose.yml up -d

# install + migrate
cd services/api
cp .env.example .env          # fill in FIREBASE_* + SOURCE_ENCRYPTION_KEY
npm install
npx prisma migrate deploy
npx prisma generate

# run
npm run start:dev             # http://localhost:3000/v1
curl http://localhost:3000/health
```

### 2. Workers (separate processes)

```bash
cd services/billing-worker && npm install && npm run start
cd services/epg-worker     && npm install && npm run start
# add WORKER_RUN_ONCE=true to either for a one-shot reconciliation
```

### 3. Android TV

```bash
# in apps/android-tv/local.properties
FIREBASE_API_KEY=...
FIREBASE_PROJECT_ID=...
FIREBASE_APPLICATION_ID=1:0:android:0
# optional override (defaults to http://10.0.2.2:3000/v1/ for the emulator):
# API_BASE_URL=http://192.168.1.50:3000/v1/

./gradlew :app:installDebug
adb shell am start -n com.premiumtvplayer.app/.MainActivity
```

> **No `google-services.json` needed.** Firebase is initialized programmatically
> from `BuildConfig`, so you can clone and import without secrets in the tree.

Open in Android Studio Hedgehog+ on JDK 17. The Leanback launcher intent makes
it appear on Google TV emulators (API 30+).

---

## Tests &amp; quality bar

```bash
# backend
cd services/api          && npm test       # 125 tests
cd packages/parsers      && npm test       #  10 tests
cd services/billing-worker && npm test     #   4 tests
cd services/epg-worker   && npm test       #   4 tests
                                           # ──────────
                                           # 143 green

# android TV (JVM unit tests)
cd apps/android-tv && ./gradlew :app:testDebugUnitTest
```

What we test:
- entitlement state-machine transitions (every event &times; every state)
- billing replay/idempotency, ack-on-first-verify, refund fallback
- AES-256-GCM round-trip + tamper rejection on source credentials
- PIN Argon2id verify + lockout counter
- profile cap derivation from entitlement, last-profile delete guard
- repository layer: every endpoint &times; happy path &times; ErrorEnvelope mapping
- ViewModels: Turbine + MockK on every state transition

---

## Roadmap

```
Phase A — Foundation & Specs                        ✓ Run 1–5
Phase B — Backend V1                                ✓ Run 6–10
Phase C — Android TV Client                         ✓ Run 11–18
Phase D — Polish & Ship-Ready                       ▶ Run 19 (done) · Run 20 (current)

Buffer (21+) — Recording · Admin web · Mobile · CI/CD
```

The full 20-run plan, locked product decisions, and per-run protocol live in
[`CLAUDE.md`](./CLAUDE.md). That file is the project's single source of truth.

---

## Documentation map

| Doc | What's in it |
|---|---|
| [`CLAUDE.md`](./CLAUDE.md) | locked decisions · roadmap · per-run protocol · run log |
| [`docs/product/PRD.md`](./docs/product/PRD.md) | vision · personas · scope · monetization |
| [`docs/product/user-flows.md`](./docs/product/user-flows.md) | 17 mermaid flows |
| [`docs/architecture/data-model.md`](./docs/architecture/data-model.md) | 15-table relational model + ER diagram |
| [`docs/architecture/entitlement-state-machine.md`](./docs/architecture/entitlement-state-machine.md) | states · transitions · billing event mapping |
| [`packages/api-contracts/openapi.yaml`](./packages/api-contracts/openapi.yaml) | full V1 contract |
| [`apps/android-tv/README.md`](./apps/android-tv/README.md) | client architecture · component catalog · per-run notes |
| [`services/api/README.md`](./services/api/README.md) | quickstart · env reference · troubleshooting |

---

## Security &amp; principles we will not bend

- **Never** MAC-address-based device binding. Slots are server-managed, account-bound.
- **Never** trust client-side trial / entitlement state. Server decides.
- **Never** ship source credentials in plaintext. AES-256-GCM at rest, fresh IV per write.
- **Never** weaken the PIN gate. Argon2id, server-side counter, server-side lockout window.
- **Never** add an OSS license. This repo is proprietary.

---

## License

Proprietary &middot; All Rights Reserved &middot; see [`LICENSE`](./LICENSE).
Not open source. Not for redistribution. Not for derivative work without
prior written consent.

<p align="center"><sub>© 2026 Premium TV Player</sub></p>

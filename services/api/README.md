# Premium TV Player — API (V1)

NestJS backend service for Premium TV Player. Scaffolded in Run 6.

- Runtime: Node.js >= 20.11
- Framework: NestJS 10 (TypeScript strict)
- ORM: Prisma 5 → PostgreSQL 16
- Cache / sessions: Redis 7 (ioredis)
- Env validation: Zod

## Prerequisites

- Node.js 20+ and npm
- Docker + Docker Compose (for local Postgres + Redis)

## Quickstart

```bash
# 1. start Postgres + Redis (from repo root)
docker compose -f infra/docker/docker-compose.yml up -d

# 2. install deps (in this folder)
cd services/api
npm install

# 3. copy env template
cp .env.example .env

# 4. generate Prisma client (after first install and schema changes)
npm run prisma:generate

# 5. run dev server with watch
npm run start:dev
```

Health check:

```bash
curl http://localhost:3000/health
```

Expected response shape (Terminus):

```json
{
  "status": "ok",
  "info": {
    "database": { "status": "up" },
    "redis":    { "status": "up" },
    "service":  { "status": "up", "name": "premium-player-api" }
  },
  "error": {},
  "details": { "...": "..." }
}
```

## Scripts

| Command                      | Purpose                                               |
|------------------------------|-------------------------------------------------------|
| `npm run start`              | Start (no watch)                                      |
| `npm run start:dev`          | Start with watch mode                                 |
| `npm run start:prod`         | Run built JS from `dist/`                             |
| `npm run build`              | Compile TypeScript via Nest CLI                       |
| `npm test`                   | Unit tests (Jest)                                     |
| `npm run test:e2e`           | End-to-end tests (Jest + Supertest)                   |
| `npm run lint`               | ESLint (auto-fix)                                     |
| `npm run format`             | Prettier write                                        |
| `npm run prisma:generate`    | Regenerate Prisma client                              |
| `npm run prisma:migrate`     | Create/apply dev migration                            |
| `npm run prisma:migrate:deploy` | Apply migrations (CI / prod)                       |
| `npm run prisma:studio`      | Open Prisma Studio                                    |

## Project layout

```
services/api/
  prisma/
    schema.prisma              # V1 data model (Run 3 aligned)
    migrations/
      20260413120000_init/     # first migration (15 V1 tables)
        migration.sql
      migration_lock.toml
  src/
    main.ts                    # bootstrap (global prefix /v1)
    app.module.ts              # root module + global exception filter
    common/
      errors.ts                # stable ErrorCode + ErrorEnvelope
      http-exception.filter.ts # normalizes errors → ErrorEnvelope
    config/
      configuration.ts         # Zod env + typed AppConfig + Firebase creds
    prisma/
      prisma.module.ts         # @Global
      prisma.service.ts
    redis/
      redis.module.ts          # @Global
      redis.service.ts
    firebase/
      firebase.module.ts       # @Global
      firebase.service.ts      # lazy-init firebase-admin + verifyIdToken
    health/
      health.module.ts
      health.controller.ts     # GET /health (db + redis + service)
    auth/
      auth.module.ts
      auth.controller.ts       # POST /v1/auth/{register,login,refresh}
      auth.service.ts
      auth.guard.ts            # verifies Bearer <firebase_id_token>
      accounts.service.ts      # idempotent account sync + snapshot
      dto.ts
      *.spec.ts
    entitlement/
      entitlement.module.ts
      entitlement.controller.ts   # GET /v1/entitlement/status + trial/start
      entitlement.service.ts
      entitlement.state-machine.ts  # pure transition function (Run 5 aligned)
      *.spec.ts
    devices/
      devices.module.ts
      devices.controller.ts    # POST /v1/devices/register|:id/revoke, GET /v1/devices
      devices.service.ts       # slot-cap enforcement + token hashing
      device.guard.ts          # validates X-Device-Token
      dto.ts
      *.spec.ts
  .env.example                 # copy to .env
  package.json
  tsconfig.json
```

## Environment variables

See `.env.example`. All values are validated at boot by
`src/config/configuration.ts`; invalid/missing values fail fast.

| Key            | Required | Default                  | Notes                                 |
|----------------|----------|--------------------------|---------------------------------------|
| `NODE_ENV`     | no       | `development`            | development / test / staging / production |
| `API_HOST`     | no       | `0.0.0.0`                |                                       |
| `API_PORT`     | no       | `3000`                   |                                       |
| `LOG_LEVEL`    | no       | `log`                    | error / warn / log / debug / verbose  |
| `DATABASE_URL` | **yes**  | (see `.env.example`)     | Postgres connection string            |
| `REDIS_URL`    | **yes**  | (see `.env.example`)     | Redis connection string               |
| `FIREBASE_SERVICE_ACCOUNT_JSON` | **yes** (option A) | — | Full service-account JSON blob. Mutually exclusive with the trio below. |
| `FIREBASE_PROJECT_ID`           | **yes** (option B) | — | Firebase project ID                   |
| `FIREBASE_CLIENT_EMAIL`         | **yes** (option B) | — | Service-account client email          |
| `FIREBASE_PRIVATE_KEY`          | **yes** (option B) | — | PEM key; `\n` sequences are decoded   |
| `BILLING_ANDROID_PACKAGE_NAME`  | yes (for billing) | — | Google Play package id (e.g. `com.premiumtvplayer.app`) |
| `BILLING_PRODUCT_ID_SINGLE`     | no              | `premium_player_single` | SKU mapping for `lifetime_single` |
| `BILLING_PRODUCT_ID_FAMILY`     | no              | `premium_player_family` | SKU mapping for `lifetime_family` |
| `BILLING_WORKER_POLL_INTERVAL_MS` | no            | `15000`                 | Worker poll interval (1s..5min)   |

In test environments (`NODE_ENV=test`) Firebase credentials are optional —
the auth module is expected to be mocked.

## Auth module (Run 7)

- **Token source:** clients authenticate end-users via Firebase Auth
  (email / password) and send the resulting ID token to the backend.
- **Protected endpoints:** expect `Authorization: Bearer <firebase_id_token>`.
  `AuthGuard` verifies the token via Firebase Admin and attaches
  `request.firebaseToken` + `request.account` on success. Any failure
  returns `401` with the stable `ErrorEnvelope` shape (`{ error: { code:
  "UNAUTHORIZED", message, details?, requestId? } }`).
- **Account sync:** on first successful verify, a local `accounts` row is
  created (keyed on `firebase_uid`) and an empty `entitlements` row with
  `state = none` is attached. Subsequent verifies update `email` /
  `email_verified`, and `locale` when the caller explicitly provides one.

### Endpoints

All three endpoints take a JSON body `{ firebaseIdToken: string, locale?: string }`
and return `{ account, entitlement }`:

| Method | Path                  | Purpose                                              |
|--------|-----------------------|------------------------------------------------------|
| POST   | `/v1/auth/register`   | First sync after Firebase signup (creates account)   |
| POST   | `/v1/auth/login`      | Token verify + account sync                          |
| POST   | `/v1/auth/refresh`    | Token re-verify with revocation check                |

OpenAPI contract (`packages/api-contracts/openapi.yaml`) was reconciled in
Run 8 to match: Firebase-only auth, no custom access/refresh/device
tokens. Device registration is a separate explicit flow — see below.

## Entitlement module (Run 8)

The server-authoritative entitlement lifecycle lives in
`src/entitlement/`. It is the single source of truth for what the caller
can do — playback, device registration, profile creation. See
`docs/architecture/entitlement-state-machine.md` for the full transition
table; the pure TypeScript implementation is in
`src/entitlement/entitlement.state-machine.ts`.

### Endpoints (all require `Authorization: Bearer <firebase_id_token>`)

| Method | Path                              | Purpose                                                       |
|--------|-----------------------------------|---------------------------------------------------------------|
| GET    | `/v1/entitlement/status`          | Current entitlement (auto-expires stale trials on read)       |
| POST   | `/v1/entitlement/trial/start`     | Start 14-day trial; `402 ENTITLEMENT_REQUIRED` if already consumed |

### Derived caps

| State             | Devices | Profiles | Playback |
|-------------------|---------|----------|----------|
| `none`            | 0       | 0        | no       |
| `trial`           | 1       | 1        | yes      |
| `lifetime_single` | 1       | 1        | yes      |
| `lifetime_family` | 5       | 5        | yes      |
| `expired`         | 0 new   | 0 new    | no       |
| `revoked`         | 0 new   | 0 new    | no       |

## Billing module (Run 9)

The billing layer is the **single writer** of purchase- and refund-driven
entitlement transitions. Both the API endpoints and the
`services/billing-worker` process go through `BillingService.applyVerified`
so they cannot diverge.

### Endpoints (`AuthGuard`-protected)

| Method | Path                | Purpose                                                                                |
|--------|---------------------|----------------------------------------------------------------------------------------|
| POST   | `/v1/billing/verify` | Verify a single purchase token with Google Play and apply the resulting event.         |
| POST   | `/v1/billing/restore` | Re-verify all non-refunded purchases for the caller and re-apply to entitlement.       |

Body for `/verify`: `{ purchaseToken: string, productId: string }`.
Both return the current `EntitlementStatusResponse`.

### Provider abstraction

`src/billing/providers/provider.interface.ts` defines
`ProviderVerificationClient`. The default binding in `BillingModule` is
`GooglePlayProvider`, which uses the Firebase service-account credentials
(must also have the `androidpublisher` GCP IAM scope) and calls
`androidpublisher.googleapis.com/v3` directly via `google-auth-library`.
Tests substitute via the DI token `PROVIDER_VERIFICATION_CLIENT`.

### Idempotency + concurrency

- `purchases` is unique on `(provider, purchase_token)` — replays just
  upsert the row with the latest payload.
- Before any entitlement mutation we take a row-level lock:
  ```sql
  SELECT id FROM entitlements WHERE account_id = $1 FOR UPDATE
  ```
  inside the same transaction as the upsert + `entitlement.update`.
- Replay detection: when the persisted purchase already matches the
  provider state AND the entitlement state already reflects the target,
  the transition is skipped entirely.

### See also

`services/billing-worker/README.md` — the polling reconciliation process
that retries unacked / pending purchases on the configured interval.

## Devices module (Run 8)

Device slots are server-managed and enforced against the entitlement cap.
The plaintext `deviceToken` is returned exactly once at registration; the
server stores only its sha256 hash. Subsequent device-bound requests
present it via the `X-Device-Token` header (see `DeviceGuard`).

### Endpoints (all require `Authorization: Bearer <firebase_id_token>`)

| Method | Path                           | Status codes                                      |
|--------|--------------------------------|---------------------------------------------------|
| POST   | `/v1/devices/register`         | `201`, `402 ENTITLEMENT_REQUIRED`, `409 SLOT_FULL` |
| GET    | `/v1/devices`                  | `200` — list with `isCurrent` / `isRevoked`        |
| POST   | `/v1/devices/:id/revoke`       | `200`, `404`                                       |

### DeviceGuard

`src/devices/device.guard.ts` validates `X-Device-Token`, attaches
`request.device`, and issues a fire-and-forget `last_seen_at` touch. It
**must** run AFTER `AuthGuard` (it relies on `request.account`). Run 8 does
not apply it to any route yet — it will be wired into playback / source
mutation endpoints in Runs 15–16.

## Migrations

Initial migration shipped in Run 7: `prisma/migrations/20260413120000_init/`
covers all 15 V1 tables, enums, FKs, unique constraints, and indexes — kept
in sync with `prisma/schema.prisma` and `docs/architecture/data-model.md`.

```bash
# Apply pending migrations to the local docker-compose Postgres
npm run prisma:migrate        # create/apply dev migration (and generate client)
npm run prisma:migrate:deploy # apply only (CI / prod)
```

The init migration expects the `pgcrypto` and `citext` extensions, which are
pre-created by `infra/postgres/init/01-extensions.sql` on the docker-compose
stack. `CREATE EXTENSION IF NOT EXISTS` is also emitted by the migration
itself so it is safe to run on any fresh Postgres 16 with sufficient privileges.

## Troubleshooting

- `Invalid environment configuration: ...` — a required env var is missing.
  Check `.env` against `.env.example`.
- Prisma cannot connect → verify docker compose is up:
  `docker compose -f infra/docker/docker-compose.yml ps`.
- Redis health `down` → container may still be starting; compose healthcheck
  needs a few seconds on cold start.

## License

Proprietary — see repo root `LICENSE`.

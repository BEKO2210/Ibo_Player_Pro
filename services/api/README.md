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
      dto.ts                   # class-validator DTOs
      *.spec.ts                # unit tests
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

**Note — contract deviation:** `packages/api-contracts/openapi.yaml`
currently defines `AuthResponse` with additional `accessToken` /
`refreshToken` / `deviceToken` fields. Custom token issuance + device
slot binding land in **Run 8 (Devices + Entitlement)**, at which point
the OpenAPI contract will be reconciled. Run 7 endpoints return a pure
account + entitlement snapshot.

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

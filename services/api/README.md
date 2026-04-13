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
    schema.prisma         # V1 data model (Run 3 aligned)
  src/
    main.ts               # bootstrap
    app.module.ts         # root module
    config/
      configuration.ts    # Zod-validated env + typed AppConfig
    prisma/
      prisma.module.ts    # @Global
      prisma.service.ts
    redis/
      redis.module.ts     # @Global
      redis.service.ts
    health/
      health.module.ts
      health.controller.ts  # GET /health (db + redis + service)
  .env.example            # copy to .env
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

## Migrations

No migrations yet. The first migration will be generated in the upcoming
**Run 7 (Auth module)** or as part of the subsequent domain modules, once the
first domain-specific writes are needed. The schema at
`prisma/schema.prisma` is aligned with `docs/architecture/data-model.md`.

## Troubleshooting

- `Invalid environment configuration: ...` — a required env var is missing.
  Check `.env` against `.env.example`.
- Prisma cannot connect → verify docker compose is up:
  `docker compose -f infra/docker/docker-compose.yml ps`.
- Redis health `down` → container may still be starting; compose healthcheck
  needs a few seconds on cold start.

## License

Proprietary — see repo root `LICENSE`.

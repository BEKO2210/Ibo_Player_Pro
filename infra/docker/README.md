# Local Dev Stack

Starts Postgres 16 + Redis 7 for `services/api`.

## Commands

```bash
# from repo root
docker compose -f infra/docker/docker-compose.yml up -d
docker compose -f infra/docker/docker-compose.yml ps
docker compose -f infra/docker/docker-compose.yml logs -f
docker compose -f infra/docker/docker-compose.yml down          # stop
docker compose -f infra/docker/docker-compose.yml down -v       # stop + wipe volumes
```

## Defaults

| Service  | Host port | User       | Password | DB / notes           |
|----------|-----------|------------|----------|----------------------|
| Postgres | 5432      | `premium`  | `premium`| `premium_player`     |
| Redis    | 6379      | —          | —        | appendonly enabled   |

Matches `services/api/.env.example`. Override via `.env` in `services/api/`.

## Extensions

`infra/postgres/init/01-extensions.sql` enables `pgcrypto` and `citext` on
first container init (required by the Prisma schema in `services/api/prisma/schema.prisma`).

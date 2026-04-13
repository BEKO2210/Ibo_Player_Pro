# Upgrades, migrations, rollback

The deployment shape we optimize for: **one host, three systemd services,
zero scheduled downtime**. Every change is shaped to be deployable while
the API keeps serving requests.

## The non-negotiable rules

1. **Migrations are always backward-compatible.** A migration may not
   break the previous version of the application code that is still
   running. This means: never `DROP COLUMN` and replace with a new one in
   the same release.
2. **Code rolls forward, schema rolls forward.** Rolling code back to a
   previous tag must succeed against the new schema. Schema rollback is
   reserved for true emergencies (corrupted migration that cannot be
   forward-fixed).
3. **One change per deploy.** API + worker + schema upgrades go in
   separate deploys when their order matters. Bundle only when they're
   orthogonal.

## Backward-compatible migration patterns

### Adding a column (safe by default)

```sql
ALTER TABLE accounts ADD COLUMN locale_preference TEXT;
```

A nullable add is always safe. If you need a default, add it nullable
first, backfill, then add the `NOT NULL` constraint in a follow-up
release.

### Renaming a column (three releases)

| Release | Code does | Schema does |
|---|---|---|
| 1 | reads + writes both old and new column | adds new column nullable, backfill copies old → new |
| 2 | reads + writes only the new column | (no schema change) |
| 3 | (no code change) | drops the old column |

Skipping releases here corrupts running pods and breaks rollback.

### Removing a column (two releases)

| Release | Code does | Schema does |
|---|---|---|
| 1 | stops reading + writing the column | (no schema change) |
| 2 | (no code change) | drops the column |

### Adding a NOT NULL constraint (three releases)

| Release | Schema |
|---|---|
| 1 | add column nullable + default |
| 2 | backfill all rows + verify zero NULLs |
| 3 | `ALTER COLUMN ... SET NOT NULL` |

### Adding an index on a large table

Always `CREATE INDEX CONCURRENTLY`. Postgres holds a `SHARE UPDATE
EXCLUSIVE` only briefly with this option.

```sql
CREATE INDEX CONCURRENTLY idx_playback_sessions_account_started
  ON playback_sessions (account_id, started_at DESC);
```

## Standard deploy

```bash
# On the deploy host (the same host that runs the services).
cd /opt/premium-player

# 1. Fetch the new tag
git fetch --tags
git checkout v0.<n>

# 2. Backend deps + build (does NOT touch running services)
cd services/api && npm ci && npm run build
cd ../billing-worker && npm ci && npm run build
cd ../epg-worker     && npm ci && npm run build

# 3. Migrate (additive only)
cd /opt/premium-player/services/api
npx prisma migrate deploy

# 4. Drift check before flipping over
cd /opt/premium-player
./scripts/check-drift.sh

# 5. Reload services
systemctl restart premium-player-api
systemctl restart premium-player-billing-worker
systemctl restart premium-player-epg-worker

# 6. Verify
curl -fsS https://api.premiumtvplayer.example/health | jq
journalctl -u premium-player-api -n 50 --no-pager
```

The API serves on `127.0.0.1:3000`; Caddy buffers in-flight requests
during the ~1-second restart. Active long-poll/heartbeat connections may
be dropped — Android clients retry transparently.

## Android TV (Play Console)

The app uses Google Play's staged rollout. Default plan:

| Day | % of users | Action |
|---|---|---|
| 0 | 1% | upload signed AAB, set rollout 1% |
| +2 | 10% | if crash-free rate &gt; 99.5% from the 1% cohort |
| +5 | 50% | if crash-free rate &gt; 99.5% and ANR &lt; 0.2% |
| +7 | 100% | full release |

If any cohort regresses, halt the rollout in the Play Console (does not
roll back installed users — only stops the install funnel).

## Rollback decision tree

```
Did the deploy break the API?
├── yes → was it caught within 5 minutes?
│         ├── yes → roll code back to previous tag (schema is forward-
│         │        compatible, so this is safe). Restart services.
│         └── no  → forward-fix. New users have already created data
│                   under the new schema; rolling back may orphan it.
└── no  → keep going.

Did the migration break the schema (failed mid-flight)?
├── yes → STOP. Do not start services on the broken schema.
│         Restore the latest hourly DB dump (see RESTORE.md scenario 1),
│         then write a corrected migration and redeploy.
└── no  → proceed.
```

## Worker shutdown safety

Both workers honour `SIGTERM` via NestJS `OnApplicationShutdown`. They:

1. Finish the in-flight batch (max ~50 rows for billing, max ~1 source
   for EPG)
2. Close the Prisma client and Redis connection
3. Exit 0

systemd's default `TimeoutStopSec=90s` is enough. If a worker hangs past
that and gets SIGKILL'd, the next boot picks up un-acknowledged work
because both workers are idempotent on `(provider, purchase_token)` and
`(source_id, external_channel_id)` respectively.

## Forbidden upgrade moves

- `prisma migrate reset` on production. There is no second clause to
  this rule.
- `DROP TABLE` or `TRUNCATE` from a migration. If a table needs to go,
  it goes via two releases (stop using → drop) and only after a backup
  drill verifies it's gone in the staging restore.
- Force-pushing a tag (`git tag -f v0.x && git push -f`). Tags are
  immutable; if you need a fix, cut `v0.x.1`.
- Deploying without running `./scripts/check-drift.sh` first. This is
  a CI gate, not a suggestion.

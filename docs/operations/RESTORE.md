# Backup &amp; restore runbook

A backup that has never been restored is not a backup. This runbook
describes the backup cadence, the verification cadence, and three
restore scenarios from quickest to most painful.

## What we back up

| Target | Method | Cadence | Retention |
|---|---|---|---|
| Postgres `premium_player` database | `pg_dump --format=custom` | hourly | 24 hourly + 14 daily + 6 monthly |
| `/etc/premium-player/` env files | tar + sops/age encryption | on every change + weekly | 90 days |
| `/etc/caddy/Caddyfile` + TLS data dir | tar | weekly | 90 days |
| Postgres WAL (optional, for PITR) | `archive_command` to off-host | continuous | 7 days |

What we **don't** back up:

- `services/api/dist/`, `node_modules/`, build artifacts — reproducible
  from the git tag deployed
- Redis — it's a cache; nothing in it is canonical (sessions can be
  re-issued, rate-limit windows reset cleanly)
- EPG `epg_programs` window — the worker re-fetches on next tick

## Backup script

`/usr/local/bin/premium-player-backup`:

```bash
#!/usr/bin/env bash
set -euo pipefail
TS=$(date -u +%Y%m%dT%H%M%SZ)
DEST=/var/backups/premium-player
mkdir -p "$DEST/db" "$DEST/etc"

# 1. Postgres
PGPASSWORD=$(grep -oP '(?<=PGPASSWORD=).*' /etc/premium-player/api.env) \
  pg_dump --format=custom --no-owner --no-acl \
    --host=127.0.0.1 --username=premium_player_app premium_player \
    > "$DEST/db/$TS.dump"

# 2. Env files (encrypted)
tar czf - -C / etc/premium-player \
  | age -r "$(cat /etc/premium-player-backup.recipient)" \
  > "$DEST/etc/$TS.tgz.age"

# 3. Caddy
tar czf "$DEST/etc/caddy-$TS.tgz" \
  -C / etc/caddy var/lib/caddy

# 4. Prune
find "$DEST/db"  -name "*.dump"      -mtime +14 -delete
find "$DEST/etc" -name "*.tgz*"      -mtime +90 -delete

# 5. Off-host (rclone or rsync to a separate machine / S3 / B2)
rclone copy "$DEST" remote:premium-player-backups
```

Wired via systemd timer:

```ini
# /etc/systemd/system/premium-player-backup.timer
[Timer]
OnCalendar=hourly
Persistent=true
```

## Verification — monthly drill

A backup that hasn't been restored in 30 days is treated as broken until
proven otherwise.

```bash
# 1. Pull the most recent dump
LATEST=$(ls -1t /var/backups/premium-player/db/*.dump | head -1)

# 2. Restore into a throwaway DB
createdb -h 127.0.0.1 premium_player_drill
pg_restore -h 127.0.0.1 -d premium_player_drill --no-owner --no-acl "$LATEST"

# 3. Smoke check
psql -h 127.0.0.1 -d premium_player_drill -c "
  SELECT count(*) FROM accounts;
  SELECT count(*) FROM entitlements WHERE state = 'lifetime_family';
  SELECT count(*) FROM sources;
"

# 4. Drop
dropdb -h 127.0.0.1 premium_player_drill
```

If row counts look wildly different from production, escalate before the
next backup overwrites a potentially-corrupt dump.

---

## Scenario 1 — Database corruption (rows wrong, schema fine)

**Symptom**: app behaving strangely, tests on production data fail
unexpectedly, an admin nuked something with a typo in a `WHERE`.

```bash
# 1. Stop writers
systemctl stop premium-player-api premium-player-billing-worker premium-player-epg-worker

# 2. Snapshot current state (so we can forensically compare)
pg_dump --format=custom premium_player > /tmp/before-restore.dump

# 3. Restore latest hourly into a parallel DB
LATEST=$(ls -1t /var/backups/premium-player/db/*.dump | head -1)
createdb premium_player_restored
pg_restore -d premium_player_restored --no-owner --no-acl "$LATEST"

# 4. Sanity check
psql -d premium_player_restored -c "SELECT count(*) FROM accounts;"

# 5. Swap
psql -c "ALTER DATABASE premium_player RENAME TO premium_player_broken;"
psql -c "ALTER DATABASE premium_player_restored RENAME TO premium_player;"

# 6. Restart
systemctl start premium-player-api premium-player-billing-worker premium-player-epg-worker

# 7. After 24h of healthy operation, drop premium_player_broken
```

Downtime: ~3-5 minutes. Data loss: anything written since the latest
hourly dump (so up to 1h, less if PITR is wired).

---

## Scenario 2 — Total host loss

**Symptom**: the box is gone. New cloud VM, fresh Debian, nothing else.

```bash
# 1. Bootstrap the new host per docs/operations/HARDENING.md
#    (user, ufw, postgres, redis, caddy, systemd units)

# 2. Pull backups from off-host
rclone copy remote:premium-player-backups /var/backups/premium-player

# 3. Restore env files
LATEST_ETC=$(ls -1t /var/backups/premium-player/etc/*.tgz.age | head -1)
age -d -i ~/.age/key.txt "$LATEST_ETC" | tar xzf - -C /

# 4. Restore Postgres
LATEST_DB=$(ls -1t /var/backups/premium-player/db/*.dump | head -1)
createdb premium_player
pg_restore -d premium_player --no-owner --no-acl "$LATEST_DB"

# 5. Restore Caddy + TLS data
LATEST_CADDY=$(ls -1t /var/backups/premium-player/etc/caddy-*.tgz | head -1)
tar xzf "$LATEST_CADDY" -C /
systemctl restart caddy

# 6. Deploy code (from a known-good git tag)
cd /opt/premium-player
git checkout v0.<latest>
cd services/api && npm ci && npm run build && npx prisma migrate deploy
cd ../billing-worker && npm ci && npm run build
cd ../epg-worker     && npm ci && npm run build

# 7. Bring services up
systemctl daemon-reload
systemctl enable --now premium-player-api premium-player-billing-worker premium-player-epg-worker

# 8. DNS — point A record at the new host's IP
# 9. Verify
curl -I https://api.premiumtvplayer.example/health
```

Downtime: 30-90 minutes if you have the runbook open and TLS cached
in Caddy's data dir restores cleanly. Up to 2-3 hours if Let's Encrypt
needs to re-issue.

---

## Scenario 3 — `SOURCE_ENCRYPTION_KEY` compromise

This is **not** a restore — it's a re-encryption. See
[`SECRETS.md#rotation-procedures`](./SECRETS.md#rotation-procedures).

The DB stays online throughout. The only "restore"-shaped step is
verifying the re-encryption SQL count after the job:

```sql
SELECT
  (SELECT count(*) FROM sources WHERE substring(encrypted_url FROM 1 FOR 1) = E'\\x01') AS new_format,
  (SELECT count(*) FROM sources WHERE substring(encrypted_url FROM 1 FOR 1) = E'\\x00') AS old_format;
-- old_format must be 0 before you remove SOURCE_ENCRYPTION_KEY_PREV
```

---

## What "tested" means

For each scenario, the runbook is "tested" only if you have actually
performed the restore on a staging host within the last 90 days and the
result equalled production state at the moment of dump. If you have not,
write `**UNTESTED — last drill: <date>**` next to the scenario heading
above so the next person knows.

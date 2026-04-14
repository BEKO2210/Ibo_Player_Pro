# Incident runbooks

One runbook per failure mode. Each runbook follows the same shape:
**symptom → diagnose → mitigate → root cause**.

These exist so the on-call engineer can act in 90 seconds without paging
the architect. If a runbook isn't actionable in that window, it's wrong —
fix the runbook.

---

## R1 — API returns 500 / unreachable

**Symptom**: clients report "Service unavailable" banner; `curl /health`
hangs or returns 5xx.

**Diagnose**

```bash
systemctl status premium-player-api
journalctl -u premium-player-api -n 100 --no-pager
ss -tlnp | grep 3000          # is the port even bound?
```

**Mitigate**

- If the service is down: `systemctl restart premium-player-api`. Wait
  ~3 seconds, retry health.
- If the service is up but Caddy returns 502: `systemctl restart caddy`.
- If both are up but health returns 503 from the API itself, jump to
  R2 (Postgres) or R3 (Redis) based on which subsystem the health JSON
  shows as down.

**Root cause** is whatever the journalctl tail says. Common: OOM kill
(see R7), uncaught Prisma error (see R2), Firebase Admin init failure
(see R5).

---

## R2 — Postgres unreachable from the API

**Symptom**: `/health` returns `database: down`. API requests that touch
the DB return `500` or `INTERNAL_ERROR`.

**Diagnose**

```bash
systemctl status postgresql
journalctl -u postgresql -n 50 --no-pager
sudo -u postgres psql -c "SELECT 1;"
```

**Mitigate**

- DB process down: `systemctl restart postgresql`.
- DB up but the API can't connect: check `DATABASE_URL` in
  `/etc/premium-player/api.env` — password rotation may have desync'd.
- Connection limit reached:
  ```sql
  SELECT count(*) FROM pg_stat_activity;
  SHOW max_connections;
  ```
  If at the cap, kill idle sessions:
  ```sql
  SELECT pg_terminate_backend(pid) FROM pg_stat_activity
   WHERE state = 'idle' AND state_change < now() - interval '10 min';
  ```

**Recovery without restart**: the API uses Prisma's connection pool,
which reconnects on its own once Postgres comes back. No manual restart
of the API is needed in &gt;90% of cases.

---

## R3 — Redis unreachable

**Symptom**: `/health` returns `redis: down`. Auth still works (Firebase
verifies are stateless), but rate limiting is degraded.

**Diagnose**

```bash
systemctl status redis-server
redis-cli -a "$REDIS_PASSWORD" ping
```

**Mitigate**

- `systemctl restart redis-server` is almost always enough.
- The API treats Redis as best-effort: a Redis outage **does not** take
  the API down. It does silently disable rate limiting until Redis is
  back. Investigate quickly.

---

## R4 — Firebase outage / token verification failing

**Symptom**: every `/auth/*` call returns 401 `UNAUTHORIZED` even with
fresh tokens. New users can't sign in.

**Diagnose**

```bash
curl -fsS https://status.firebase.google.com/incidents.json | jq '.[0]'
journalctl -u premium-player-api -n 100 | grep -iE 'firebase|jwks'
```

**Mitigate**

- If Firebase itself is down (status page confirms): there is nothing
  to do server-side. Post a banner. Existing logged-in users with
  cached tokens continue to work for up to ~1h.
- If only your service is failing: check
  `FIREBASE_SERVICE_ACCOUNT_JSON` in `/etc/premium-player/api.env`. A
  recent rotation may have left the API on the old key.
- If JWKS lookup is slow but eventually succeeds, it's a transient
  Google network issue; nothing to mitigate.

---

## R5 — Billing replay storm

**Symptom**: `billing-worker` log shows the same purchase token being
processed thousands of times per minute.

**Diagnose**

```bash
journalctl -u premium-player-billing-worker -n 200 --no-pager
psql -d premium_player -c "
  SELECT purchase_token, count(*) FROM purchases
   WHERE created_at > now() - interval '1 hour'
   GROUP BY purchase_token HAVING count(*) > 1;
"
```

**Mitigate**

- The worker is **idempotent** on `(provider, purchase_token)`.
  Replays do not double-grant entitlements. They only waste CPU.
- If volume is interrupting other work, pause the worker:
  `systemctl stop premium-player-billing-worker`. Investigate.
- Most likely cause: a Play Real-Time Developer Notification webhook
  retry loop, or a forwarder sending the same event many times. Check
  the source.

**Verify no double-grant happened**:

```sql
SELECT a.id, count(*) AS active_purchases
  FROM purchases p JOIN accounts a ON a.id = p.account_id
 WHERE p.refunded_at IS NULL
 GROUP BY a.id HAVING count(*) > 2;
-- expected: zero rows for "Single", at most 1 row for "Family upgraders"
```

---

## R6 — EPG worker stuck

**Symptom**: `epg_programs.last_validated_at` for active sources is
hours behind. EPG screen shows yesterday's lineup.

**Diagnose**

```bash
systemctl status premium-player-epg-worker
journalctl -u premium-player-epg-worker -n 100 --no-pager
psql -d premium_player -c "
  SELECT id, kind, last_validated_at, validation_status
    FROM sources WHERE is_active = true
   ORDER BY last_validated_at NULLS FIRST LIMIT 10;
"
```

**Mitigate**

- Restart: `systemctl restart premium-player-epg-worker`.
- If a single source keeps failing (`validation_status='invalid'`), it
  has a per-source isolation; the worker continues with the others.
  Disable the bad source via the admin tooling or directly:
  ```sql
  UPDATE sources SET is_active = false WHERE id = '<uuid>';
  ```
- If all sources are stuck, the worker itself is wedged. Kill,
  re-start, look at the first error in journalctl after the restart.

---

## R7 — Out of memory / OOM kills

**Symptom**: a service silently restarts; `dmesg` shows
`Out of memory: Killed process ... node`.

**Diagnose**

```bash
dmesg -T | grep -i 'killed process'
free -h
systemctl status premium-player-api premium-player-billing-worker premium-player-epg-worker
```

**Mitigate**

- Identify which service was killed. Add a memory cap that triggers a
  controlled restart **before** OOM-killer fires:
  ```ini
  # /etc/systemd/system/premium-player-api.service.d/limits.conf
  [Service]
  MemoryHigh=512M
  MemoryMax=768M
  ```
  Then `systemctl daemon-reload && systemctl restart premium-player-api`.
- If the host as a whole is exhausted, add swap (a 1-2 GB swapfile is
  cheap insurance), or scale up.

**Root cause hunting**: an OOM in the API is almost always a leak in
hot streaming code (large body parsing without limits, unbounded
in-memory cache). An OOM in the EPG worker usually means an unusually
large XMLTV file. Both want a stack trace from a heap snapshot.

---

## R8 — Disk full

**Symptom**: writes start failing; `df -h` shows 100%.

**Diagnose**

```bash
df -h
du -shx /var/log /var/lib/postgresql /var/backups /var/lib/caddy 2>/dev/null | sort -h
```

**Mitigate**

- Postgres bloat: run `VACUUM (ANALYZE);` on the largest tables. If
  `epg_programs` is dominant, run the dedupe job (see Parking Lot in
  CLAUDE.md).
- Logs: rotate or truncate. `journalctl --vacuum-size=200M` shrinks
  systemd journal aggressively.
- Backups: prune old. The default retention is 14 daily, but if disk
  filled because of weeks of missed prune cycles, run the retention
  step from `RESTORE.md` manually.

---

## R9 — Source decryption suddenly failing

**Symptom**: `epg-worker` logs `auth tag mismatch` or
`InvalidEncryptedDataException` for sources that worked yesterday.

**Diagnose**

```bash
journalctl -u premium-player-epg-worker | grep -i 'crypto\|auth tag' | tail -20
```

**Mitigate**

- 99% of the time: someone rotated `SOURCE_ENCRYPTION_KEY` without
  following the rotation procedure (see
  [`SECRETS.md`](./SECRETS.md#rotation-procedures)).
- Restore the previous key into `SOURCE_ENCRYPTION_KEY_PREV`,
  redeploy, then run the proper rotation.
- If the previous key is genuinely lost: source rows that were
  encrypted under it are unrecoverable. Affected users must re-enter
  their M3U/XMLTV credentials. Audit which sources fall into this
  bucket:
  ```sql
  SELECT id, account_id, profile_id, kind FROM sources
   WHERE substring(encrypted_url FROM 1 FOR 1) = E'\\x00';
  ```

---

## R10 — TLS certificate expiring / expired

**Symptom**: clients see TLS errors; browser shows expired cert.

**Diagnose**

```bash
echo | openssl s_client -servername api.premiumtvplayer.example \
        -connect api.premiumtvplayer.example:443 2>/dev/null \
  | openssl x509 -noout -dates
journalctl -u caddy -n 100 --no-pager | grep -i 'cert\|acme'
```

**Mitigate**

- Caddy auto-renews. If it failed, the most common cause is firewall
  blocking outbound to Let's Encrypt's ACME endpoints. Verify
  `curl https://acme-v02.api.letsencrypt.org/directory` works from
  the host.
- Force a renewal: `caddy reload --config /etc/caddy/Caddyfile`. If
  that doesn't trigger, `systemctl restart caddy`.
- Last resort: `caddy untrust` then restart to fully re-issue.

---

## When a runbook doesn't exist

If you handled an incident that isn't covered above:

1. Write it up here using the same shape (symptom → diagnose →
   mitigate → root cause). Even three lines of "what worked" is
   better than nothing.
2. Cross-link from any related operations doc (e.g. a recurring
   billing oddity belongs in both R5 and a "known issues" section of
   `services/billing-worker/README.md`).
3. Commit it the same day. Memory of the incident is the most accurate
   it will ever be.

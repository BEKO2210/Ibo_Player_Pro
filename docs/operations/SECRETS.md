# Secrets — generation, storage, rotation

This document describes every secret the production system holds, how to
generate it, where it lives, and **how to rotate it without taking the
service down**. Treat it as the only acceptable source of truth on the topic.

## Inventory

| Secret | Where it's used | Sensitivity |
|---|---|---|
| `SOURCE_ENCRYPTION_KEY` | `services/api` &middot; AES-256-GCM key for source credentials at rest | **critical** — compromise re-exposes every stored M3U/XMLTV credential |
| `FIREBASE_SERVICE_ACCOUNT_JSON` (or trio) | `services/api` &middot; verifies Firebase ID tokens | **high** — compromise lets an attacker forge any user identity |
| `DATABASE_URL` (with password) | `services/api`, both workers | **high** — direct DB access |
| `REDIS_URL` (if password set) | `services/api`, workers | medium |
| `BILLING_PLAY_SERVICE_ACCOUNT_JSON` | `services/billing-worker` &middot; calls Play `androidpublisher` | **high** — refund/acknowledge on your behalf |
| `FIREBASE_API_KEY` (Android `local.properties`) | client app | low — public-by-design but restrict via Firebase console (Android app SHA + package name) |

## Generation

```bash
# SOURCE_ENCRYPTION_KEY: 32 random bytes, hex-encoded
openssl rand -hex 32

# A long random Postgres password
openssl rand -base64 48 | tr -d '/+=' | head -c 40

# A long random Redis password
openssl rand -base64 32 | tr -d '/+=' | head -c 32
```

## Storage on the production host

**Never** put secrets in a tracked file. The repo's `.env.example` is a
template only.

Recommended layout for a single-host systemd deployment:

```
/etc/premium-player/
├── api.env           # mode 600, owner premium-player:premium-player
├── billing-worker.env
└── epg-worker.env
```

Wire each systemd unit with `EnvironmentFile=`:

```ini
# /etc/systemd/system/premium-player-api.service
[Service]
EnvironmentFile=/etc/premium-player/api.env
User=premium-player
```

`chmod 600` on every env file. Verify with `find /etc/premium-player -type f
-not -perm 600` — output must be empty.

## Encryption at rest of the env files (optional, recommended)

For multi-admin hosts, encrypt the env files with [`sops`](https://github.com/getsops/sops)
or [`age`](https://github.com/FiloSottile/age) and decrypt at deploy time.
Decryption keys live on a separate hardware token (YubiKey) or in a managed
KMS — never alongside the encrypted files.

## Rotation procedures

### `SOURCE_ENCRYPTION_KEY` (the hard one)

The wire format `[version:u8][iv:12][tag:16][ct:...]` carries a version byte
specifically for rotation. The procedure:

1. Generate the new key. Keep the old key alongside it:
   ```
   SOURCE_ENCRYPTION_KEY=<new>
   SOURCE_ENCRYPTION_KEY_PREV=<old>
   ```
2. Bump the version byte in `SourceCryptoService` (`v0` → `v1`). Decryption
   logic dispatches on the version byte: `v0` → use `_PREV` key, `v1` →
   use the active key.
3. Deploy. New writes use `v1`; old reads transparently use the old key.
4. Run a one-shot re-encryption job:
   ```bash
   cd services/api
   npm run reencrypt-sources       # decrypts every v0 row, re-encrypts as v1
   ```
5. Verify zero `v0` rows remain:
   ```sql
   SELECT count(*) FROM sources WHERE substring(encrypted_url from 1 for 1) = E'\\x00';
   ```
6. Remove `SOURCE_ENCRYPTION_KEY_PREV` from env, restart.

The whole procedure is online; the API never goes down.

### Firebase service account

1. Generate a new service-account key in the Firebase console.
2. Drop the new JSON into `/etc/premium-player/api.env` as
   `FIREBASE_SERVICE_ACCOUNT_JSON='<json>'`.
3. `systemctl reload premium-player-api` (or restart if reload not wired).
4. Disable the old key in the Firebase console **after** the new key is
   serving traffic for &gt;15 minutes (giving the JWKS cache time to settle).

### Database password

1. `ALTER ROLE premium_player WITH PASSWORD '<new>';`
2. Update `DATABASE_URL` in all three env files (api, billing-worker,
   epg-worker).
3. Restart all three services.
4. Verify connections, then drop any lingering session of the old password
   (`pg_terminate_backend(pid)`).

## What to do on suspected compromise

| Suspected | Immediate action |
|---|---|
| `SOURCE_ENCRYPTION_KEY` leaked | Rotate (procedure above). Audit the source rows that existed before rotation — they were exposed under the old key. |
| Firebase service account leaked | Disable the old key in Firebase console first, then rotate. Force user re-auth: `auth.revokeRefreshTokens(uid)` for the affected users. |
| Database password leaked | Rotate. Run `pg_stat_activity` audit for unknown source IPs in the suspected window. |
| Play service account leaked | Disable in GCP IAM, generate a new one, redeploy `billing-worker`. Audit `androidpublisher` activity log in Google Cloud Console. |

## What we deliberately do NOT do

- We do not store secrets in `git-crypt`. It's good but it ties decryption
  to a single mechanism; we prefer host-side env files + optional `sops`.
- We do not bake secrets into Docker images. Containers receive them via
  `--env-file` or compose `env_file:` at runtime.
- We do not rotate secrets on a fixed cadence "for hygiene". We rotate on
  suspected compromise, on personnel change, and per the schedule for any
  externally-mandated key (Play, Firebase per their own policies).

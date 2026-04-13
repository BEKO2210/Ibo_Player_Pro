# Production hardening checklist

For a single Linux host running the API + the two workers + Postgres + Redis
behind a Caddy reverse proxy. Each item is binary: done or not done.

## Network surface

- [ ] **ufw enabled**, default deny inbound, default allow outbound
  ```bash
  ufw default deny incoming
  ufw default allow outgoing
  ufw allow 22/tcp
  ufw allow 80/tcp
  ufw allow 443/tcp
  ufw enable
  ```
- [ ] Postgres bound to `127.0.0.1` only (`listen_addresses = 'localhost'`
  in `postgresql.conf`) — never 0.0.0.0 in production
- [ ] Redis bound to `127.0.0.1` only (`bind 127.0.0.1` in `redis.conf`)
  and `requirepass <strong>` set
- [ ] API bound to `127.0.0.1:3000` — public access goes through Caddy
- [ ] No public exposure of `/health` if the host serves multiple
  domains — gate it behind a path in Caddy or limit by source IP

Verify externally:

```bash
nmap -p- <public-ip>     # only 22, 80, 443 should answer
```

## SSH

- [ ] `PermitRootLogin no` in `/etc/ssh/sshd_config`
- [ ] `PasswordAuthentication no` — keys only
- [ ] At least one non-root sudo user with a known public key
- [ ] **fail2ban** enabled with the `sshd` jail (default 10-min ban after
  5 failures is a fine starting point)
- [ ] Optional: move SSH off port 22 (mostly cosmetic, blocks log noise)

## TLS / Caddy

`/etc/caddy/Caddyfile` (example):

```caddy
api.premiumtvplayer.example {
    encode zstd gzip
    reverse_proxy 127.0.0.1:3000

    # Security headers — set these explicitly, don't trust defaults.
    header {
        Strict-Transport-Security "max-age=31536000; includeSubDomains; preload"
        X-Content-Type-Options    "nosniff"
        X-Frame-Options           "DENY"
        Referrer-Policy           "strict-origin-when-cross-origin"
        Permissions-Policy        "geolocation=(), camera=(), microphone=()"
        -Server
    }

    # Rate limit auth endpoints. Adjust to your traffic shape.
    @auth path /v1/auth/*
    rate_limit @auth {
        zone auth_zone {
            key {client.ip}
            events 30
            window 1m
        }
    }

    log {
        output file /var/log/caddy/api.log {
            roll_size 100mb
            roll_keep 14
        }
    }
}
```

Verify:

```bash
curl -I https://api.premiumtvplayer.example/health
# Strict-Transport-Security present, no Server header, valid TLS chain
```

## Postgres roles

- [ ] **Two roles minimum**: a superuser for migrations only, and an
  `app` role used by the API and workers
  ```sql
  CREATE ROLE premium_player_app LOGIN PASSWORD '<strong>';
  GRANT CONNECT ON DATABASE premium_player TO premium_player_app;
  GRANT USAGE  ON SCHEMA public          TO premium_player_app;
  GRANT SELECT, INSERT, UPDATE, DELETE
        ON ALL TABLES    IN SCHEMA public TO premium_player_app;
  GRANT USAGE  ON ALL SEQUENCES IN SCHEMA public TO premium_player_app;
  ```
- [ ] `DATABASE_URL` in production uses `premium_player_app`, not the
  superuser
- [ ] Migrations run with the superuser via a one-shot deploy command,
  never with the app role at runtime
- [ ] `pg_hba.conf` requires `scram-sha-256` for all entries

## Application user + filesystem

- [ ] Dedicated unprivileged user `premium-player`:
  ```bash
  useradd -r -s /usr/sbin/nologin -d /opt/premium-player premium-player
  ```
- [ ] `/opt/premium-player` owned by `premium-player:premium-player`,
  mode `750`
- [ ] `/etc/premium-player/*.env` mode `600`, owned by
  `premium-player:premium-player`
- [ ] systemd unit runs as `User=premium-player`, `Group=premium-player`,
  with a tight sandbox:
  ```ini
  [Service]
  NoNewPrivileges=true
  ProtectSystem=strict
  ProtectHome=true
  ReadWritePaths=/var/log/premium-player
  PrivateTmp=true
  PrivateDevices=true
  ```

## Firebase API key (Android client)

The Android `FIREBASE_API_KEY` is in `local.properties` and shipped inside
the APK. It is technically extractable, but it is **not** a server secret —
its safety comes from the restrictions you set in the Firebase / Google
Cloud Console:

- [ ] In Google Cloud Console &rarr; Credentials &rarr; the Android API key
- [ ] Application restriction: **Android apps**, allowed package
  `com.premiumtvplayer.app`, with the SHA-1 of the release keystore
- [ ] API restriction: only the Firebase APIs the client actually uses
  (Identity Toolkit, Token, Installations)

## Logging + audit

- [ ] All three services log to stdout, journald collects them
  (`journalctl -u premium-player-api`)
- [ ] `/var/log/caddy/` rotated daily, retained 14 days
- [ ] Postgres `log_min_duration_statement = 250ms` to catch slow queries
- [ ] Postgres `log_connections = on` and `log_disconnections = on` for
  forensic trail
- [ ] Daily logwatch / journald summary emailed to the on-call address

## Updates

- [ ] `unattended-upgrades` configured for security updates (Debian/Ubuntu)
- [ ] Reboot scheduled weekly during a low-traffic window if an update
  requires it (`unattended-upgrade`'s `Automatic-Reboot`)

## Verification — run quarterly

```bash
# inbound surface
nmap -p- <public-ip>

# TLS grade (target A or A+)
curl -sI https://<host>/ | grep -i strict-transport
testssl.sh https://<host>/

# secrets perms
find /etc/premium-player -type f -not -perm 600    # must be empty

# no env in any tracked file
git ls-files | xargs grep -lE '(SECRET|PASSWORD|KEY).*=' || true
```

If any of those produce unexpected output, treat it as an incident.

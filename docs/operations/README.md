# Operations

The five docs in this folder cover everything you need to run **Premium TV
Player** in production on a single Linux host (or scale out from there).
They're written so the on-call engineer can act in 90 seconds without
paging the architect.

| Doc | Read it when |
|---|---|
| [`SECRETS.md`](./SECRETS.md) | adding a new secret · rotating an existing one · suspected compromise |
| [`HARDENING.md`](./HARDENING.md) | bringing up a new production host · quarterly security review |
| [`RESTORE.md`](./RESTORE.md) | DB corruption · total host loss · monthly backup drill |
| [`UPGRADE.md`](./UPGRADE.md) | every deploy · every schema migration · every Play Console rollout |
| [`RUNBOOKS.md`](./RUNBOOKS.md) | something just broke (10 indexed runbooks, R1–R10) |

## Pre-flight before any production change

```bash
./scripts/check-drift.sh        # 8 invariants, exits non-zero on drift
cd services/api && npm test     # backend + workers + parsers (143 tests)
```

If either fails, the change does not go to production. No exceptions, no
"I'll fix the failing test in the next PR".

## Architecture in one sentence

One Linux host runs three systemd services (`premium-player-api`,
`premium-player-billing-worker`, `premium-player-epg-worker`) behind a
Caddy reverse proxy on `:443`, talking to a local Postgres 16 + Redis 7,
with secrets in `/etc/premium-player/*.env` (mode 600) and backups
to off-host storage.

The full topology, port list, and systemd unit shapes live across the
five docs above.

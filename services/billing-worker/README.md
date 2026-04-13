# Premium TV Player — Billing Worker (V1)

Reconciliation worker for Google Play billing. Runs alongside the NestJS
API and is the sole writer for billing-driven entitlement transitions per
`docs/architecture/entitlement-state-machine.md`.

## How it works

- Boots a headless Nest application context (`createApplicationContext` —
  no HTTP listener).
- Imports the API's `@api/billing` module via TS path alias so worker and
  API use **one** `BillingService` implementation. This means the work
  surface (`POST /v1/billing/verify`, restore, and worker reconciliation)
  all go through `applyVerified()` — they cannot diverge.
- Polls the `purchases` table on a configurable interval for rows that
  need re-verification:
  - `acknowledged_at IS NULL` + `purchase_state = 'purchased'`
    (ack failed earlier — retry within Google's 3-day grace window).
  - `purchase_state = 'pending'` (not yet finalized by Google).
- For each row, calls `BillingService.reverify(purchase)` which:
  1. verifies with Google Play via the shared `ProviderVerificationClient`
  2. upserts the `purchases` row (idempotent on provider + purchase_token)
  3. `SELECT ... FOR UPDATE` on the entitlement row (single-writer lock)
  4. applies the right `EntitlementEvent` via the pure state machine
  5. acknowledges with Google if we haven't yet
- Errors on individual rows don't abort the batch.

## Prerequisites

- The API's `node_modules/` (the worker shares dependencies via the TS
  path alias — no separate install needed for local dev).
- `DATABASE_URL`, `REDIS_URL`, Firebase credentials, and
  `BILLING_ANDROID_PACKAGE_NAME` set — same `.env` as the API.

## Commands

From `services/billing-worker/`:

```bash
# dev: hot-reload on source changes
npm run start:dev

# build to dist/
npm run build

# run compiled, once (batch reconciliation, then exit) — useful for CI
WORKER_RUN_ONCE=true npm start

# run compiled, forever (poll loop)
npm start
```

## Env reference

| Key | Default | Purpose |
|---|---|---|
| `BILLING_WORKER_POLL_INTERVAL_MS` | `15000` | Gap between polls |
| `WORKER_RUN_ONCE` | *(unset)* | Set to `true` to exit after one tick |
| (all other API envs) | — | See `services/api/README.md` |

## Test

```bash
npm test
```

Unit tests mock `BillingService.findWorkBatch` + `reverify` and assert
the worker tick behavior (empty batch, full batch, per-purchase failure
isolation, shutdown). Integration with the real service is covered by
`services/api`'s own billing tests.

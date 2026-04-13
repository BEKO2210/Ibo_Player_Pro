# Data Model — Part 2: Commerce & Sources

> **Scope:** Entitlements, purchases, sources, source credentials.
> **Other parts:** [Part 1 — Identity](./data-model-part-1-identity.md) · [Part 3 — EPG & Activity](./data-model-part-3-epg-activity.md) · [Index + global ER diagram](./data-model.md)

---

## 1. Narrative

### Entitlement

Every account has **exactly one `entitlements` row** (1:1, account-scoped). It is the server-authoritative answer to the question: _"What can this user do right now?"_ It carries one of the six states from the locked product decisions:

```
none → trial → expired
             ↘ lifetime_single
             ↘ lifetime_family
             ↘ revoked   (from any state)
```

- `max_profiles` / `max_devices` on the row are the **caps** enforced at the application layer (1/1 for Single, 5/5 for Family).
- When a `purchases` row flips to `verified`, the billing worker updates the entitlement (`state`, `lifetime_activated_at`, caps, `source_purchase_id`).
- Refund / chargeback → entitlement → `revoked` (with `revoked_reason`).

State transitions live in `docs/architecture/entitlement-state-machine.md` (Run 5). This schema just has to **represent** them.

### Purchases

`purchases` is an append-mostly ledger — one row per Play Billing order (or Apple IAP / Stripe later). The `raw_payload` jsonb keeps the verification receipt for later audit. `purchase_token` is globally unique per rail, which prevents replays.

### Sources

Per locked decision: **the app ships empty.** Users add their own M3U / M3U8 / XMLTV URLs. Those URLs are personal data + potentially sensitive, so:

- The URL itself is stored **encrypted at rest** (AES-256-GCM, per-row nonce, KMS-managed key id).
- HTTP Basic credentials (if present) live in a **separate `source_credentials` table** keyed 1:1 with the source — so most reads (listing sources, refreshing EPG) never decrypt the password.

Sources are **always scoped to an account**, never to a device or profile. A profile that is allowed by parental rules sees all account-level sources.

### Entities in this part

| Table | Purpose |
|---|---|
| `entitlements` | 1:1 with account. Source of truth for trial/lifetime/revoked state and caps. |
| `purchases` | Immutable-ish ledger of Play Billing / IAP / Stripe transactions. |
| `sources` | User-provided M3U / M3U8 / XMLTV feed URLs (encrypted). |
| `source_credentials` | Encrypted HTTP-basic username/password for a source (1:1 with source). |

---

## 2. SQL DDL (PostgreSQL)

```sql
-- ──────────────────────────────────────────────────────────────
-- entitlement state enum
-- ──────────────────────────────────────────────────────────────
create type entitlement_state as enum (
  'none',
  'trial',
  'lifetime_single',
  'lifetime_family',
  'expired',
  'revoked'
);

-- ──────────────────────────────────────────────────────────────
-- entitlements   (1:1 with accounts)
-- ──────────────────────────────────────────────────────────────
create table entitlements (
  id                      uuid primary key default gen_random_uuid(),
  account_id              uuid not null unique references accounts(id) on delete cascade,
  state                   entitlement_state not null default 'none',

  trial_started_at        timestamptz,
  trial_expires_at        timestamptz,
  lifetime_activated_at   timestamptz,

  max_profiles            smallint not null default 1,    -- Single=1, Family=5
  max_devices             smallint not null default 1,    -- Single=1, Family=5

  source_purchase_id      uuid,                           -- FK added below
  revoked_reason          text,

  updated_at              timestamptz not null default now()
);

create index entitlements_state_idx         on entitlements (state);
create index entitlements_trial_expires_idx on entitlements (trial_expires_at) where state = 'trial';

-- ──────────────────────────────────────────────────────────────
-- purchases   (ledger)
-- ──────────────────────────────────────────────────────────────
create table purchases (
  id                 uuid primary key default gen_random_uuid(),
  account_id         uuid        not null references accounts(id) on delete cascade,

  rail               text        not null default 'google_play'
                       check (rail in ('google_play','apple_iap','stripe')),
  product_id         text        not null,              -- e.g. 'lifetime_single', 'lifetime_family'
  purchase_token     text        not null,              -- Play Billing token / IAP receipt / Stripe pi
  order_id           text,

  state              text        not null
                       check (state in ('pending','verified','acknowledged','refunded','revoked','failed')),
  amount_cents       integer,
  currency           char(3),
  country_code       char(2),

  verified_at        timestamptz,
  acknowledged_at    timestamptz,
  refunded_at        timestamptz,

  raw_payload        jsonb,                             -- full verification receipt for audit

  created_at         timestamptz not null default now(),
  updated_at         timestamptz not null default now()
);

create unique index purchases_token_uniq on purchases (rail, purchase_token);
create index        purchases_account_idx on purchases (account_id, created_at desc);
create index        purchases_state_idx   on purchases (state);

-- Entitlement → purchase backref (nullable; set once a lifetime activates)
alter table entitlements
  add constraint entitlements_source_purchase_fk
  foreign key (source_purchase_id) references purchases(id) on delete set null;

-- ──────────────────────────────────────────────────────────────
-- source kind enum
-- ──────────────────────────────────────────────────────────────
create type source_kind as enum ('m3u','m3u8','xmltv');

-- ──────────────────────────────────────────────────────────────
-- sources
-- ──────────────────────────────────────────────────────────────
create table sources (
  id                        uuid primary key default gen_random_uuid(),
  account_id                uuid not null references accounts(id) on delete cascade,

  label                     text not null,              -- user-editable friendly name
  kind                      source_kind not null,

  -- Encrypted URL (AES-256-GCM). The plaintext URL never touches disk.
  url_ciphertext            bytea not null,
  url_nonce                 bytea not null,
  url_key_id                text  not null,             -- KMS key identifier

  user_agent                text,
  refresh_interval_seconds  integer not null default 21600,    -- 6h default
  last_fetched_at           timestamptz,
  last_fetch_status         text,                              -- 'ok' | 'http_4xx' | 'http_5xx' | 'timeout' | ...
  last_fetch_error          text,

  created_at                timestamptz not null default now(),
  updated_at                timestamptz not null default now(),
  deleted_at                timestamptz
);

create index sources_account_idx  on sources (account_id)  where deleted_at is null;
create index sources_refresh_idx  on sources (last_fetched_at) where deleted_at is null;

-- ──────────────────────────────────────────────────────────────
-- source_credentials   (1:1 with source, encrypted)
-- ──────────────────────────────────────────────────────────────
create table source_credentials (
  source_id            uuid primary key references sources(id) on delete cascade,

  username_ciphertext  bytea,
  username_nonce       bytea,
  password_ciphertext  bytea,
  password_nonce       bytea,
  key_id               text not null,

  updated_at           timestamptz not null default now()
);
```

---

## 3. Access paths (why these indexes)

| Query | Index used |
|---|---|
| "What's my entitlement right now?" (every app start) | PK on `account_id` (unique) |
| Nightly trial-expiry sweeper | `entitlements_trial_expires_idx` (partial) |
| Dedupe Play Billing replay | `purchases_token_uniq` |
| Account billing history screen | `purchases_account_idx` |
| Billing worker "find pending verifications" | `purchases_state_idx` |
| List all sources for account | `sources_account_idx` |
| EPG worker "which sources are due for refresh" | `sources_refresh_idx` |

---

## 4. Flow mapping (from `docs/product/user-flows.md`)

| User flow | Tables touched |
|---|---|
| Trial activation | `entitlements` (insert/update → `trial`) |
| Purchase (Lifetime Single/Family) | `purchases` (insert), `entitlements` (update → `lifetime_*`, bump caps) |
| Restore purchase | `purchases` (lookup by token), `entitlements` (update) |
| Refund / chargeback | `purchases` (→ `refunded`), `entitlements` (→ `revoked`) |
| Expired / revoked handling | `entitlements.state` read on every privileged call |
| Add source | `sources` (insert), optional `source_credentials` (insert) |
| Edit source | `sources`, `source_credentials` |
| Delete source | `sources` (soft delete; cascades via FKs handle EPG in Part 3) |

Identity tables live in Part 1; EPG/activity/audit tables in Part 3.

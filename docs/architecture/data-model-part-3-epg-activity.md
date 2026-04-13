# Data Model — Part 3: EPG & Activity

> **Scope:** EPG cache, viewing activity, playback sessions, audit log. Plus cross-cutting concerns (encryption, PIN hashing, soft-delete, timestamps, UUIDs).
> **Other parts:** [Part 1 — Identity](./data-model-part-1-identity.md) · [Part 2 — Commerce & Sources](./data-model-part-2-commerce-sources.md) · [Index + global ER diagram](./data-model.md)

---

## 1. Narrative

### EPG cache

`epg_channels` and `epg_programs` are the **server-side cache** of XMLTV data fetched by the `epg-worker` from the user's own sources. We cache server-side so that (a) every device gets a fast, consistent EPG grid and (b) we can serve EPG without the client re-parsing multi-megabyte XMLTVs.

- `epg_channels` is keyed by `(source_id, external_id)` — the XMLTV `tvg-id` is only unique **within** a source.
- `epg_programs` is a time-indexed program grid: the dominant query is "give me programs for channel X between T and T+Δ".
- Stream URLs on channels are encrypted at rest (same pattern as `sources` in Part 2).

### Activity

Three user-facing features live here:

- **`watch_history`** — append-only. Every finished (or abandoned) viewing session. Fuel for "Recently watched" and analytics.
- **`continue_watching`** — de-duplicated by `(profile_id, asset_ref)`. At most one row per asset per profile, updated on every heartbeat. This is the Home-row "Continue watching".
- **`favorites`** — user-pinned rail on Home.

All three are **profile-scoped** (not account-scoped) because the product promises personal rows per profile.

### Playback sessions

`playback_sessions` is the live view: a row exists while a stream is actually playing. It is the input to:

- Concurrent-stream enforcement (cap per plan).
- Heartbeat sync (position, bitrate).
- Device "currently watching" indicator.

Rows are closed by setting `ended_at`; partial-index `playback_sessions_active_idx` makes "what's live right now on this device" a cheap lookup.

### Audit log

`audit_log` is the **append-only compliance trail**: PIN changes, device revocations, entitlement transitions, billing events, admin actions. Uses `bigserial` (not UUID) because it's write-heavy, ordered, and we never need to generate IDs offline.

### Entities in this part

| Table | Purpose |
|---|---|
| `epg_channels` | Server-cached channel list per source (with encrypted stream URL) |
| `epg_programs` | Server-cached program grid, time-indexed |
| `watch_history` | Append-only viewing log per profile |
| `continue_watching` | Latest resume position per (profile, asset) |
| `favorites` | Pinned items per profile |
| `playback_sessions` | Live in-flight playback, heartbeat-updated |
| `audit_log` | Append-only compliance trail |

---

## 2. SQL DDL (PostgreSQL)

```sql
-- ──────────────────────────────────────────────────────────────
-- epg_channels
-- ──────────────────────────────────────────────────────────────
create table epg_channels (
  id                    uuid primary key default gen_random_uuid(),
  source_id             uuid not null references sources(id) on delete cascade,

  external_id           text not null,     -- tvg-id from XMLTV
  display_name          text not null,
  icon_url              text,
  language              text,
  category              text,

  -- Encrypted stream URL (live channel). Same crypto pattern as sources.
  stream_url_ciphertext bytea,
  stream_url_nonce      bytea,
  stream_key_id         text,

  position              integer,
  created_at            timestamptz not null default now(),
  updated_at            timestamptz not null default now()
);

create unique index epg_channels_source_ext_uniq on epg_channels (source_id, external_id);
create index        epg_channels_source_idx      on epg_channels (source_id);

-- ──────────────────────────────────────────────────────────────
-- epg_programs
-- ──────────────────────────────────────────────────────────────
create table epg_programs (
  id           uuid primary key default gen_random_uuid(),
  channel_id   uuid not null references epg_channels(id) on delete cascade,

  start_at     timestamptz not null,
  end_at       timestamptz not null,
  title        text not null,
  subtitle     text,
  description  text,
  category     text,
  age_rating   smallint,
  episode_num  text,
  season_num   text,
  icon_url     text,

  created_at   timestamptz not null default now()
);

create index epg_programs_channel_time_idx on epg_programs (channel_id, start_at, end_at);
create index epg_programs_time_idx          on epg_programs (start_at, end_at);

-- ──────────────────────────────────────────────────────────────
-- watch_history
-- ──────────────────────────────────────────────────────────────
create table watch_history (
  id               uuid primary key default gen_random_uuid(),
  profile_id       uuid not null references profiles(id) on delete cascade,
  device_id        uuid references devices(id)          on delete set null,
  channel_id       uuid references epg_channels(id)     on delete set null,
  program_id       uuid references epg_programs(id)     on delete set null,

  asset_type       text not null check (asset_type in ('live','vod','recording')),
  asset_ref        text not null,                 -- opaque id (vod:<hash>, live:<channel-ext-id>, ...)
  watched_seconds  integer not null default 0,

  started_at       timestamptz not null default now(),
  ended_at         timestamptz
);

create index watch_history_profile_idx on watch_history (profile_id, started_at desc);
create index watch_history_asset_idx   on watch_history (profile_id, asset_ref);

-- ──────────────────────────────────────────────────────────────
-- continue_watching   (deduplicated per profile+asset)
-- ──────────────────────────────────────────────────────────────
create table continue_watching (
  profile_id        uuid not null references profiles(id) on delete cascade,
  asset_ref         text not null,
  asset_type        text not null check (asset_type in ('live','vod','recording')),
  channel_id        uuid references epg_channels(id) on delete set null,

  position_seconds  integer not null,
  duration_seconds  integer,

  updated_at        timestamptz not null default now(),
  primary key (profile_id, asset_ref)
);

create index continue_watching_profile_updated_idx on continue_watching (profile_id, updated_at desc);

-- ──────────────────────────────────────────────────────────────
-- favorites
-- ──────────────────────────────────────────────────────────────
create table favorites (
  profile_id   uuid not null references profiles(id) on delete cascade,
  asset_ref    text not null,
  asset_type   text not null check (asset_type in ('live','vod','recording')),
  channel_id   uuid references epg_channels(id) on delete set null,
  label        text,
  created_at   timestamptz not null default now(),
  primary key (profile_id, asset_ref)
);

create index favorites_profile_idx on favorites (profile_id, created_at desc);

-- ──────────────────────────────────────────────────────────────
-- playback_sessions   (live, heartbeat-updated)
-- ──────────────────────────────────────────────────────────────
create table playback_sessions (
  id                 uuid primary key default gen_random_uuid(),
  profile_id         uuid not null references profiles(id) on delete cascade,
  device_id          uuid not null references devices(id)  on delete cascade,

  asset_type         text not null check (asset_type in ('live','vod','recording')),
  asset_ref          text not null,
  channel_id         uuid references epg_channels(id) on delete set null,

  started_at         timestamptz not null default now(),
  last_heartbeat_at  timestamptz not null default now(),
  ended_at           timestamptz,

  bitrate_bps        integer,
  player_state       text           -- 'buffering'|'playing'|'paused'|'error'
);

create index playback_sessions_profile_idx on playback_sessions (profile_id, started_at desc);
create index playback_sessions_active_idx  on playback_sessions (device_id) where ended_at is null;

-- ──────────────────────────────────────────────────────────────
-- audit_log   (append-only)
-- ──────────────────────────────────────────────────────────────
create table audit_log (
  id          bigserial primary key,
  account_id  uuid references accounts(id) on delete set null,
  actor_type  text not null check (actor_type in ('user','system','worker','admin')),
  event_type  text not null,                       -- 'entitlement.trial_started', 'device.revoked', ...
  payload     jsonb not null default '{}'::jsonb,
  ip          inet,
  user_agent  text,
  created_at  timestamptz not null default now()
);

create index audit_log_account_idx on audit_log (account_id, created_at desc);
create index audit_log_event_idx   on audit_log (event_type, created_at desc);
```

---

## 3. Access paths (why these indexes)

| Query | Index used |
|---|---|
| EPG grid for channel between T1..T2 | `epg_programs_channel_time_idx` |
| "What's on now across all channels" | `epg_programs_time_idx` |
| Deduplicate channels on XMLTV re-fetch | `epg_channels_source_ext_uniq` |
| Home row "Continue watching" | `continue_watching_profile_updated_idx` |
| Home row "Favorites" | `favorites_profile_idx` |
| "Recently watched" screen | `watch_history_profile_idx` |
| Concurrent-stream enforcement | `playback_sessions_active_idx` |
| Account audit viewer | `audit_log_account_idx` |
| Event analytics (e.g. all `entitlement.*`) | `audit_log_event_idx` |

---

## 4. Flow mapping (from `docs/product/user-flows.md`)

| User flow | Tables touched |
|---|---|
| Home (continue watching, favorites, rows) | `continue_watching`, `favorites`, `epg_channels`, `epg_programs` |
| EPG browse | `epg_channels`, `epg_programs` |
| Playback + resume | `playback_sessions`, `continue_watching` (heartbeat), `watch_history` (on end) |
| Device management (active session indicator) | `playback_sessions` (active partial index) |
| Error surfaces / diagnostics | `audit_log`, `playback_sessions` |

---

## 5. Cross-cutting concerns (applies to Parts 1–3)

### 5.1 UUID primary keys
- All domain tables use `uuid` PKs generated by `gen_random_uuid()` (pgcrypto).
- Exception: `audit_log` uses `bigserial` — append-heavy, strictly server-generated, IDs must be monotonic for cursor pagination.

### 5.2 Timestamps
- `created_at timestamptz not null default now()` on every domain row.
- `updated_at timestamptz not null default now()` on every mutable row; maintained by a Prisma middleware (or `BEFORE UPDATE` trigger if we drop Prisma later).
- All timestamps stored in UTC (`timestamptz`). Client renders to local tz.

### 5.3 Soft-delete strategy
- `deleted_at timestamptz null` marks a soft-deleted row: used on `accounts`, `profiles`, `sources`.
- All "list" queries filter `where deleted_at is null` — partial indexes (e.g. `profiles_account_idx`) are scoped so the filter is free.
- Hard delete only happens via the right-to-be-forgotten admin pipeline (GDPR), which cascades via FKs.
- Ledger/append tables (`purchases`, `audit_log`, `watch_history`) are **never** soft-deleted.

### 5.4 Encryption at rest for URLs & credentials
- All URLs pointing at user-owned infra — `sources.url`, `source_credentials.username/password`, `epg_channels.stream_url` — are stored as AES-256-GCM ciphertext with a per-row `bytea nonce` (96-bit) and a `key_id` pointing at a KMS (GCP KMS or AWS KMS) DEK.
- The database never sees the plaintext; decryption happens in a dedicated NestJS `CryptoModule` that only the playback/EPG/source services call.
- Key rotation: write new `key_id`, re-encrypt in background, retire old `key_id` — no schema change.

### 5.5 PIN hashing
- `profile_pins.pin_hash` is **argon2id** encoded using the libsodium default profile (`ops=3`, `mem=64 MiB`, parallelism=1). Tuned on the API host, stored in the hash string so future migrations are transparent.
- `pin_algo` column is a forward-compatibility escape hatch (e.g. `'argon2id'` → `'argon2id-v2'`).
- Brute-force mitigation: `failed_attempts` + `locked_until` on the row; after 5 failures the profile PIN is locked for 15 minutes.
- PINs are **never** logged, returned in API responses, or included in `audit_log.payload`.

### 5.6 Multi-tenant isolation
- Every domain query is scoped by `account_id` at the service layer — no row-level security in V1 (added later if we introduce an admin DB role).
- `profiles`, `devices`, `sources`, `entitlements`, `purchases` all cascade on `accounts.id` delete.

### 5.7 Family cap enforcement (5 profiles / 5 active devices)
- Schema does **not** cap — `entitlements.max_profiles` / `max_devices` do, at the application layer.
- On profile create: `count(profiles where account_id=$1 and deleted_at is null) < entitlements.max_profiles`.
- On device register: `count(devices where account_id=$1 and revoked_at is null) < entitlements.max_devices`.
- Both checks run inside the same transaction as the insert to prevent TOCTOU.

### 5.8 From here to Prisma (Run 6)
- All tables in Parts 1–3 map 1:1 to Prisma models.
- Enums (`entitlement_state`, `source_kind`) become Prisma enums.
- Partial indexes survive as raw `@@index` with `where:` (Prisma preview feature) or native migrations.
- `bytea` → Prisma `Bytes`.
- `citext` → treat as `String` in Prisma; uniqueness is guaranteed at the DB layer.
- No guesswork required — Run 6 is a mechanical translation of these three files.

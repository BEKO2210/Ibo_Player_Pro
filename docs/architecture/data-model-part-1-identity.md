# Data Model — Part 1: Identity

> **Scope:** Accounts, profiles, profile PINs, devices.
> **Other parts:** [Part 2 — Commerce & Sources](./data-model-part-2-commerce-sources.md) · [Part 3 — EPG & Activity](./data-model-part-3-epg-activity.md) · [Index + global ER diagram](./data-model.md)

---

## 1. Narrative

The **account** is the root of everything. It is keyed to a Firebase UID (primary sign-in) and owns every child entity the user ever creates: profiles, devices, entitlement, purchases, sources, EPG caches, watch data, and audit trail.

Under each account we model two orthogonal collections:

- **Profiles** — up to **5 per account** (Family plan; Single = 1). A profile is a viewing persona, not a user. Profiles can be flagged `is_kids`, in which case they are gated by a per-profile PIN and age-filtered.
- **Devices** — up to **5 concurrently active per account** (Family plan; Single = 1). A device is a server-managed slot identified by an **app-generated `install_id`**, **never** a MAC address. Revocation flips `revoked_at` — the row stays for audit.

The 5/5 caps are **enforced in application logic** (entitlement module) against `max_profiles`/`max_devices` on the `entitlements` row (see Part 2). The schema alone does not cap — this keeps upgrades from Single → Family trivial (bump the numbers, no migration).

PINs are stored in a **separate `profile_pins` table** so that (a) most profile reads never touch the hash, and (b) PIN rotation/lockout state has a clean home.

### Entities in this part

| Table | Purpose |
|---|---|
| `accounts` | Root identity; 1:1 with a Firebase user |
| `profiles` | Viewing personas (max 5 per account) |
| `profile_pins` | Argon2id PIN hash + lockout state for kids/parental gates |
| `devices` | Server-managed device slots (max 5 active per account) |

---

## 2. SQL DDL (PostgreSQL)

> All primary keys are UUID v4 (`gen_random_uuid()` from `pgcrypto`).
> All timestamps are `timestamptz` and default to `now()`.
> Soft-delete is via `deleted_at` where marked.

```sql
create extension if not exists "pgcrypto";
create extension if not exists "citext";

-- ──────────────────────────────────────────────────────────────
-- accounts
-- ──────────────────────────────────────────────────────────────
create table accounts (
  id                 uuid primary key default gen_random_uuid(),
  firebase_uid       text        not null unique,
  email              citext      not null unique,
  display_name       text,
  locale             text        not null default 'en',
  country_code       char(2),
  marketing_opt_in   boolean     not null default false,
  created_at         timestamptz not null default now(),
  updated_at         timestamptz not null default now(),
  deleted_at         timestamptz
);

create index accounts_firebase_uid_idx on accounts (firebase_uid);
create index accounts_email_idx        on accounts (email);
create index accounts_active_idx       on accounts (id) where deleted_at is null;

-- ──────────────────────────────────────────────────────────────
-- profiles
-- ──────────────────────────────────────────────────────────────
create table profiles (
  id              uuid primary key default gen_random_uuid(),
  account_id      uuid        not null references accounts(id) on delete cascade,
  name            text        not null,
  avatar_key      text,                       -- reference to avatars/ CDN asset
  is_kids         boolean     not null default false,
  max_age_rating  smallint,                   -- 6/12/16/18; null = no filter
  language        text        not null default 'en',
  position        smallint    not null default 0,
  created_at      timestamptz not null default now(),
  updated_at      timestamptz not null default now(),
  deleted_at      timestamptz
);

create index        profiles_account_idx       on profiles (account_id) where deleted_at is null;
create unique index profiles_account_name_uniq on profiles (account_id, name) where deleted_at is null;

-- ──────────────────────────────────────────────────────────────
-- profile_pins
-- ──────────────────────────────────────────────────────────────
create table profile_pins (
  profile_id       uuid        primary key references profiles(id) on delete cascade,
  pin_hash         text        not null,                -- argon2id encoded
  pin_algo         text        not null default 'argon2id',
  failed_attempts  smallint    not null default 0,
  locked_until     timestamptz,
  updated_at       timestamptz not null default now()
);

-- ──────────────────────────────────────────────────────────────
-- devices
-- ──────────────────────────────────────────────────────────────
create table devices (
  id            uuid primary key default gen_random_uuid(),
  account_id    uuid        not null references accounts(id) on delete cascade,
  device_label  text        not null,                    -- user-editable friendly name
  platform      text        not null check (platform in
                   ('android_tv','android_mobile','tvos','ios','tizen','webos','web')),
  app_version   text,
  os_version    text,
  model         text,
  install_id    text        not null,                    -- app-generated UUID per install; NOT MAC
  last_seen_at  timestamptz not null default now(),
  registered_at timestamptz not null default now(),
  revoked_at    timestamptz
);

create unique index devices_install_uniq      on devices (account_id, install_id) where revoked_at is null;
create index        devices_account_active_idx on devices (account_id)             where revoked_at is null;
create index        devices_last_seen_idx      on devices (account_id, last_seen_at desc);
```

---

## 3. Access paths (why these indexes)

| Query | Index used |
|---|---|
| Resolve account from Firebase ID token | `accounts_firebase_uid_idx` |
| Login by email (admin / support) | `accounts_email_idx` |
| List profiles for account (profile picker) | `profiles_account_idx` |
| Prevent duplicate profile names per account | `profiles_account_name_uniq` |
| Count active devices for cap enforcement | `devices_account_active_idx` |
| Re-register same install after network loss | `devices_install_uniq` |
| "Devices" settings screen sorted by recency | `devices_last_seen_idx` |

---

## 4. Flow mapping (from `docs/product/user-flows.md`)

| User flow | Tables touched |
|---|---|
| Signup | `accounts` (insert) |
| Login | `accounts` (select) |
| Profile picker | `profiles`, `profile_pins` (for kids gate) |
| Profile CRUD | `profiles`, `profile_pins` |
| Kids PIN gate | `profile_pins` |
| Device management (list / revoke / unpair) | `devices` |
| Logout | `devices` (update `revoked_at` for current slot) |

Commerce, source, EPG, and playback tables are covered in Parts 2 and 3.

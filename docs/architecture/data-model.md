# Data Model — V1

> This document is **split into three parts** for readability. Each part is self-contained: narrative, SQL DDL, indexes, and user-flow mapping. The consolidated ER diagram lives at the bottom of this file.

| Part | Scope | File |
|---|---|---|
| **1 — Identity** | `accounts`, `profiles`, `profile_pins`, `devices` | [data-model-part-1-identity.md](./data-model-part-1-identity.md) |
| **2 — Commerce & Sources** | `entitlements`, `purchases`, `sources`, `source_credentials` | [data-model-part-2-commerce-sources.md](./data-model-part-2-commerce-sources.md) |
| **3 — EPG & Activity** | `epg_channels`, `epg_programs`, `watch_history`, `continue_watching`, `favorites`, `playback_sessions`, `audit_log` + cross-cutting concerns | [data-model-part-3-epg-activity.md](./data-model-part-3-epg-activity.md) |

---

## Design principles (recap)

- **Account is the root.** All user data cascades from `accounts.id`.
- **Server-authoritative.** Trial + entitlement state live here, not on the client.
- **No MAC binding.** Devices are identified by app-generated `install_id` and managed as server-side slots.
- **Encryption at rest** for user-provided URLs and credentials (AES-256-GCM, KMS-managed keys).
- **Soft-delete** on `accounts`, `profiles`, `sources`; **append-only** on `purchases`, `watch_history`, `audit_log`.
- **UUID v4** PKs everywhere except `audit_log` (`bigserial`).
- **Caps enforced in application logic** against `entitlements.max_profiles` / `max_devices`.

See Part 3 §5 for the full cross-cutting notes (encryption, PIN hashing, timestamps, soft-delete, multi-tenant isolation, Family cap, Prisma handoff).

---

## Entitlement states (recap)

```
none ──► trial ──► expired
           │
           ├──► lifetime_single
           ├──► lifetime_family
           │
           └──► revoked   (also reachable from lifetime_*)
```

All six states from the locked product decisions are representable via the `entitlement_state` enum in Part 2.

---

## Global ER diagram

```mermaid
erDiagram
    ACCOUNTS ||--|| ENTITLEMENTS : "1:1"
    ACCOUNTS ||--o{ PROFILES : "up to 5"
    ACCOUNTS ||--o{ DEVICES : "up to 5 active"
    ACCOUNTS ||--o{ PURCHASES : "ledger"
    ACCOUNTS ||--o{ SOURCES : "user-added"
    ACCOUNTS ||--o{ AUDIT_LOG : "trail"

    PROFILES ||--o| PROFILE_PINS : "0..1"
    PROFILES ||--o{ WATCH_HISTORY : ""
    PROFILES ||--o{ CONTINUE_WATCHING : ""
    PROFILES ||--o{ FAVORITES : ""
    PROFILES ||--o{ PLAYBACK_SESSIONS : ""

    DEVICES ||--o{ PLAYBACK_SESSIONS : ""
    DEVICES ||--o{ WATCH_HISTORY : "optional"

    ENTITLEMENTS }o--|| PURCHASES : "source_purchase_id"

    SOURCES ||--o| SOURCE_CREDENTIALS : "0..1"
    SOURCES ||--o{ EPG_CHANNELS : ""

    EPG_CHANNELS ||--o{ EPG_PROGRAMS : ""
    EPG_CHANNELS ||--o{ WATCH_HISTORY : "optional"
    EPG_CHANNELS ||--o{ CONTINUE_WATCHING : "optional"
    EPG_CHANNELS ||--o{ FAVORITES : "optional"
    EPG_CHANNELS ||--o{ PLAYBACK_SESSIONS : "optional"
    EPG_PROGRAMS ||--o{ WATCH_HISTORY : "optional"

    ACCOUNTS {
        uuid id PK
        text firebase_uid UK
        citext email UK
        text display_name
        text locale
        char country_code
        bool marketing_opt_in
        timestamptz created_at
        timestamptz updated_at
        timestamptz deleted_at
    }

    PROFILES {
        uuid id PK
        uuid account_id FK
        text name
        text avatar_key
        bool is_kids
        smallint max_age_rating
        text language
        smallint position
        timestamptz deleted_at
    }

    PROFILE_PINS {
        uuid profile_id PK_FK
        text pin_hash
        text pin_algo
        smallint failed_attempts
        timestamptz locked_until
    }

    DEVICES {
        uuid id PK
        uuid account_id FK
        text device_label
        text platform
        text install_id
        timestamptz last_seen_at
        timestamptz revoked_at
    }

    ENTITLEMENTS {
        uuid id PK
        uuid account_id FK_UK
        enum state
        timestamptz trial_started_at
        timestamptz trial_expires_at
        timestamptz lifetime_activated_at
        smallint max_profiles
        smallint max_devices
        uuid source_purchase_id FK
        text revoked_reason
    }

    PURCHASES {
        uuid id PK
        uuid account_id FK
        text rail
        text product_id
        text purchase_token
        text state
        int amount_cents
        char currency
        jsonb raw_payload
        timestamptz verified_at
        timestamptz refunded_at
    }

    SOURCES {
        uuid id PK
        uuid account_id FK
        text label
        enum kind
        bytea url_ciphertext
        bytea url_nonce
        text url_key_id
        int refresh_interval_seconds
        timestamptz last_fetched_at
        timestamptz deleted_at
    }

    SOURCE_CREDENTIALS {
        uuid source_id PK_FK
        bytea username_ciphertext
        bytea password_ciphertext
        text key_id
    }

    EPG_CHANNELS {
        uuid id PK
        uuid source_id FK
        text external_id
        text display_name
        text icon_url
        bytea stream_url_ciphertext
    }

    EPG_PROGRAMS {
        uuid id PK
        uuid channel_id FK
        timestamptz start_at
        timestamptz end_at
        text title
        smallint age_rating
    }

    WATCH_HISTORY {
        uuid id PK
        uuid profile_id FK
        uuid device_id FK
        uuid channel_id FK
        uuid program_id FK
        text asset_type
        text asset_ref
        int watched_seconds
        timestamptz started_at
        timestamptz ended_at
    }

    CONTINUE_WATCHING {
        uuid profile_id PK_FK
        text asset_ref PK
        text asset_type
        uuid channel_id FK
        int position_seconds
        int duration_seconds
        timestamptz updated_at
    }

    FAVORITES {
        uuid profile_id PK_FK
        text asset_ref PK
        text asset_type
        uuid channel_id FK
        text label
        timestamptz created_at
    }

    PLAYBACK_SESSIONS {
        uuid id PK
        uuid profile_id FK
        uuid device_id FK
        text asset_type
        text asset_ref
        uuid channel_id FK
        timestamptz started_at
        timestamptz last_heartbeat_at
        timestamptz ended_at
    }

    AUDIT_LOG {
        bigint id PK
        uuid account_id FK
        text actor_type
        text event_type
        jsonb payload
        inet ip
        timestamptz created_at
    }
```

---

## Acceptance check (vs. Run 3 deliverables)

- [x] Narrative overview — per part + this index
- [x] SQL DDL (PostgreSQL) for all 15 tables — spread across Parts 1–3
- [x] Indexes + FKs for common access paths — per part, §3
- [x] ER diagram as mermaid `erDiagram` — above
- [x] Notes on encryption-at-rest, PIN hashing (argon2id), soft-delete, timestamps, UUID PKs — Part 3 §5
- [x] All 5 entitlement states (+ `none`) representable — Part 2 enum
- [x] Family cap (5 profiles / 5 active devices) enforceable — Part 2 (`max_profiles`/`max_devices`) + Part 3 §5.7
- [x] Every entity in `docs/product/user-flows.md` has a table — see per-part flow mapping
- [x] Reader can go straight to Prisma in Run 6 — Part 3 §5.8

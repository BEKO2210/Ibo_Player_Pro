-- Premium TV Player — initial migration (V1)
-- Covers all 15 tables from docs/architecture/data-model.md, matching
-- services/api/prisma/schema.prisma. Safe to apply on a fresh Postgres 16
-- instance provisioned by infra/docker/docker-compose.yml (extensions
-- pgcrypto + citext are preloaded by infra/postgres/init/01-extensions.sql,
-- but the CREATE EXTENSION IF NOT EXISTS calls are repeated here for safety).

CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "citext";

-- ============================================================================
-- Enums
-- ============================================================================

CREATE TYPE "EntitlementState" AS ENUM (
  'none',
  'trial',
  'lifetime_single',
  'lifetime_family',
  'expired',
  'revoked'
);

CREATE TYPE "SourceKind" AS ENUM ('m3u', 'xmltv', 'm3u_plus_epg');

CREATE TYPE "DevicePlatform" AS ENUM (
  'android_tv',
  'android_mobile',
  'web',
  'ios',
  'tvos',
  'tizen',
  'webos',
  'unknown'
);

CREATE TYPE "PlaybackState" AS ENUM (
  'starting',
  'playing',
  'paused',
  'buffering',
  'stopped',
  'error'
);

-- ============================================================================
-- 1) accounts
-- ============================================================================

CREATE TABLE "accounts" (
  "id"              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  "firebase_uid"    TEXT NOT NULL,
  "email"           CITEXT NOT NULL,
  "email_verified"  BOOLEAN NOT NULL DEFAULT FALSE,
  "locale"          VARCHAR(16) NOT NULL DEFAULT 'en',
  "trial_consumed"  BOOLEAN NOT NULL DEFAULT FALSE,
  "status"          TEXT NOT NULL DEFAULT 'active',
  "created_at"      TIMESTAMPTZ(6) NOT NULL DEFAULT now(),
  "updated_at"      TIMESTAMPTZ(6) NOT NULL DEFAULT now(),
  "deleted_at"      TIMESTAMPTZ(6)
);
CREATE UNIQUE INDEX "accounts_firebase_uid_key" ON "accounts"("firebase_uid");
CREATE UNIQUE INDEX "accounts_email_key" ON "accounts"("email");

-- ============================================================================
-- 2) devices
-- ============================================================================

CREATE TABLE "devices" (
  "id"                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  "account_id"         UUID NOT NULL,
  "device_token_hash"  TEXT NOT NULL,
  "device_name"        TEXT NOT NULL,
  "platform"           "DevicePlatform" NOT NULL,
  "app_version"        TEXT,
  "os_version"         TEXT,
  "last_ip"            INET,
  "last_seen_at"       TIMESTAMPTZ(6),
  "revoked_at"         TIMESTAMPTZ(6),
  "created_at"         TIMESTAMPTZ(6) NOT NULL DEFAULT now(),
  "updated_at"         TIMESTAMPTZ(6) NOT NULL DEFAULT now(),
  "deleted_at"         TIMESTAMPTZ(6)
);
CREATE UNIQUE INDEX "devices_device_token_hash_key" ON "devices"("device_token_hash");
CREATE INDEX "idx_devices_account_active"
  ON "devices"("account_id", "revoked_at", "deleted_at");
ALTER TABLE "devices" ADD CONSTRAINT "devices_account_id_fkey"
  FOREIGN KEY ("account_id") REFERENCES "accounts"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- ============================================================================
-- 3) profiles
-- ============================================================================

CREATE TABLE "profiles" (
  "id"          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  "account_id"  UUID NOT NULL,
  "name"        VARCHAR(50) NOT NULL,
  "avatar_key"  TEXT,
  "is_kids"     BOOLEAN NOT NULL DEFAULT FALSE,
  "age_limit"   SMALLINT,
  "is_default"  BOOLEAN NOT NULL DEFAULT FALSE,
  "created_at"  TIMESTAMPTZ(6) NOT NULL DEFAULT now(),
  "updated_at"  TIMESTAMPTZ(6) NOT NULL DEFAULT now(),
  "deleted_at"  TIMESTAMPTZ(6),
  CONSTRAINT "profiles_age_limit_ck" CHECK ("age_limit" IS NULL OR "age_limit" BETWEEN 0 AND 21)
);
CREATE INDEX "idx_profiles_account_active" ON "profiles"("account_id", "deleted_at");
ALTER TABLE "profiles" ADD CONSTRAINT "profiles_account_id_fkey"
  FOREIGN KEY ("account_id") REFERENCES "accounts"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- ============================================================================
-- 4) profile_pins
-- ============================================================================

CREATE TABLE "profile_pins" (
  "profile_id"            UUID PRIMARY KEY,
  "pin_hash"              TEXT NOT NULL,
  "pin_algo"              TEXT NOT NULL DEFAULT 'argon2id',
  "pin_updated_at"        TIMESTAMPTZ(6) NOT NULL DEFAULT now(),
  "failed_attempt_count"  INTEGER NOT NULL DEFAULT 0,
  "lock_until"            TIMESTAMPTZ(6),
  "created_at"            TIMESTAMPTZ(6) NOT NULL DEFAULT now(),
  "updated_at"            TIMESTAMPTZ(6) NOT NULL DEFAULT now(),
  CONSTRAINT "profile_pins_algo_ck" CHECK ("pin_algo" = 'argon2id')
);
ALTER TABLE "profile_pins" ADD CONSTRAINT "profile_pins_profile_id_fkey"
  FOREIGN KEY ("profile_id") REFERENCES "profiles"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- ============================================================================
-- 5) purchases (created before entitlements so entitlements can FK it)
-- ============================================================================

CREATE TABLE "purchases" (
  "id"               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  "account_id"       UUID NOT NULL,
  "provider"         TEXT NOT NULL DEFAULT 'google_play',
  "product_id"       TEXT NOT NULL,
  "purchase_token"   TEXT NOT NULL,
  "order_id"         TEXT,
  "purchase_state"   TEXT NOT NULL,
  "purchased_at"     TIMESTAMPTZ(6),
  "acknowledged_at"  TIMESTAMPTZ(6),
  "raw_payload"      JSONB,
  "created_at"       TIMESTAMPTZ(6) NOT NULL DEFAULT now(),
  "updated_at"       TIMESTAMPTZ(6) NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX "purchases_token_unique" ON "purchases"("provider", "purchase_token");
CREATE INDEX "idx_purchases_account_created_at"
  ON "purchases"("account_id", "created_at" DESC);
ALTER TABLE "purchases" ADD CONSTRAINT "purchases_account_id_fkey"
  FOREIGN KEY ("account_id") REFERENCES "accounts"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- ============================================================================
-- 6) entitlements
-- ============================================================================

CREATE TABLE "entitlements" (
  "id"                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  "account_id"          UUID NOT NULL,
  "state"               "EntitlementState" NOT NULL DEFAULT 'none',
  "trial_started_at"    TIMESTAMPTZ(6),
  "trial_ends_at"       TIMESTAMPTZ(6),
  "activated_at"        TIMESTAMPTZ(6),
  "expires_at"          TIMESTAMPTZ(6),
  "revoked_at"          TIMESTAMPTZ(6),
  "revoke_reason"       TEXT,
  "source_purchase_id"  UUID,
  "created_at"          TIMESTAMPTZ(6) NOT NULL DEFAULT now(),
  "updated_at"          TIMESTAMPTZ(6) NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX "entitlements_account_id_key" ON "entitlements"("account_id");
CREATE INDEX "idx_entitlements_state" ON "entitlements"("state");
ALTER TABLE "entitlements" ADD CONSTRAINT "entitlements_account_id_fkey"
  FOREIGN KEY ("account_id") REFERENCES "accounts"("id") ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE "entitlements" ADD CONSTRAINT "entitlements_source_purchase_id_fkey"
  FOREIGN KEY ("source_purchase_id") REFERENCES "purchases"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- ============================================================================
-- 7) sources
-- ============================================================================

CREATE TABLE "sources" (
  "id"                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  "account_id"            UUID NOT NULL,
  "profile_id"            UUID,
  "name"                  VARCHAR(120) NOT NULL,
  "kind"                  "SourceKind" NOT NULL,
  "is_active"             BOOLEAN NOT NULL DEFAULT TRUE,
  "validation_status"     TEXT NOT NULL DEFAULT 'pending',
  "last_validated_at"     TIMESTAMPTZ(6),
  "item_count_estimate"   INTEGER,
  "created_at"            TIMESTAMPTZ(6) NOT NULL DEFAULT now(),
  "updated_at"            TIMESTAMPTZ(6) NOT NULL DEFAULT now(),
  "deleted_at"            TIMESTAMPTZ(6)
);
CREATE INDEX "idx_sources_account_active"
  ON "sources"("account_id", "is_active", "deleted_at");
CREATE INDEX "idx_sources_profile_active"
  ON "sources"("profile_id", "is_active", "deleted_at");
ALTER TABLE "sources" ADD CONSTRAINT "sources_account_id_fkey"
  FOREIGN KEY ("account_id") REFERENCES "accounts"("id") ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE "sources" ADD CONSTRAINT "sources_profile_id_fkey"
  FOREIGN KEY ("profile_id") REFERENCES "profiles"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- ============================================================================
-- 8) source_credentials
-- ============================================================================

CREATE TABLE "source_credentials" (
  "source_id"           UUID PRIMARY KEY,
  "encrypted_url"       BYTEA NOT NULL,
  "encrypted_username"  BYTEA,
  "encrypted_password"  BYTEA,
  "encrypted_headers"   BYTEA,
  "kms_key_id"          TEXT NOT NULL,
  "encryption_version"  SMALLINT NOT NULL DEFAULT 1,
  "created_at"          TIMESTAMPTZ(6) NOT NULL DEFAULT now(),
  "updated_at"          TIMESTAMPTZ(6) NOT NULL DEFAULT now()
);
ALTER TABLE "source_credentials" ADD CONSTRAINT "source_credentials_source_id_fkey"
  FOREIGN KEY ("source_id") REFERENCES "sources"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- ============================================================================
-- 9) epg_channels
-- ============================================================================

CREATE TABLE "epg_channels" (
  "id"                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  "source_id"             UUID NOT NULL,
  "external_channel_id"   TEXT NOT NULL,
  "display_name"          TEXT NOT NULL,
  "icon_url"              TEXT,
  "created_at"            TIMESTAMPTZ(6) NOT NULL DEFAULT now(),
  "updated_at"            TIMESTAMPTZ(6) NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX "epg_channels_unique"
  ON "epg_channels"("source_id", "external_channel_id");
CREATE INDEX "idx_epg_channels_source_name"
  ON "epg_channels"("source_id", "display_name");
ALTER TABLE "epg_channels" ADD CONSTRAINT "epg_channels_source_id_fkey"
  FOREIGN KEY ("source_id") REFERENCES "sources"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- ============================================================================
-- 10) epg_programs
-- ============================================================================

CREATE TABLE "epg_programs" (
  "id"                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  "channel_id"            UUID NOT NULL,
  "source_id"             UUID NOT NULL,
  "external_program_id"   TEXT,
  "title"                 TEXT NOT NULL,
  "subtitle"              TEXT,
  "description"           TEXT,
  "category"              TEXT,
  "rating"                TEXT,
  "starts_at"             TIMESTAMPTZ(6) NOT NULL,
  "ends_at"               TIMESTAMPTZ(6) NOT NULL,
  "created_at"            TIMESTAMPTZ(6) NOT NULL DEFAULT now(),
  "updated_at"            TIMESTAMPTZ(6) NOT NULL DEFAULT now(),
  CONSTRAINT "epg_programs_time_ck" CHECK ("ends_at" > "starts_at")
);
CREATE INDEX "idx_epg_programs_channel_time"
  ON "epg_programs"("channel_id", "starts_at", "ends_at");
CREATE INDEX "idx_epg_programs_source_time"
  ON "epg_programs"("source_id", "starts_at", "ends_at");
ALTER TABLE "epg_programs" ADD CONSTRAINT "epg_programs_channel_id_fkey"
  FOREIGN KEY ("channel_id") REFERENCES "epg_channels"("id") ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE "epg_programs" ADD CONSTRAINT "epg_programs_source_id_fkey"
  FOREIGN KEY ("source_id") REFERENCES "sources"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- ============================================================================
-- 11) watch_history
-- ============================================================================

CREATE TABLE "watch_history" (
  "id"                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  "account_id"        UUID NOT NULL,
  "profile_id"        UUID NOT NULL,
  "source_id"         UUID,
  "item_id"           TEXT NOT NULL,
  "item_type"         TEXT NOT NULL,
  "watched_seconds"   INTEGER NOT NULL DEFAULT 0,
  "duration_seconds"  INTEGER,
  "completed"         BOOLEAN NOT NULL DEFAULT FALSE,
  "occurred_at"       TIMESTAMPTZ(6) NOT NULL DEFAULT now(),
  "created_at"        TIMESTAMPTZ(6) NOT NULL DEFAULT now(),
  "updated_at"        TIMESTAMPTZ(6) NOT NULL DEFAULT now(),
  CONSTRAINT "watch_history_seconds_ck" CHECK ("watched_seconds" >= 0)
);
CREATE INDEX "idx_watch_history_profile_time"
  ON "watch_history"("profile_id", "occurred_at" DESC);
CREATE INDEX "idx_watch_history_account_time"
  ON "watch_history"("account_id", "occurred_at" DESC);
ALTER TABLE "watch_history" ADD CONSTRAINT "watch_history_account_id_fkey"
  FOREIGN KEY ("account_id") REFERENCES "accounts"("id") ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE "watch_history" ADD CONSTRAINT "watch_history_profile_id_fkey"
  FOREIGN KEY ("profile_id") REFERENCES "profiles"("id") ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE "watch_history" ADD CONSTRAINT "watch_history_source_id_fkey"
  FOREIGN KEY ("source_id") REFERENCES "sources"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- ============================================================================
-- 12) continue_watching
-- ============================================================================

CREATE TABLE "continue_watching" (
  "id"                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  "account_id"                UUID NOT NULL,
  "profile_id"                UUID NOT NULL,
  "source_id"                 UUID,
  "item_id"                   TEXT NOT NULL,
  "item_type"                 TEXT NOT NULL,
  "resume_position_seconds"   INTEGER NOT NULL DEFAULT 0,
  "duration_seconds"          INTEGER,
  "last_played_at"            TIMESTAMPTZ(6) NOT NULL DEFAULT now(),
  "created_at"                TIMESTAMPTZ(6) NOT NULL DEFAULT now(),
  "updated_at"                TIMESTAMPTZ(6) NOT NULL DEFAULT now(),
  "deleted_at"                TIMESTAMPTZ(6),
  CONSTRAINT "continue_watching_resume_ck" CHECK ("resume_position_seconds" >= 0)
);
CREATE UNIQUE INDEX "continue_watching_unique"
  ON "continue_watching"("profile_id", "item_id");
CREATE INDEX "idx_continue_watching_profile_last_played"
  ON "continue_watching"("profile_id", "last_played_at" DESC);
ALTER TABLE "continue_watching" ADD CONSTRAINT "continue_watching_account_id_fkey"
  FOREIGN KEY ("account_id") REFERENCES "accounts"("id") ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE "continue_watching" ADD CONSTRAINT "continue_watching_profile_id_fkey"
  FOREIGN KEY ("profile_id") REFERENCES "profiles"("id") ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE "continue_watching" ADD CONSTRAINT "continue_watching_source_id_fkey"
  FOREIGN KEY ("source_id") REFERENCES "sources"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- ============================================================================
-- 13) favorites
-- ============================================================================

CREATE TABLE "favorites" (
  "id"                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  "account_id"        UUID NOT NULL,
  "profile_id"        UUID NOT NULL,
  "source_id"         UUID,
  "item_id"           TEXT NOT NULL,
  "item_type"         TEXT NOT NULL,
  "title_cache"       TEXT,
  "poster_url_cache"  TEXT,
  "created_at"        TIMESTAMPTZ(6) NOT NULL DEFAULT now(),
  "updated_at"        TIMESTAMPTZ(6) NOT NULL DEFAULT now(),
  "deleted_at"        TIMESTAMPTZ(6)
);
CREATE UNIQUE INDEX "favorites_unique" ON "favorites"("profile_id", "item_id");
CREATE INDEX "idx_favorites_profile_created"
  ON "favorites"("profile_id", "created_at" DESC);
ALTER TABLE "favorites" ADD CONSTRAINT "favorites_account_id_fkey"
  FOREIGN KEY ("account_id") REFERENCES "accounts"("id") ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE "favorites" ADD CONSTRAINT "favorites_profile_id_fkey"
  FOREIGN KEY ("profile_id") REFERENCES "profiles"("id") ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE "favorites" ADD CONSTRAINT "favorites_source_id_fkey"
  FOREIGN KEY ("source_id") REFERENCES "sources"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- ============================================================================
-- 14) playback_sessions
-- ============================================================================

CREATE TABLE "playback_sessions" (
  "id"                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  "account_id"                UUID NOT NULL,
  "profile_id"                UUID NOT NULL,
  "device_id"                 UUID,
  "source_id"                 UUID,
  "item_id"                   TEXT NOT NULL,
  "item_type"                 TEXT NOT NULL,
  "session_started_at"        TIMESTAMPTZ(6) NOT NULL DEFAULT now(),
  "last_heartbeat_at"         TIMESTAMPTZ(6),
  "stopped_at"                TIMESTAMPTZ(6),
  "latest_position_seconds"   INTEGER NOT NULL DEFAULT 0,
  "state"                     "PlaybackState" NOT NULL DEFAULT 'starting',
  "error_code"                TEXT,
  "created_at"                TIMESTAMPTZ(6) NOT NULL DEFAULT now(),
  "updated_at"                TIMESTAMPTZ(6) NOT NULL DEFAULT now(),
  CONSTRAINT "playback_sessions_position_ck" CHECK ("latest_position_seconds" >= 0)
);
CREATE INDEX "idx_playback_sessions_profile_time"
  ON "playback_sessions"("profile_id", "session_started_at" DESC);
CREATE INDEX "idx_playback_sessions_device_time"
  ON "playback_sessions"("device_id", "session_started_at" DESC);
ALTER TABLE "playback_sessions" ADD CONSTRAINT "playback_sessions_account_id_fkey"
  FOREIGN KEY ("account_id") REFERENCES "accounts"("id") ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE "playback_sessions" ADD CONSTRAINT "playback_sessions_profile_id_fkey"
  FOREIGN KEY ("profile_id") REFERENCES "profiles"("id") ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE "playback_sessions" ADD CONSTRAINT "playback_sessions_device_id_fkey"
  FOREIGN KEY ("device_id") REFERENCES "devices"("id") ON DELETE SET NULL ON UPDATE CASCADE;
ALTER TABLE "playback_sessions" ADD CONSTRAINT "playback_sessions_source_id_fkey"
  FOREIGN KEY ("source_id") REFERENCES "sources"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- ============================================================================
-- 15) audit_log
-- ============================================================================

CREATE TABLE "audit_log" (
  "id"             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  "account_id"     UUID,
  "actor_type"     TEXT NOT NULL,
  "actor_id"       TEXT,
  "action"         TEXT NOT NULL,
  "target_table"   TEXT,
  "target_id"      TEXT,
  "request_id"     TEXT,
  "ip_address"     INET,
  "user_agent"     TEXT,
  "metadata"       JSONB NOT NULL DEFAULT '{}'::jsonb,
  "created_at"     TIMESTAMPTZ(6) NOT NULL DEFAULT now()
);
CREATE INDEX "idx_audit_log_account_created"
  ON "audit_log"("account_id", "created_at" DESC);
CREATE INDEX "idx_audit_log_action_created"
  ON "audit_log"("action", "created_at" DESC);
ALTER TABLE "audit_log" ADD CONSTRAINT "audit_log_account_id_fkey"
  FOREIGN KEY ("account_id") REFERENCES "accounts"("id") ON DELETE SET NULL ON UPDATE CASCADE;

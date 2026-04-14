-- Prerequisite extensions for Premium TV Player schema.
-- Runs automatically on first Postgres container init.
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS citext;

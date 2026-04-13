/**
 * Zod schemas — Part 3 (EPG & Activity).
 * Mirrors packages/api-contracts/openapi/part-3-epg-activity.yaml.
 */
import { z } from 'zod';
import { AssetType, Iso8601, Uuid } from './common';

// ─── EPG ───────────────────────────────────────────────────────────────────

export const EpgChannel = z.object({
  id: Uuid,
  source_id: Uuid,
  external_id: z.string(),
  display_name: z.string(),
  icon_url: z.string().nullable(),
  language: z.string().nullable(),
  category: z.string().nullable(),
  position: z.number().int().nullable(),
});
export type EpgChannel = z.infer<typeof EpgChannel>;

export const EpgChannelsResponse = z.object({
  items: z.array(EpgChannel),
  next_cursor: z.string().nullable().optional(),
});
export type EpgChannelsResponse = z.infer<typeof EpgChannelsResponse>;

export const EpgProgram = z.object({
  id: Uuid,
  channel_id: Uuid,
  start_at: Iso8601,
  end_at: Iso8601,
  title: z.string(),
  subtitle: z.string().nullable(),
  description: z.string().nullable(),
  category: z.string().nullable(),
  age_rating: z.number().int().nullable(),
  episode_num: z.string().nullable(),
  season_num: z.string().nullable(),
  icon_url: z.string().nullable(),
});
export type EpgProgram = z.infer<typeof EpgProgram>;

// ─── Continue Watching ─────────────────────────────────────────────────────

export const ContinueWatchingItem = z.object({
  profile_id: Uuid,
  asset_ref: z.string(),
  asset_type: AssetType,
  channel_id: Uuid.nullable(),
  position_seconds: z.number().int().min(0),
  duration_seconds: z.number().int().min(0).nullable(),
  updated_at: Iso8601,
});
export type ContinueWatchingItem = z.infer<typeof ContinueWatchingItem>;

export const ContinueWatchingUpsert = z.object({
  asset_ref: z.string().min(1),
  asset_type: AssetType,
  channel_id: Uuid.optional(),
  position_seconds: z.number().int().min(0),
  duration_seconds: z.number().int().min(0).optional(),
});
export type ContinueWatchingUpsert = z.infer<typeof ContinueWatchingUpsert>;

// ─── Favorites ─────────────────────────────────────────────────────────────

export const FavoriteItem = z.object({
  profile_id: Uuid,
  asset_ref: z.string(),
  asset_type: AssetType,
  channel_id: Uuid.nullable(),
  label: z.string().nullable(),
  created_at: Iso8601,
});
export type FavoriteItem = z.infer<typeof FavoriteItem>;

export const FavoriteUpsert = z.object({
  asset_type: AssetType,
  channel_id: Uuid.optional(),
  label: z.string().max(120).optional(),
});
export type FavoriteUpsert = z.infer<typeof FavoriteUpsert>;

// ─── Watch History ─────────────────────────────────────────────────────────

export const WatchHistoryAppend = z.object({
  asset_type: AssetType,
  asset_ref: z.string().min(1),
  channel_id: Uuid.optional(),
  program_id: Uuid.optional(),
  device_id: Uuid.optional(),
  watched_seconds: z.number().int().min(0),
  started_at: Iso8601,
  ended_at: Iso8601.optional(),
});
export type WatchHistoryAppend = z.infer<typeof WatchHistoryAppend>;

// ─── Playback ──────────────────────────────────────────────────────────────

export const PlayerState = z.enum(['buffering', 'playing', 'paused', 'error']);
export type PlayerState = z.infer<typeof PlayerState>;

export const PlaybackSession = z.object({
  id: Uuid,
  profile_id: Uuid,
  device_id: Uuid,
  asset_type: AssetType,
  asset_ref: z.string(),
  channel_id: Uuid.nullable(),
  started_at: Iso8601,
  last_heartbeat_at: Iso8601,
  ended_at: Iso8601.nullable(),
  bitrate_bps: z.number().int().nullable(),
  player_state: PlayerState.nullable(),
});
export type PlaybackSession = z.infer<typeof PlaybackSession>;

export const PlaybackSessionStart = z.object({
  device_id: Uuid,
  asset_type: AssetType,
  asset_ref: z.string().min(1),
  channel_id: Uuid.optional(),
});
export type PlaybackSessionStart = z.infer<typeof PlaybackSessionStart>;

export const PlaybackHeartbeat = z.object({
  position_seconds: z.number().int().min(0),
  duration_seconds: z.number().int().min(0).optional(),
  bitrate_bps: z.number().int().min(0).optional(),
  player_state: PlayerState.optional(),
});
export type PlaybackHeartbeat = z.infer<typeof PlaybackHeartbeat>;

export const PlaybackEnd = z.object({
  position_seconds: z.number().int().min(0),
  duration_seconds: z.number().int().min(0).optional(),
  reason: z.enum(['user', 'eof', 'error', 'entitlement_lost']).optional(),
});
export type PlaybackEnd = z.infer<typeof PlaybackEnd>;

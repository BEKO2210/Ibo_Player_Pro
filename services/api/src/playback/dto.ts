import { IsEnum, IsInt, IsOptional, IsString, IsUUID, Length, Min } from 'class-validator';
import { PlaybackState } from '@prisma/client';

export class StartPlaybackDto {
  @IsUUID()
  profileId!: string;

  @IsUUID()
  sourceId!: string;

  @IsString()
  @Length(1, 256)
  itemId!: string;

  /** live | vod | series_episode (matches watch_history.item_type). */
  @IsString()
  @Length(1, 32)
  itemType!: string;

  @IsOptional()
  @IsUUID()
  deviceId?: string;
}

export class HeartbeatDto {
  @IsUUID()
  sessionId!: string;

  @IsInt()
  @Min(0)
  positionSeconds!: number;

  @IsEnum(PlaybackState)
  state!: PlaybackState;

  /** Optional — clients send this when they know the full duration. */
  @IsOptional()
  @IsInt()
  @Min(0)
  durationSeconds?: number;
}

export class StopPlaybackDto {
  @IsUUID()
  sessionId!: string;

  @IsInt()
  @Min(0)
  finalPositionSeconds!: number;

  @IsOptional()
  @IsInt()
  @Min(0)
  durationSeconds?: number;

  /** When true, set completed=true on the watch_history row. */
  @IsOptional()
  completed?: boolean;
}

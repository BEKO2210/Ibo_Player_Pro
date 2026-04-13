import {
  Body,
  Controller,
  Get,
  HttpCode,
  HttpStatus,
  Post,
  Query,
  Req,
  UseGuards,
} from '@nestjs/common';
import type { PlaybackSession } from '@prisma/client';
import { AuthGuard, type AuthenticatedRequest } from '../auth/auth.guard';
import { HeartbeatDto, StartPlaybackDto, StopPlaybackDto } from './dto';
import { PlaybackService } from './playback.service';

export interface StartPlaybackResponse {
  session: PlaybackSessionView;
}

export interface PlaybackSessionView {
  id: string;
  profileId: string;
  sourceId: string | null;
  itemId: string;
  itemType: string;
  state: string;
  latestPositionSeconds: number;
  sessionStartedAt: string;
  lastHeartbeatAt: string | null;
  stoppedAt: string | null;
}

export interface ContinueWatchingResponse {
  items: ContinueWatchingRow[];
}

export interface ContinueWatchingRow {
  id: string;
  sourceId: string | null;
  itemId: string;
  itemType: string;
  resumePositionSeconds: number;
  durationSeconds: number | null;
  lastPlayedAt: string;
}

@Controller()
@UseGuards(AuthGuard)
export class PlaybackController {
  constructor(private readonly playback: PlaybackService) {}

  @Post('playback/start')
  @HttpCode(HttpStatus.OK)
  async start(
    @Req() req: AuthenticatedRequest,
    @Body() body: StartPlaybackDto,
  ): Promise<StartPlaybackResponse> {
    const session = await this.playback.start(req.account, {
      profileId: body.profileId,
      sourceId: body.sourceId,
      itemId: body.itemId,
      itemType: body.itemType,
      deviceId: body.deviceId,
    });
    return { session: toView(session) };
  }

  @Post('playback/heartbeat')
  @HttpCode(HttpStatus.OK)
  async heartbeat(
    @Req() req: AuthenticatedRequest,
    @Body() body: HeartbeatDto,
  ): Promise<{ session: PlaybackSessionView }> {
    const session = await this.playback.heartbeat(req.account, {
      sessionId: body.sessionId,
      positionSeconds: body.positionSeconds,
      state: body.state,
      durationSeconds: body.durationSeconds,
    });
    return { session: toView(session) };
  }

  @Post('playback/stop')
  @HttpCode(HttpStatus.OK)
  async stop(
    @Req() req: AuthenticatedRequest,
    @Body() body: StopPlaybackDto,
  ): Promise<{ session: PlaybackSessionView }> {
    const session = await this.playback.stop(req.account, {
      sessionId: body.sessionId,
      finalPositionSeconds: body.finalPositionSeconds,
      durationSeconds: body.durationSeconds,
      completed: body.completed,
    });
    return { session: toView(session) };
  }

  @Get('continue-watching')
  async listContinueWatching(
    @Req() req: AuthenticatedRequest,
    @Query('profileId') profileId: string,
    @Query('limit') limit?: string,
  ): Promise<ContinueWatchingResponse> {
    const parsedLimit = limit ? Math.max(1, Number.parseInt(limit, 10) || 20) : 20;
    const rows = await this.playback.listContinueWatching(req.account, profileId, parsedLimit);
    return {
      items: rows.map((r) => ({
        id: r.id,
        sourceId: r.sourceId,
        itemId: r.itemId,
        itemType: r.itemType,
        resumePositionSeconds: r.resumePositionSeconds,
        durationSeconds: r.durationSeconds,
        lastPlayedAt: r.lastPlayedAt.toISOString(),
      })),
    };
  }
}

function toView(s: PlaybackSession): PlaybackSessionView {
  return {
    id: s.id,
    profileId: s.profileId,
    sourceId: s.sourceId,
    itemId: s.itemId,
    itemType: s.itemType,
    state: s.state,
    latestPositionSeconds: s.latestPositionSeconds,
    sessionStartedAt: s.sessionStartedAt.toISOString(),
    lastHeartbeatAt: s.lastHeartbeatAt?.toISOString() ?? null,
    stoppedAt: s.stoppedAt?.toISOString() ?? null,
  };
}

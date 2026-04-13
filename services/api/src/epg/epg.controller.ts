import { Controller, Get, Query, Req, UseGuards } from '@nestjs/common';
import type { EpgChannel, EpgProgram } from '@prisma/client';
import { AuthGuard, type AuthenticatedRequest } from '../auth/auth.guard';
import { EpgService } from './epg.service';

export interface EpgChannelView {
  id: string;
  sourceId: string;
  externalChannelId: string;
  displayName: string;
  iconUrl: string | null;
}

export interface EpgProgrammeView {
  id: string;
  channelId: string;
  sourceId: string;
  title: string;
  subtitle: string | null;
  description: string | null;
  category: string | null;
  startsAt: string;
  endsAt: string;
}

@Controller('epg')
@UseGuards(AuthGuard)
export class EpgController {
  constructor(private readonly epg: EpgService) {}

  @Get('channels')
  async channels(
    @Req() req: AuthenticatedRequest,
    @Query('sourceId') sourceId: string,
  ): Promise<{ channels: EpgChannelView[] }> {
    const rows = await this.epg.channelsForSource(req.account, sourceId);
    return { channels: rows.map(toChannelView) };
  }

  @Get('programmes')
  async programmes(
    @Req() req: AuthenticatedRequest,
    @Query('channelId') channelId: string,
    @Query('from') from?: string,
    @Query('to') to?: string,
  ): Promise<{ programmes: EpgProgrammeView[] }> {
    const rows = await this.epg.programmes(
      req.account,
      channelId,
      from ? new Date(from) : undefined,
      to ? new Date(to) : undefined,
    );
    return { programmes: rows.map(toProgrammeView) };
  }
}

function toChannelView(c: EpgChannel): EpgChannelView {
  return {
    id: c.id,
    sourceId: c.sourceId,
    externalChannelId: c.externalChannelId,
    displayName: c.displayName,
    iconUrl: c.iconUrl,
  };
}

function toProgrammeView(p: EpgProgram): EpgProgrammeView {
  return {
    id: p.id,
    channelId: p.channelId,
    sourceId: p.sourceId,
    title: p.title,
    subtitle: p.subtitle,
    description: p.description,
    category: p.category,
    startsAt: p.startsAt.toISOString(),
    endsAt: p.endsAt.toISOString(),
  };
}

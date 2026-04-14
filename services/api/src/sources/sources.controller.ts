import {
  Body,
  Controller,
  Delete,
  Get,
  HttpCode,
  HttpStatus,
  Param,
  ParseUUIDPipe,
  Post,
  Put,
  Query,
  Req,
  UseGuards,
} from '@nestjs/common';
import { IsOptional, IsUUID } from 'class-validator';
import type { Source, SourceKind } from '@prisma/client';
import { AuthGuard, type AuthenticatedRequest } from '../auth/auth.guard';
import { SourceService } from './sources.service';
import { CreateSourceDto, UpdateSourceDto } from './dto';

class ListSourcesQuery {
  @IsOptional()
  @IsUUID()
  profileId?: string;
}

export interface SourceView {
  id: string;
  profileId: string | null;
  name: string;
  kind: SourceKind;
  isActive: boolean;
  validationStatus: string;
  itemCountEstimate: number | null;
  createdAt: string;
}

@Controller({ path: 'sources' })
@UseGuards(AuthGuard)
export class SourcesController {
  constructor(private readonly sources: SourceService) {}

  @Get()
  async list(
    @Req() req: AuthenticatedRequest,
    @Query() query: ListSourcesQuery,
  ): Promise<{ sources: SourceView[] }> {
    const items = await this.sources.listForAccount(req.account.id, query.profileId);
    return { sources: items.map(toView) };
  }

  @Post()
  @HttpCode(HttpStatus.CREATED)
  async create(
    @Req() req: AuthenticatedRequest,
    @Body() body: CreateSourceDto,
  ): Promise<{ source: SourceView }> {
    const created = await this.sources.create(req.account, body);
    return { source: toView(created) };
  }

  @Put(':id')
  async update(
    @Req() req: AuthenticatedRequest,
    @Param('id', new ParseUUIDPipe()) id: string,
    @Body() body: UpdateSourceDto,
  ): Promise<{ source: SourceView }> {
    const updated = await this.sources.update(req.account, id, body);
    return { source: toView(updated) };
  }

  @Delete(':id')
  @HttpCode(HttpStatus.NO_CONTENT)
  async remove(
    @Req() req: AuthenticatedRequest,
    @Param('id', new ParseUUIDPipe()) id: string,
  ): Promise<void> {
    await this.sources.softDelete(req.account, id);
  }
}

function toView(s: Source): SourceView {
  return {
    id: s.id,
    profileId: s.profileId,
    name: s.name,
    kind: s.kind,
    isActive: s.isActive,
    validationStatus: s.validationStatus,
    itemCountEstimate: s.itemCountEstimate,
    createdAt: s.createdAt.toISOString(),
  };
}

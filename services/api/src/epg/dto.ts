import { IsISO8601, IsOptional, IsUUID } from 'class-validator';

export class EpgChannelsQuery {
  @IsUUID()
  sourceId!: string;
}

export class EpgProgrammesQuery {
  @IsUUID()
  channelId!: string;

  @IsOptional()
  @IsISO8601()
  from?: string;

  @IsOptional()
  @IsISO8601()
  to?: string;
}

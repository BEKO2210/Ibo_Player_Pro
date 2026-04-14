import { Injectable, NotFoundException } from '@nestjs/common';
import type { Account, EpgChannel, EpgProgram } from '@prisma/client';
import { PrismaService } from '../prisma/prisma.service';

@Injectable()
export class EpgService {
  constructor(private readonly prisma: PrismaService) {}

  async channelsForSource(account: Account, sourceId: string): Promise<EpgChannel[]> {
    // Defensive: source must belong to the caller.
    const source = await this.prisma.source.findFirst({
      where: { id: sourceId, accountId: account.id, deletedAt: null },
    });
    if (!source) throw new NotFoundException('Source not found for this account.');

    return this.prisma.epgChannel.findMany({
      where: { sourceId },
      orderBy: { displayName: 'asc' },
    });
  }

  async programmes(
    account: Account,
    channelId: string,
    from?: Date,
    to?: Date,
  ): Promise<EpgProgram[]> {
    const channel = await this.prisma.epgChannel.findUnique({
      where: { id: channelId },
      include: { source: true },
    });
    if (!channel || channel.source.accountId !== account.id || channel.source.deletedAt) {
      throw new NotFoundException('EPG channel not found for this account.');
    }

    const windowFrom = from ?? new Date();
    const windowTo = to ?? new Date(windowFrom.getTime() + 6 * 60 * 60 * 1000);

    return this.prisma.epgProgram.findMany({
      where: {
        channelId,
        endsAt: { gt: windowFrom },
        startsAt: { lt: windowTo },
      },
      orderBy: { startsAt: 'asc' },
    });
  }
}

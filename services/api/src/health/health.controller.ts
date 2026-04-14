import { Controller, Get } from '@nestjs/common';
import {
  HealthCheck,
  HealthCheckResult,
  HealthCheckService,
  HealthIndicatorResult,
} from '@nestjs/terminus';
import { PrismaService } from '../prisma/prisma.service';
import { RedisService } from '../redis/redis.service';

@Controller('health')
export class HealthController {
  constructor(
    private readonly health: HealthCheckService,
    private readonly prisma: PrismaService,
    private readonly redis: RedisService,
  ) {}

  @Get()
  @HealthCheck()
  async check(): Promise<HealthCheckResult> {
    return this.health.check([
      async (): Promise<HealthIndicatorResult> => {
        try {
          await this.prisma.ping();
          return { database: { status: 'up' } };
        } catch (err) {
          return {
            database: {
              status: 'down',
              message: err instanceof Error ? err.message : 'unknown error',
            },
          };
        }
      },
      async (): Promise<HealthIndicatorResult> => {
        try {
          const pong = await this.redis.ping();
          return { redis: { status: pong === 'PONG' ? 'up' : 'down' } };
        } catch (err) {
          return {
            redis: {
              status: 'down',
              message: err instanceof Error ? err.message : 'unknown error',
            },
          };
        }
      },
      async (): Promise<HealthIndicatorResult> => ({
        service: { status: 'up', name: 'premium-player-api' },
      }),
    ]);
  }
}

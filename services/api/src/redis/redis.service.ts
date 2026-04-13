import {
  Inject,
  Injectable,
  Logger,
  OnModuleDestroy,
  OnModuleInit,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import Redis from 'ioredis';
import type { AppConfig } from '../config/configuration';

@Injectable()
export class RedisService implements OnModuleInit, OnModuleDestroy {
  private readonly logger = new Logger(RedisService.name);
  private client!: Redis;

  constructor(
    @Inject(ConfigService)
    private readonly config: ConfigService<AppConfig, true>,
  ) {}

  onModuleInit(): void {
    const url = this.config.get('redis.url', { infer: true });
    this.client = new Redis(url, {
      lazyConnect: false,
      maxRetriesPerRequest: 3,
      enableReadyCheck: true,
    });
    this.client.on('error', (err) => this.logger.error(`Redis error: ${err.message}`));
    this.client.on('ready', () => this.logger.log('Redis ready'));
  }

  async onModuleDestroy(): Promise<void> {
    if (this.client) {
      await this.client.quit();
    }
  }

  getClient(): Redis {
    return this.client;
  }

  async ping(): Promise<string> {
    return this.client.ping();
  }
}

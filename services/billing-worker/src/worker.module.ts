import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { configuration, validate } from '@api/config/configuration';
import { PrismaModule } from '@api/prisma/prisma.module';
import { RedisModule } from '@api/redis/redis.module';
import { FirebaseModule } from '@api/firebase/firebase.module';
import { EntitlementModule } from '@api/entitlement/entitlement.module';
import { BillingModule } from '@api/billing/billing.module';
import { BillingWorker } from './billing.worker';

/**
 * Minimal Nest module for the billing worker process. Reuses the API's
 * config/Prisma/Redis/Firebase/Entitlement/Billing modules via the `@api/*`
 * path alias, so there is exactly one source of truth for the billing
 * logic. No HTTP listener.
 */
@Module({
  imports: [
    ConfigModule.forRoot({
      isGlobal: true,
      load: [configuration],
      validate,
      cache: true,
    }),
    PrismaModule,
    RedisModule,
    FirebaseModule,
    EntitlementModule,
    BillingModule,
  ],
  providers: [BillingWorker],
  exports: [BillingWorker],
})
export class WorkerModule {}

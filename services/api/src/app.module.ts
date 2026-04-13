import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { APP_FILTER } from '@nestjs/core';
import { TerminusModule } from '@nestjs/terminus';
import { configuration, validate } from './config/configuration';
import { PrismaModule } from './prisma/prisma.module';
import { RedisModule } from './redis/redis.module';
import { FirebaseModule } from './firebase/firebase.module';
import { HealthModule } from './health/health.module';
import { AuthModule } from './auth/auth.module';
import { EntitlementModule } from './entitlement/entitlement.module';
import { DevicesModule } from './devices/devices.module';
import { BillingModule } from './billing/billing.module';
import { AllExceptionsFilter } from './common/http-exception.filter';

@Module({
  imports: [
    ConfigModule.forRoot({
      isGlobal: true,
      load: [configuration],
      validate,
      cache: true,
    }),
    TerminusModule,
    PrismaModule,
    RedisModule,
    FirebaseModule,
    HealthModule,
    AuthModule,
    EntitlementModule,
    DevicesModule,
    BillingModule,
  ],
  providers: [
    {
      provide: APP_FILTER,
      useClass: AllExceptionsFilter,
    },
  ],
})
export class AppModule {}

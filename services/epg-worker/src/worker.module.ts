import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { configuration, validate } from '@api/config/configuration';
import { PrismaModule } from '@api/prisma/prisma.module';
import { FirebaseModule } from '@api/firebase/firebase.module';
import { SourcesModule } from '@api/sources/sources.module';
import { EpgFetcher } from './epg.fetcher';
import { EpgWorker } from './epg.worker';

/**
 * Minimal Nest module for the EPG worker. Reuses the API's config +
 * Prisma + Sources (for source_credentials decryption) via the @api/*
 * path alias so worker and API share exactly one implementation of the
 * persistence layer.
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
    FirebaseModule,
    SourcesModule,
  ],
  providers: [EpgFetcher, EpgWorker],
  exports: [EpgFetcher, EpgWorker],
})
export class WorkerModule {}

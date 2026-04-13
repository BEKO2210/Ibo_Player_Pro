import 'reflect-metadata';
import { NestFactory } from '@nestjs/core';
import { Logger } from '@nestjs/common';
import { WorkerModule } from './worker.module';
import { EpgWorker } from './epg.worker';

async function bootstrap(): Promise<void> {
  const app = await NestFactory.createApplicationContext(WorkerModule, { bufferLogs: false });
  app.enableShutdownHooks();
  const worker = app.get(EpgWorker);
  await worker.start();

  if (process.env.WORKER_RUN_ONCE === 'true') {
    await app.close();
    return;
  }
  Logger.log('EPG worker running. Send SIGTERM to exit.', 'Bootstrap');
}

bootstrap().catch((err) => {
  // eslint-disable-next-line no-console
  console.error('Fatal epg-worker bootstrap error', err);
  process.exit(1);
});

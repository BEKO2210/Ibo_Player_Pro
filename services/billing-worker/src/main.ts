import 'reflect-metadata';
import { NestFactory } from '@nestjs/core';
import { Logger } from '@nestjs/common';
import { WorkerModule } from './worker.module';
import { BillingWorker } from './billing.worker';

async function bootstrap(): Promise<void> {
  // Standalone app — no HTTP listener.
  const app = await NestFactory.createApplicationContext(WorkerModule, {
    bufferLogs: false,
  });
  app.enableShutdownHooks();

  const worker = app.get(BillingWorker);
  await worker.start();

  if (process.env.WORKER_RUN_ONCE === 'true') {
    await app.close();
    return;
  }

  // Keep the process alive on SIGINT/SIGTERM via shutdownHooks.
  Logger.log('Billing worker running. Send SIGTERM to exit.', 'Bootstrap');
}

bootstrap().catch((err) => {
  // eslint-disable-next-line no-console
  console.error('Fatal billing-worker bootstrap error', err);
  process.exit(1);
});

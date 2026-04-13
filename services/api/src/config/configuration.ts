import { z } from 'zod';

const envSchema = z.object({
  NODE_ENV: z
    .enum(['development', 'test', 'staging', 'production'])
    .default('development'),
  API_HOST: z.string().default('0.0.0.0'),
  API_PORT: z.coerce.number().int().positive().default(3000),

  DATABASE_URL: z
    .string()
    .min(1, 'DATABASE_URL is required (postgres connection string)'),

  REDIS_URL: z.string().min(1, 'REDIS_URL is required (redis connection string)'),

  LOG_LEVEL: z.enum(['error', 'warn', 'log', 'debug', 'verbose']).default('log'),
});

export type Env = z.infer<typeof envSchema>;

export interface AppConfig {
  app: {
    env: Env['NODE_ENV'];
    host: string;
    port: number;
    logLevel: Env['LOG_LEVEL'];
  };
  database: {
    url: string;
  };
  redis: {
    url: string;
  };
}

export function validate(raw: Record<string, unknown>): Env {
  const parsed = envSchema.safeParse(raw);
  if (!parsed.success) {
    const message = parsed.error.issues
      .map((i) => `${i.path.join('.')}: ${i.message}`)
      .join('; ');
    throw new Error(`Invalid environment configuration: ${message}`);
  }
  return parsed.data;
}

export function configuration(): AppConfig {
  const env = validate(process.env);
  return {
    app: {
      env: env.NODE_ENV,
      host: env.API_HOST,
      port: env.API_PORT,
      logLevel: env.LOG_LEVEL,
    },
    database: { url: env.DATABASE_URL },
    redis: { url: env.REDIS_URL },
  };
}

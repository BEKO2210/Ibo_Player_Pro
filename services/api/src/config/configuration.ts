import { z } from 'zod';

const envSchema = z
  .object({
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

    // Firebase Admin — either a single JSON blob OR the three discrete fields.
    FIREBASE_SERVICE_ACCOUNT_JSON: z.string().optional(),
    FIREBASE_PROJECT_ID: z.string().optional(),
    FIREBASE_CLIENT_EMAIL: z.string().optional(),
    FIREBASE_PRIVATE_KEY: z.string().optional(),

    // Billing (Google Play). The service-account reuses the Firebase
    // credentials above — add the `androidpublisher` scope in GCP IAM.
    BILLING_ANDROID_PACKAGE_NAME: z.string().optional(),
    BILLING_PRODUCT_ID_SINGLE: z.string().default('premium_player_single'),
    BILLING_PRODUCT_ID_FAMILY: z.string().default('premium_player_family'),
    BILLING_WORKER_POLL_INTERVAL_MS: z.coerce
      .number()
      .int()
      .min(1_000)
      .max(300_000)
      .default(15_000),
  })
  .superRefine((env, ctx) => {
    const hasJson = !!env.FIREBASE_SERVICE_ACCOUNT_JSON;
    const hasTriple =
      !!env.FIREBASE_PROJECT_ID &&
      !!env.FIREBASE_CLIENT_EMAIL &&
      !!env.FIREBASE_PRIVATE_KEY;
    // Only enforce in non-test environments; tests can mock Firebase.
    if (env.NODE_ENV !== 'test' && !hasJson && !hasTriple) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['FIREBASE_SERVICE_ACCOUNT_JSON'],
        message:
          'Firebase credentials are required: set FIREBASE_SERVICE_ACCOUNT_JSON, or all of FIREBASE_PROJECT_ID + FIREBASE_CLIENT_EMAIL + FIREBASE_PRIVATE_KEY.',
      });
    }
  });

export type Env = z.infer<typeof envSchema>;

export interface FirebaseCredentials {
  projectId: string;
  clientEmail: string;
  privateKey: string;
}

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
  firebase: {
    credentials: FirebaseCredentials | null;
  };
  billing: {
    androidPackageName: string | null;
    productIdSingle: string;
    productIdFamily: string;
    workerPollIntervalMs: number;
  };
}

function resolveFirebaseCredentials(env: Env): FirebaseCredentials | null {
  if (env.FIREBASE_SERVICE_ACCOUNT_JSON) {
    try {
      const parsed = JSON.parse(env.FIREBASE_SERVICE_ACCOUNT_JSON) as {
        project_id?: string;
        client_email?: string;
        private_key?: string;
      };
      if (parsed.project_id && parsed.client_email && parsed.private_key) {
        return {
          projectId: parsed.project_id,
          clientEmail: parsed.client_email,
          privateKey: parsed.private_key.replace(/\\n/g, '\n'),
        };
      }
    } catch {
      throw new Error(
        'FIREBASE_SERVICE_ACCOUNT_JSON is not valid JSON. Expected a Firebase service-account JSON object.',
      );
    }
    throw new Error(
      'FIREBASE_SERVICE_ACCOUNT_JSON is missing one of: project_id, client_email, private_key.',
    );
  }

  if (env.FIREBASE_PROJECT_ID && env.FIREBASE_CLIENT_EMAIL && env.FIREBASE_PRIVATE_KEY) {
    return {
      projectId: env.FIREBASE_PROJECT_ID,
      clientEmail: env.FIREBASE_CLIENT_EMAIL,
      // Allow `\n`-escaped private keys (common in .env files).
      privateKey: env.FIREBASE_PRIVATE_KEY.replace(/\\n/g, '\n'),
    };
  }

  return null;
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
    firebase: { credentials: resolveFirebaseCredentials(env) },
    billing: {
      androidPackageName: env.BILLING_ANDROID_PACKAGE_NAME ?? null,
      productIdSingle: env.BILLING_PRODUCT_ID_SINGLE,
      productIdFamily: env.BILLING_PRODUCT_ID_FAMILY,
      workerPollIntervalMs: env.BILLING_WORKER_POLL_INTERVAL_MS,
    },
  };
}

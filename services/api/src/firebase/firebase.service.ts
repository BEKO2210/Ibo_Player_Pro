import { Inject, Injectable, Logger, OnModuleInit } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import {
  cert,
  getApp,
  getApps,
  initializeApp,
  App as FirebaseApp,
} from 'firebase-admin/app';
import { getAuth, Auth, DecodedIdToken } from 'firebase-admin/auth';
import type { AppConfig } from '../config/configuration';

/**
 * Lazy wrapper around Firebase Admin SDK.
 *
 * - Initializes the default app on first use (keeping startup cheap if Firebase
 *   is not reached at boot).
 * - Safe to use in tests: when credentials are null (test env), attempts to
 *   call `verifyIdToken` throw a clear error rather than crashing the process.
 */
@Injectable()
export class FirebaseService implements OnModuleInit {
  private readonly logger = new Logger(FirebaseService.name);
  private app: FirebaseApp | null = null;

  constructor(
    @Inject(ConfigService)
    private readonly config: ConfigService<AppConfig, true>,
  ) {}

  onModuleInit(): void {
    // No eager init — we just log credential presence.
    const creds = this.config.get('firebase.credentials', { infer: true });
    if (creds) {
      this.logger.log(`Firebase credentials present (project=${creds.projectId})`);
    } else {
      this.logger.warn(
        'Firebase credentials NOT configured — token verification will fail. Set FIREBASE_SERVICE_ACCOUNT_JSON or the FIREBASE_PROJECT_ID/CLIENT_EMAIL/PRIVATE_KEY trio.',
      );
    }
  }

  private getApp(): FirebaseApp {
    if (this.app) {
      return this.app;
    }
    const existing = getApps();
    if (existing.length > 0) {
      this.app = getApp();
      return this.app;
    }
    const creds = this.config.get('firebase.credentials', { infer: true });
    if (!creds) {
      throw new Error(
        'Firebase Admin is not configured. Set FIREBASE_SERVICE_ACCOUNT_JSON or the project/client-email/private-key trio.',
      );
    }
    this.app = initializeApp({
      credential: cert({
        projectId: creds.projectId,
        clientEmail: creds.clientEmail,
        privateKey: creds.privateKey,
      }),
    });
    this.logger.log('Firebase Admin initialized');
    return this.app;
  }

  auth(): Auth {
    return getAuth(this.getApp());
  }

  /**
   * Verify a Firebase ID token. Throws if invalid/expired/revoked.
   * `checkRevoked=true` forces a revocation check (useful for session refresh).
   */
  async verifyIdToken(token: string, checkRevoked = false): Promise<DecodedIdToken> {
    return this.auth().verifyIdToken(token, checkRevoked);
  }
}

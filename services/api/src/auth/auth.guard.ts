import {
  CanActivate,
  ExecutionContext,
  HttpException,
  HttpStatus,
  Injectable,
  Logger,
} from '@nestjs/common';
import type { Request } from 'express';
import type { DecodedIdToken } from 'firebase-admin/auth';
import { FirebaseService } from '../firebase/firebase.service';
import { AccountsService } from './accounts.service';
import { buildErrorEnvelope, ErrorCode } from '../common/errors';
import type { Account } from '@prisma/client';

export interface AuthenticatedRequest extends Request {
  firebaseToken: DecodedIdToken;
  account: Account;
}

/**
 * Verifies `Authorization: Bearer <firebase_id_token>` and attaches the
 * caller's Firebase claims + synced `accounts` row to the request.
 *
 * Used on every protected endpoint (non-auth routes). Returns 401 with the
 * stable ErrorEnvelope shape on any verification/sync failure.
 */
@Injectable()
export class AuthGuard implements CanActivate {
  private readonly logger = new Logger(AuthGuard.name);

  constructor(
    private readonly firebase: FirebaseService,
    private readonly accounts: AccountsService,
  ) {}

  async canActivate(context: ExecutionContext): Promise<boolean> {
    const req = context.switchToHttp().getRequest<AuthenticatedRequest>();
    const token = this.extractBearer(req.headers['authorization']);
    if (!token) {
      throw this.unauthorized('Missing Authorization: Bearer token');
    }

    let decoded: DecodedIdToken;
    try {
      decoded = await this.firebase.verifyIdToken(token, false);
    } catch (err) {
      this.logger.debug(
        `Firebase token verification failed: ${
          err instanceof Error ? err.message : String(err)
        }`,
      );
      throw this.unauthorized('Invalid or expired Firebase ID token');
    }

    const account = await this.accounts.syncFromFirebaseToken(decoded);
    req.firebaseToken = decoded;
    req.account = account;
    return true;
  }

  private extractBearer(header: string | string[] | undefined): string | null {
    if (!header) return null;
    const raw = Array.isArray(header) ? header[0] : header;
    if (!raw) return null;
    const [scheme, value] = raw.split(' ', 2);
    if (!scheme || scheme.toLowerCase() !== 'bearer' || !value) return null;
    return value.trim();
  }

  private unauthorized(message: string): HttpException {
    return new HttpException(
      buildErrorEnvelope(ErrorCode.UNAUTHORIZED, message),
      HttpStatus.UNAUTHORIZED,
    );
  }
}

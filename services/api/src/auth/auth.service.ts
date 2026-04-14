import {
  HttpException,
  HttpStatus,
  Injectable,
  Logger,
} from '@nestjs/common';
import { FirebaseService } from '../firebase/firebase.service';
import { AccountsService, type AccountSnapshot } from './accounts.service';
import { buildErrorEnvelope, ErrorCode } from '../common/errors';

/**
 * Orchestrates Firebase token verification + account sync for the three
 * auth endpoints (register / login / refresh).
 *
 * Scope for Run 7: verify Firebase ID tokens and return AccountSnapshot
 * (account + entitlement). Access/refresh/device token issuance is Run 8+.
 */
@Injectable()
export class AuthService {
  private readonly logger = new Logger(AuthService.name);

  constructor(
    private readonly firebase: FirebaseService,
    private readonly accounts: AccountsService,
  ) {}

  /** POST /v1/auth/register — first verify after Firebase signup. */
  async register(token: string, locale?: string): Promise<AccountSnapshot> {
    return this.syncAndSnapshot(token, { locale, checkRevoked: false });
  }

  /** POST /v1/auth/login — token verify + account sync. */
  async login(token: string, locale?: string): Promise<AccountSnapshot> {
    return this.syncAndSnapshot(token, { locale, checkRevoked: false });
  }

  /** POST /v1/auth/refresh — token re-verify with revocation check. */
  async refresh(token: string): Promise<AccountSnapshot> {
    return this.syncAndSnapshot(token, { checkRevoked: true });
  }

  private async syncAndSnapshot(
    token: string,
    opts: { locale?: string; checkRevoked: boolean },
  ): Promise<AccountSnapshot> {
    let decoded;
    try {
      decoded = await this.firebase.verifyIdToken(token, opts.checkRevoked);
    } catch (err) {
      this.logger.debug(
        `Firebase verifyIdToken failed: ${
          err instanceof Error ? err.message : String(err)
        }`,
      );
      throw new HttpException(
        buildErrorEnvelope(
          ErrorCode.UNAUTHORIZED,
          'Invalid or expired Firebase ID token',
        ),
        HttpStatus.UNAUTHORIZED,
      );
    }

    const account = await this.accounts.syncFromFirebaseToken(decoded, {
      locale: opts.locale,
    });
    return this.accounts.snapshot(account.id);
  }
}

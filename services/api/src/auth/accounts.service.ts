import { Injectable, Logger } from '@nestjs/common';
import type { Account, Entitlement } from '@prisma/client';
import type { DecodedIdToken } from 'firebase-admin/auth';
import { PrismaService } from '../prisma/prisma.service';

export interface AccountSnapshot {
  account: Account;
  entitlement: Entitlement;
}

/**
 * Owns the local `accounts` row lifecycle driven by Firebase Authentication.
 *
 * Contract:
 * - `syncFromFirebaseToken(decoded)` is idempotent: first verify creates the
 *   row + an empty `entitlement` (state=none); subsequent verifies update
 *   email / email_verified / locale drift if Firebase has newer values.
 * - Never throws on duplicate — relies on DB uniqueness for firebase_uid + email.
 */
@Injectable()
export class AccountsService {
  private readonly logger = new Logger(AccountsService.name);

  constructor(private readonly prisma: PrismaService) {}

  /**
   * Upsert an account row from a Firebase decoded ID token. Creates the empty
   * entitlement row alongside on first touch. Returns the up-to-date account.
   */
  async syncFromFirebaseToken(
    decoded: DecodedIdToken,
    fallback?: { locale?: string },
  ): Promise<Account> {
    const email = this.requireEmail(decoded);
    const locale = this.resolveLocale(fallback?.locale);
    const emailVerified = decoded.email_verified ?? false;

    const account = await this.prisma.account.upsert({
      where: { firebaseUid: decoded.uid },
      update: {
        email,
        emailVerified,
        // Locale drift: only update if caller explicitly provided one.
        ...(fallback?.locale ? { locale } : {}),
      },
      create: {
        firebaseUid: decoded.uid,
        email,
        emailVerified,
        locale,
        entitlement: {
          create: {
            state: 'none',
          },
        },
      },
    });

    this.logger.debug(`Synced account ${account.id} (firebase_uid=${decoded.uid})`);
    return account;
  }

  /**
   * Return account + entitlement (guaranteed present after sync).
   */
  async snapshot(accountId: string): Promise<AccountSnapshot> {
    const [account, entitlement] = await Promise.all([
      this.prisma.account.findUniqueOrThrow({ where: { id: accountId } }),
      this.prisma.entitlement.upsert({
        where: { accountId },
        update: {},
        create: { accountId, state: 'none' },
      }),
    ]);
    return { account, entitlement };
  }

  private requireEmail(decoded: DecodedIdToken): string {
    const email = decoded.email;
    if (!email || typeof email !== 'string') {
      throw new Error(
        'Firebase ID token is missing `email`. Email/password auth is required for Premium TV Player accounts.',
      );
    }
    return email.toLowerCase();
  }

  private resolveLocale(locale: string | undefined): string {
    if (!locale) return 'en';
    const trimmed = locale.trim();
    if (!trimmed) return 'en';
    return trimmed.slice(0, 16);
  }
}

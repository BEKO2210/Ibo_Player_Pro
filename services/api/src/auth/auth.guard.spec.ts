import { HttpException, HttpStatus, type ExecutionContext } from '@nestjs/common';
import type { DecodedIdToken } from 'firebase-admin/auth';
import type { Account } from '@prisma/client';
import { AuthGuard, type AuthenticatedRequest } from './auth.guard';
import type { FirebaseService } from '../firebase/firebase.service';
import type { AccountsService } from './accounts.service';

describe('AuthGuard', () => {
  const account: Account = {
    id: 'a1111111-1111-1111-1111-111111111111',
    firebaseUid: 'firebase-uid-123',
    email: 'user@example.com',
    emailVerified: true,
    locale: 'en',
    trialConsumed: false,
    status: 'active',
    createdAt: new Date(),
    updatedAt: new Date(),
    deletedAt: null,
  };

  const decodedToken = {
    uid: 'firebase-uid-123',
    email: 'user@example.com',
    email_verified: true,
  } as DecodedIdToken;

  let firebase: { verifyIdToken: jest.Mock };
  let accounts: { syncFromFirebaseToken: jest.Mock };
  let guard: AuthGuard;

  beforeEach(() => {
    firebase = { verifyIdToken: jest.fn() };
    accounts = { syncFromFirebaseToken: jest.fn() };
    guard = new AuthGuard(
      firebase as unknown as FirebaseService,
      accounts as unknown as AccountsService,
    );
  });

  function makeContext(authHeader?: string): {
    ctx: ExecutionContext;
    req: Partial<AuthenticatedRequest>;
  } {
    const req: Partial<AuthenticatedRequest> = {
      headers: authHeader === undefined ? {} : { authorization: authHeader },
    };
    const ctx = {
      switchToHttp: () => ({
        getRequest: () => req,
        getResponse: () => ({}),
      }),
    } as unknown as ExecutionContext;
    return { ctx, req };
  }

  it('attaches firebaseToken + account on success', async () => {
    firebase.verifyIdToken.mockResolvedValue(decodedToken);
    accounts.syncFromFirebaseToken.mockResolvedValue(account);
    const { ctx, req } = makeContext('Bearer valid-token-xyz');

    await expect(guard.canActivate(ctx)).resolves.toBe(true);

    expect(firebase.verifyIdToken).toHaveBeenCalledWith('valid-token-xyz', false);
    expect(accounts.syncFromFirebaseToken).toHaveBeenCalledWith(decodedToken);
    expect(req.firebaseToken).toBe(decodedToken);
    expect(req.account).toBe(account);
  });

  it('accepts case-insensitive "bearer" scheme', async () => {
    firebase.verifyIdToken.mockResolvedValue(decodedToken);
    accounts.syncFromFirebaseToken.mockResolvedValue(account);
    const { ctx } = makeContext('bearer valid-token-xyz');

    await expect(guard.canActivate(ctx)).resolves.toBe(true);
  });

  it('rejects requests without Authorization header (401 ErrorEnvelope)', async () => {
    const { ctx } = makeContext(undefined);

    await expectUnauthorized(() => guard.canActivate(ctx), /Missing Authorization/);
    expect(firebase.verifyIdToken).not.toHaveBeenCalled();
  });

  it('rejects non-Bearer schemes', async () => {
    const { ctx } = makeContext('Basic dXNlcjpwYXNz');

    await expectUnauthorized(() => guard.canActivate(ctx), /Missing Authorization/);
  });

  it('rejects empty bearer values', async () => {
    const { ctx } = makeContext('Bearer ');

    await expectUnauthorized(() => guard.canActivate(ctx), /Missing Authorization/);
  });

  it('rejects invalid Firebase tokens', async () => {
    firebase.verifyIdToken.mockRejectedValue(new Error('token expired'));
    const { ctx } = makeContext('Bearer bad-token');

    await expectUnauthorized(
      () => guard.canActivate(ctx),
      /Invalid or expired Firebase ID token/,
    );
    expect(accounts.syncFromFirebaseToken).not.toHaveBeenCalled();
  });

  async function expectUnauthorized(
    run: () => Promise<unknown>,
    messageRegex: RegExp,
  ): Promise<void> {
    try {
      await run();
      fail('Expected HttpException');
    } catch (err) {
      expect(err).toBeInstanceOf(HttpException);
      const ex = err as HttpException;
      expect(ex.getStatus()).toBe(HttpStatus.UNAUTHORIZED);
      const body = ex.getResponse() as {
        error: { code: string; message: string };
      };
      expect(body.error.code).toBe('UNAUTHORIZED');
      expect(body.error.message).toMatch(messageRegex);
    }
  }
});

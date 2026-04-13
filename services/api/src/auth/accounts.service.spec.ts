import type { DecodedIdToken } from 'firebase-admin/auth';
import type { Account, Entitlement } from '@prisma/client';
import { AccountsService } from './accounts.service';
import type { PrismaService } from '../prisma/prisma.service';

describe('AccountsService', () => {
  const now = new Date('2026-04-13T12:00:00.000Z');
  const existingAccount: Account = {
    id: 'a1111111-1111-1111-1111-111111111111',
    firebaseUid: 'firebase-uid-123',
    email: 'user@example.com',
    emailVerified: true,
    locale: 'en',
    trialConsumed: false,
    status: 'active',
    createdAt: now,
    updatedAt: now,
    deletedAt: null,
  };

  const existingEntitlement: Entitlement = {
    id: 'e1111111-1111-1111-1111-111111111111',
    accountId: existingAccount.id,
    state: 'none',
    trialStartedAt: null,
    trialEndsAt: null,
    activatedAt: null,
    expiresAt: null,
    revokedAt: null,
    revokeReason: null,
    sourcePurchaseId: null,
    createdAt: now,
    updatedAt: now,
  };

  let prisma: {
    account: { upsert: jest.Mock; findUniqueOrThrow: jest.Mock };
    entitlement: { upsert: jest.Mock };
  };
  let service: AccountsService;

  beforeEach(() => {
    prisma = {
      account: {
        upsert: jest.fn(),
        findUniqueOrThrow: jest.fn(),
      },
      entitlement: {
        upsert: jest.fn(),
      },
    };
    service = new AccountsService(prisma as unknown as PrismaService);
  });

  function decoded(overrides: Partial<DecodedIdToken> = {}): DecodedIdToken {
    return {
      uid: 'firebase-uid-123',
      email: 'User@Example.com',
      email_verified: true,
      aud: 'test',
      auth_time: 0,
      exp: 0,
      iat: 0,
      iss: 'test',
      sub: 'test',
      firebase: { identities: {}, sign_in_provider: 'password' },
      ...overrides,
    } as DecodedIdToken;
  }

  describe('syncFromFirebaseToken', () => {
    it('upserts with lowercased email, creates entitlement on first sync', async () => {
      prisma.account.upsert.mockResolvedValue(existingAccount);

      const result = await service.syncFromFirebaseToken(decoded());

      expect(prisma.account.upsert).toHaveBeenCalledTimes(1);
      const call = prisma.account.upsert.mock.calls[0][0];
      expect(call.where).toEqual({ firebaseUid: 'firebase-uid-123' });
      expect(call.create).toEqual({
        firebaseUid: 'firebase-uid-123',
        email: 'user@example.com',
        emailVerified: true,
        locale: 'en',
        entitlement: { create: { state: 'none' } },
      });
      expect(call.update).toEqual({
        email: 'user@example.com',
        emailVerified: true,
      });
      expect(result).toBe(existingAccount);
    });

    it('updates locale only when caller provides one (drift-safe)', async () => {
      prisma.account.upsert.mockResolvedValue(existingAccount);

      await service.syncFromFirebaseToken(decoded(), { locale: 'de' });

      const call = prisma.account.upsert.mock.calls[0][0];
      expect(call.update).toEqual({
        email: 'user@example.com',
        emailVerified: true,
        locale: 'de',
      });
      expect(call.create.locale).toBe('de');
    });

    it('defaults emailVerified=false when token omits the claim', async () => {
      prisma.account.upsert.mockResolvedValue(existingAccount);

      await service.syncFromFirebaseToken(decoded({ email_verified: undefined }));

      const call = prisma.account.upsert.mock.calls[0][0];
      expect(call.create.emailVerified).toBe(false);
      expect(call.update.emailVerified).toBe(false);
    });

    it('throws when Firebase token has no email', async () => {
      await expect(
        service.syncFromFirebaseToken(decoded({ email: undefined })),
      ).rejects.toThrow(/missing `email`/);
      expect(prisma.account.upsert).not.toHaveBeenCalled();
    });

    it('clamps locale to 16 chars and falls back to "en" on empty string', async () => {
      prisma.account.upsert.mockResolvedValue(existingAccount);

      await service.syncFromFirebaseToken(decoded(), { locale: '   ' });
      let call = prisma.account.upsert.mock.calls[0][0];
      expect(call.create.locale).toBe('en');

      prisma.account.upsert.mockClear();
      prisma.account.upsert.mockResolvedValue(existingAccount);
      await service.syncFromFirebaseToken(decoded(), {
        locale: 'abcdefghijklmnopqrstuv',
      });
      call = prisma.account.upsert.mock.calls[0][0];
      expect(call.create.locale).toHaveLength(16);
    });
  });

  describe('snapshot', () => {
    it('returns account + entitlement for a synced account', async () => {
      prisma.account.findUniqueOrThrow.mockResolvedValue(existingAccount);
      prisma.entitlement.upsert.mockResolvedValue(existingEntitlement);

      const snap = await service.snapshot(existingAccount.id);

      expect(snap.account).toBe(existingAccount);
      expect(snap.entitlement).toBe(existingEntitlement);
      expect(prisma.entitlement.upsert).toHaveBeenCalledWith({
        where: { accountId: existingAccount.id },
        update: {},
        create: { accountId: existingAccount.id, state: 'none' },
      });
    });
  });
});

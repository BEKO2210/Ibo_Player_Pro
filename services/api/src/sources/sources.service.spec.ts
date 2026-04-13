import { HttpException, HttpStatus, NotFoundException } from '@nestjs/common';
import { randomBytes } from 'node:crypto';
import type { ConfigService } from '@nestjs/config';
import type { Account, Entitlement, Source, SourceCredential } from '@prisma/client';
import { SourceService } from './sources.service';
import { SourceCryptoService } from './source-crypto.service';
import type { PrismaService } from '../prisma/prisma.service';
import type { EntitlementService } from '../entitlement/entitlement.service';
import type { AppConfig } from '../config/configuration';

describe('SourceService', () => {
  const now = new Date('2026-04-13T12:00:00.000Z');
  const account: Account = {
    id: 'a1',
    firebaseUid: 'f',
    email: 'u@e.com',
    emailVerified: true,
    locale: 'en',
    trialConsumed: false,
    status: 'active',
    createdAt: now,
    updatedAt: now,
    deletedAt: null,
  };

  function makeEnt(state: Entitlement['state']): Entitlement {
    return {
      id: 'e1',
      accountId: account.id,
      state,
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
  }

  function makeSource(over: Partial<Source> = {}): Source {
    return {
      id: 's1',
      accountId: account.id,
      profileId: null,
      name: 'My M3U',
      kind: 'm3u',
      isActive: true,
      validationStatus: 'pending',
      lastValidatedAt: null,
      itemCountEstimate: null,
      createdAt: now,
      updatedAt: now,
      deletedAt: null,
      ...over,
    };
  }

  function realCrypto(): SourceCryptoService {
    const key = randomBytes(32); // capture once so all get() calls return the same key
    const config = {
      get: jest.fn((k: string) => {
        if (k === 'sourceCrypto.key') return key;
        if (k === 'sourceCrypto.kmsKeyId') return 'local-test';
        return undefined;
      }),
    } as unknown as ConfigService<AppConfig, true>;
    return new SourceCryptoService(config);
  }

  function mockPrisma(opts: {
    sourceCreated?: Source;
    sourceFound?: Source | null;
    profileFound?: { id: string } | null;
    credentialFound?: SourceCredential | null;
  } = {}): {
    source: { create: jest.Mock; update: jest.Mock; findFirst: jest.Mock; findMany: jest.Mock };
    sourceCredential: { create: jest.Mock; findUnique: jest.Mock };
    profile: { findFirst: jest.Mock };
    $transaction: jest.Mock;
  } {
    const created = opts.sourceCreated ?? makeSource();
    const sourceCreate = jest.fn().mockResolvedValue(created);
    const credentialCreate = jest.fn().mockResolvedValue({ sourceId: created.id });
    return {
      source: {
        create: sourceCreate,
        update: jest.fn().mockResolvedValue(created),
        findFirst: jest
          .fn()
          .mockResolvedValue('sourceFound' in opts ? opts.sourceFound : created),
        findMany: jest.fn().mockResolvedValue([]),
      },
      sourceCredential: {
        create: credentialCreate,
        findUnique: jest.fn().mockResolvedValue(opts.credentialFound ?? null),
      },
      profile: {
        findFirst: jest.fn().mockResolvedValue(opts.profileFound ?? null),
      },
      $transaction: jest.fn(async (cb: (tx: unknown) => unknown) =>
        cb({
          source: { create: sourceCreate },
          sourceCredential: { create: credentialCreate },
        }),
      ),
    };
  }

  function mockEnt(state: Entitlement['state']): jest.Mocked<Pick<EntitlementService, 'getOrInitialize'>> {
    return {
      getOrInitialize: jest.fn().mockResolvedValue(makeEnt(state)),
    } as jest.Mocked<Pick<EntitlementService, 'getOrInitialize'>>;
  }

  describe('create', () => {
    it('encrypts URL/username/password/headers and persists with kms metadata', async () => {
      const prisma = mockPrisma();
      const crypto = realCrypto();
      const svc = new SourceService(
        prisma as unknown as PrismaService,
        mockEnt('lifetime_family') as unknown as EntitlementService,
        crypto,
      );

      await svc.create(account, {
        name: 'Provider',
        kind: 'm3u_plus_epg',
        url: 'https://example.com/playlist.m3u',
        username: 'alice',
        password: 'sup3r$ecret',
        headers: { 'X-Token': 'abc123' },
      });

      const credCall = prisma.sourceCredential.create.mock.calls[0][0].data;
      expect(credCall.encryptedUrl).toBeInstanceOf(Buffer);
      // None of the encrypted blobs should equal the plaintext bytes.
      expect(credCall.encryptedUrl.toString('utf8')).not.toContain('example.com');
      expect(credCall.encryptedUsername.toString('utf8')).not.toContain('alice');
      expect(credCall.encryptedPassword.toString('utf8')).not.toContain('sup3r');
      expect(credCall.encryptedHeaders.toString('utf8')).not.toContain('abc123');
      expect(credCall.kmsKeyId).toBe('local-test');
      expect(credCall.encryptionVersion).toBe(1);

      // Round-trip via decryptCredentials should recover the plaintexts.
      const decrypted = svc.decryptCredentials({
        sourceId: 's1',
        encryptedUrl: credCall.encryptedUrl,
        encryptedUsername: credCall.encryptedUsername,
        encryptedPassword: credCall.encryptedPassword,
        encryptedHeaders: credCall.encryptedHeaders,
        kmsKeyId: 'local-test',
        encryptionVersion: 1,
        createdAt: now,
        updatedAt: now,
      });
      expect(decrypted.url).toBe('https://example.com/playlist.m3u');
      expect(decrypted.username).toBe('alice');
      expect(decrypted.password).toBe('sup3r$ecret');
      expect(decrypted.headers).toEqual({ 'X-Token': 'abc123' });
    });

    it('omits encrypted blobs when fields are not provided', async () => {
      const prisma = mockPrisma();
      const svc = new SourceService(
        prisma as unknown as PrismaService,
        mockEnt('lifetime_family') as unknown as EntitlementService,
        realCrypto(),
      );
      await svc.create(account, {
        name: 'Bare',
        kind: 'm3u',
        url: 'https://x.example/p.m3u',
      });
      const credCall = prisma.sourceCredential.create.mock.calls[0][0].data;
      expect(credCall.encryptedUsername).toBeNull();
      expect(credCall.encryptedPassword).toBeNull();
      expect(credCall.encryptedHeaders).toBeNull();
    });

    it('returns 402 ENTITLEMENT_REQUIRED on entitlement that does not allow playback', async () => {
      const prisma = mockPrisma();
      const svc = new SourceService(
        prisma as unknown as PrismaService,
        mockEnt('expired') as unknown as EntitlementService,
        realCrypto(),
      );
      try {
        await svc.create(account, { name: 'X', kind: 'm3u', url: 'https://x/p.m3u' });
        fail('expected HttpException');
      } catch (e) {
        expect(e).toBeInstanceOf(HttpException);
        expect((e as HttpException).getStatus()).toBe(HttpStatus.PAYMENT_REQUIRED);
      }
      expect(prisma.source.create).not.toHaveBeenCalled();
    });

    it('rejects profileId that does not belong to the caller (404)', async () => {
      const prisma = mockPrisma({ profileFound: null });
      const svc = new SourceService(
        prisma as unknown as PrismaService,
        mockEnt('lifetime_family') as unknown as EntitlementService,
        realCrypto(),
      );
      await expect(
        svc.create(account, {
          profileId: 'someone-elses',
          name: 'X',
          kind: 'm3u',
          url: 'https://x/p.m3u',
        }),
      ).rejects.toBeInstanceOf(NotFoundException);
    });
  });

  describe('softDelete', () => {
    it('marks deleted_at and isActive=false', async () => {
      const existing = makeSource();
      const prisma = mockPrisma({ sourceFound: existing });
      const svc = new SourceService(
        prisma as unknown as PrismaService,
        mockEnt('lifetime_family') as unknown as EntitlementService,
        realCrypto(),
      );
      await svc.softDelete(account, existing.id);
      const updateArgs = prisma.source.update.mock.calls[0][0];
      expect(updateArgs.where).toEqual({ id: existing.id });
      expect(updateArgs.data.deletedAt).toBeInstanceOf(Date);
      expect(updateArgs.data.isActive).toBe(false);
    });

    it('throws 404 when not owned', async () => {
      const prisma = mockPrisma({ sourceFound: null });
      const svc = new SourceService(
        prisma as unknown as PrismaService,
        mockEnt('lifetime_family') as unknown as EntitlementService,
        realCrypto(),
      );
      await expect(svc.softDelete(account, 'nope')).rejects.toBeInstanceOf(NotFoundException);
    });
  });
});

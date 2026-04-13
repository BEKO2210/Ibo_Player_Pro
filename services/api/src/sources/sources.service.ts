import {
  HttpException,
  HttpStatus,
  Injectable,
  Logger,
  NotFoundException,
} from '@nestjs/common';
import type { Account, Source, SourceCredential, SourceKind } from '@prisma/client';
import { PrismaService } from '../prisma/prisma.service';
import { EntitlementService } from '../entitlement/entitlement.service';
import { allowsPlayback } from '../entitlement/entitlement.state-machine';
import { buildErrorEnvelope, ErrorCode } from '../common/errors';
import { SourceCryptoService } from './source-crypto.service';

export interface CreateSourceInput {
  profileId?: string;
  name: string;
  kind: SourceKind;
  url: string;
  username?: string;
  password?: string;
  headers?: Record<string, string>;
}

export interface UpdateSourceInput {
  name?: string;
  isActive?: boolean;
}

export interface DecryptedSourceCredentials {
  url: string;
  username: string | null;
  password: string | null;
  headers: Record<string, string> | null;
}

@Injectable()
export class SourceService {
  private readonly logger = new Logger(SourceService.name);

  constructor(
    private readonly prisma: PrismaService,
    private readonly entitlement: EntitlementService,
    private readonly crypto: SourceCryptoService,
  ) {}

  async listForAccount(accountId: string, profileId?: string): Promise<Source[]> {
    return this.prisma.source.findMany({
      where: {
        accountId,
        deletedAt: null,
        ...(profileId ? { profileId } : {}),
      },
      orderBy: { createdAt: 'asc' },
    });
  }

  async create(account: Account, input: CreateSourceInput): Promise<Source> {
    const ent = await this.entitlement.getOrInitialize(account.id);
    if (!allowsPlayback(ent.state)) {
      throw new HttpException(
        buildErrorEnvelope(
          ErrorCode.ENTITLEMENT_REQUIRED,
          `Source creation requires an active entitlement (current state: ${ent.state}).`,
        ),
        HttpStatus.PAYMENT_REQUIRED,
      );
    }

    if (input.profileId) {
      const profile = await this.prisma.profile.findFirst({
        where: { id: input.profileId, accountId: account.id, deletedAt: null },
      });
      if (!profile) {
        throw new NotFoundException('profileId does not belong to this account.');
      }
    }

    const encryptedUrl = this.crypto.encrypt(input.url);
    const encryptedUsername = this.crypto.encryptOptional(input.username);
    const encryptedPassword = this.crypto.encryptOptional(input.password);
    const encryptedHeaders = this.crypto.encryptOptional(
      input.headers ? JSON.stringify(input.headers) : null,
    );

    const created = await this.prisma.$transaction(async (tx) => {
      const source = await tx.source.create({
        data: {
          accountId: account.id,
          profileId: input.profileId ?? null,
          name: input.name,
          kind: input.kind,
          isActive: true,
          validationStatus: 'pending',
        },
      });
      await tx.sourceCredential.create({
        data: {
          sourceId: source.id,
          encryptedUrl,
          encryptedUsername,
          encryptedPassword,
          encryptedHeaders,
          kmsKeyId: this.crypto.kmsKeyId,
          encryptionVersion: this.crypto.encryptionVersion,
        },
      });
      return source;
    });

    this.logger.log(
      `Created source ${created.id} for account=${account.id} (kind=${created.kind})`,
    );
    return created;
  }

  async update(
    account: Account,
    sourceId: string,
    input: UpdateSourceInput,
  ): Promise<Source> {
    await this.requireOwned(account.id, sourceId);
    return this.prisma.source.update({
      where: { id: sourceId },
      data: {
        ...(input.name !== undefined ? { name: input.name } : {}),
        ...(input.isActive !== undefined ? { isActive: input.isActive } : {}),
      },
    });
  }

  async softDelete(account: Account, sourceId: string): Promise<void> {
    await this.requireOwned(account.id, sourceId);
    await this.prisma.source.update({
      where: { id: sourceId },
      data: { deletedAt: new Date(), isActive: false },
    });
    this.logger.log(`Soft-deleted source ${sourceId} for account=${account.id}`);
  }

  /** Internal — used by parsers / EPG worker (Run 15+) to fetch credentials. */
  async getDecryptedCredentials(
    account: Account,
    sourceId: string,
  ): Promise<DecryptedSourceCredentials> {
    await this.requireOwned(account.id, sourceId);
    const cred = await this.prisma.sourceCredential.findUnique({
      where: { sourceId },
    });
    if (!cred) {
      throw new NotFoundException('Source credentials missing.');
    }
    return this.decryptCredentials(cred);
  }

  decryptCredentials(cred: SourceCredential): DecryptedSourceCredentials {
    return {
      url: this.crypto.decrypt(cred.encryptedUrl),
      username: this.crypto.decryptOptional(cred.encryptedUsername),
      password: this.crypto.decryptOptional(cred.encryptedPassword),
      headers: cred.encryptedHeaders
        ? (JSON.parse(this.crypto.decrypt(cred.encryptedHeaders)) as Record<string, string>)
        : null,
    };
  }

  async requireOwned(accountId: string, sourceId: string): Promise<Source> {
    const s = await this.prisma.source.findFirst({
      where: { id: sourceId, accountId, deletedAt: null },
    });
    if (!s) throw new NotFoundException('Source not found for this account.');
    return s;
  }
}

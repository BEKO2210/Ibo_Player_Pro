import {
  HttpException,
  HttpStatus,
  Injectable,
  Logger,
  NotFoundException,
} from '@nestjs/common';
import type { Account, Profile } from '@prisma/client';
import { PrismaService } from '../prisma/prisma.service';
import { EntitlementService } from '../entitlement/entitlement.service';
import { profileCapFor } from '../entitlement/entitlement.state-machine';
import { buildErrorEnvelope, ErrorCode } from '../common/errors';
import { PinService } from './pin.service';

export interface CreateProfileInput {
  name: string;
  isKids: boolean;
  ageLimit?: number;
  pin?: string;
  isDefault?: boolean;
}

export interface UpdateProfileInput {
  name?: string;
  ageLimit?: number;
  pin?: string;
  clearPin?: boolean;
  isDefault?: boolean;
}

@Injectable()
export class ProfileService {
  private readonly logger = new Logger(ProfileService.name);

  constructor(
    private readonly prisma: PrismaService,
    private readonly entitlement: EntitlementService,
    private readonly pin: PinService,
  ) {}

  async listForAccount(accountId: string): Promise<Profile[]> {
    return this.prisma.profile.findMany({
      where: { accountId, deletedAt: null },
      orderBy: [{ isDefault: 'desc' }, { createdAt: 'asc' }],
    });
  }

  async create(account: Account, input: CreateProfileInput): Promise<Profile> {
    const ent = await this.entitlement.getOrInitialize(account.id);
    const cap = profileCapFor(ent.state);
    if (cap === 0) {
      throw new HttpException(
        buildErrorEnvelope(
          ErrorCode.ENTITLEMENT_REQUIRED,
          `Profile creation requires an active entitlement (current state: ${ent.state}).`,
        ),
        HttpStatus.PAYMENT_REQUIRED,
      );
    }

    const active = await this.prisma.profile.count({
      where: { accountId: account.id, deletedAt: null },
    });
    if (active >= cap) {
      throw new HttpException(
        buildErrorEnvelope(
          ErrorCode.SLOT_FULL,
          `Profile cap reached for entitlement "${ent.state}" (max ${cap}).`,
          { activeProfiles: active, cap, state: ent.state },
        ),
        HttpStatus.CONFLICT,
      );
    }

    if (input.isKids && input.ageLimit === undefined) {
      // Kids profiles get a sensible default age cap (PG-12) when caller
      // omits one — easy override via update later.
      input.ageLimit = 12;
    }

    const shouldBeDefault = input.isDefault ?? active === 0;

    const profile = await this.prisma.$transaction(async (tx) => {
      if (shouldBeDefault) {
        await tx.profile.updateMany({
          where: { accountId: account.id, isDefault: true, deletedAt: null },
          data: { isDefault: false },
        });
      }
      return tx.profile.create({
        data: {
          accountId: account.id,
          name: input.name,
          isKids: input.isKids,
          ageLimit: input.ageLimit ?? null,
          isDefault: shouldBeDefault,
        },
      });
    });

    if (input.pin) {
      await this.pin.setPin(profile.id, input.pin);
    }

    this.logger.log(
      `Created profile ${profile.id} for account=${account.id} (kids=${profile.isKids}, default=${profile.isDefault}, cap=${active + 1}/${cap})`,
    );
    return profile;
  }

  async update(
    account: Account,
    profileId: string,
    input: UpdateProfileInput,
  ): Promise<Profile> {
    const existing = await this.requireOwned(account.id, profileId);

    const updated = await this.prisma.$transaction(async (tx) => {
      if (input.isDefault === true && !existing.isDefault) {
        await tx.profile.updateMany({
          where: { accountId: account.id, isDefault: true, deletedAt: null },
          data: { isDefault: false },
        });
      }
      return tx.profile.update({
        where: { id: profileId },
        data: {
          ...(input.name !== undefined ? { name: input.name } : {}),
          ...(input.ageLimit !== undefined ? { ageLimit: input.ageLimit } : {}),
          ...(input.isDefault !== undefined ? { isDefault: input.isDefault } : {}),
        },
      });
    });

    if (input.clearPin) {
      await this.pin.clearPin(profileId);
    } else if (input.pin) {
      await this.pin.setPin(profileId, input.pin);
    }

    return updated;
  }

  /**
   * Soft-delete a profile.
   *  - Refuses to delete the LAST remaining profile of an account.
   *  - When deleting the default profile, promotes the oldest remaining
   *    profile to default in the same transaction.
   */
  async softDelete(account: Account, profileId: string): Promise<void> {
    const existing = await this.requireOwned(account.id, profileId);

    await this.prisma.$transaction(async (tx) => {
      const remaining = await tx.profile.count({
        where: {
          accountId: account.id,
          deletedAt: null,
          id: { not: profileId },
        },
      });
      if (remaining === 0) {
        throw new HttpException(
          buildErrorEnvelope(
            ErrorCode.VALIDATION_ERROR,
            'Cannot delete the last remaining profile.',
          ),
          HttpStatus.CONFLICT,
        );
      }
      await tx.profile.update({
        where: { id: profileId },
        data: { deletedAt: new Date(), isDefault: false },
      });
      if (existing.isDefault) {
        const promoted = await tx.profile.findFirst({
          where: { accountId: account.id, deletedAt: null },
          orderBy: { createdAt: 'asc' },
        });
        if (promoted) {
          await tx.profile.update({
            where: { id: promoted.id },
            data: { isDefault: true },
          });
        }
      }
    });

    await this.pin.clearPin(profileId);
    this.logger.log(`Soft-deleted profile ${profileId} for account=${account.id}`);
  }

  async requireOwned(accountId: string, profileId: string): Promise<Profile> {
    const p = await this.prisma.profile.findFirst({
      where: { id: profileId, accountId, deletedAt: null },
    });
    if (!p) {
      throw new NotFoundException('Profile not found for this account.');
    }
    return p;
  }
}

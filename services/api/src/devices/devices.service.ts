import {
  HttpException,
  HttpStatus,
  Injectable,
  Logger,
  NotFoundException,
} from '@nestjs/common';
import type { Account, Device, DevicePlatform } from '@prisma/client';
import { randomBytes, createHash } from 'node:crypto';
import { PrismaService } from '../prisma/prisma.service';
import { buildErrorEnvelope, ErrorCode } from '../common/errors';
import { EntitlementService } from '../entitlement/entitlement.service';
import { deviceCapFor } from '../entitlement/entitlement.state-machine';

export interface RegisterDeviceInput {
  name: string;
  platform: DevicePlatform;
  appVersion?: string;
  osVersion?: string;
  lastIp?: string;
}

export interface RegisteredDevice {
  device: Device;
  /**
   * Plaintext device token — returned to the client exactly once at
   * registration time; never stored or returned again. Subsequent requests
   * present this via `X-Device-Token` header.
   */
  deviceToken: string;
}

@Injectable()
export class DevicesService {
  private readonly logger = new Logger(DevicesService.name);

  constructor(
    private readonly prisma: PrismaService,
    private readonly entitlement: EntitlementService,
  ) {}

  /**
   * Register a new server-managed device slot for the account. Enforces the
   * entitlement-derived cap (1 for single/trial, 5 for family, 0 for none/
   * expired/revoked). Returns the plaintext deviceToken ONCE — only the
   * sha256 hash is persisted.
   */
  async register(account: Account, input: RegisterDeviceInput): Promise<RegisteredDevice> {
    const ent = await this.entitlement.getOrInitialize(account.id);
    const cap = deviceCapFor(ent.state);

    if (cap === 0) {
      throw new HttpException(
        buildErrorEnvelope(
          ErrorCode.ENTITLEMENT_REQUIRED,
          `Device registration requires an active entitlement (current state: ${ent.state}).`,
        ),
        HttpStatus.PAYMENT_REQUIRED,
      );
    }

    const active = await this.prisma.device.count({
      where: {
        accountId: account.id,
        revokedAt: null,
        deletedAt: null,
      },
    });

    if (active >= cap) {
      throw new HttpException(
        buildErrorEnvelope(
          ErrorCode.SLOT_FULL,
          `Device slot cap reached for entitlement "${ent.state}" (max ${cap}).`,
          { activeDevices: active, cap, state: ent.state },
        ),
        HttpStatus.CONFLICT,
      );
    }

    const plaintext = generateDeviceToken();
    const hash = hashDeviceToken(plaintext);

    const device = await this.prisma.device.create({
      data: {
        accountId: account.id,
        deviceTokenHash: hash,
        deviceName: input.name,
        platform: input.platform,
        appVersion: input.appVersion,
        osVersion: input.osVersion,
        lastIp: input.lastIp,
        lastSeenAt: new Date(),
      },
    });

    this.logger.log(
      `Registered device ${device.id} for account=${account.id} (platform=${device.platform}, cap=${active + 1}/${cap})`,
    );

    return { device, deviceToken: plaintext };
  }

  /**
   * List all devices for the account (including revoked/deleted? - no,
   * exclude soft-deleted; include revoked with isRevoked=true for UX).
   * `currentDeviceId` is used to mark `isCurrent` in the response.
   */
  async listForAccount(
    accountId: string,
    currentDeviceId?: string,
  ): Promise<Array<Device & { isCurrent: boolean; isRevoked: boolean }>> {
    const devices = await this.prisma.device.findMany({
      where: { accountId, deletedAt: null },
      orderBy: { createdAt: 'asc' },
    });
    return devices.map((d) => ({
      ...d,
      isCurrent: d.id === currentDeviceId,
      isRevoked: d.revokedAt !== null,
    }));
  }

  /**
   * Revoke a device slot owned by the given account. Throws 404 if the
   * device belongs to a different account or does not exist.
   */
  async revoke(accountId: string, deviceId: string): Promise<Device> {
    const existing = await this.prisma.device.findFirst({
      where: { id: deviceId, accountId, deletedAt: null },
    });
    if (!existing) {
      throw new NotFoundException('Device not found for this account.');
    }
    if (existing.revokedAt) return existing;

    const revoked = await this.prisma.device.update({
      where: { id: deviceId },
      data: { revokedAt: new Date() },
    });
    this.logger.log(`Revoked device ${deviceId} for account=${accountId}`);
    return revoked;
  }

  /**
   * Look up a device by its plaintext token (used by DeviceGuard).
   * Returns null if token doesn't match any active device slot.
   */
  async findByToken(token: string): Promise<Device | null> {
    if (!token) return null;
    const hash = hashDeviceToken(token);
    return this.prisma.device.findUnique({ where: { deviceTokenHash: hash } });
  }

  /** Touch `last_seen_at` — cheap, fire-and-forget from guards. */
  async touch(deviceId: string, lastIp?: string): Promise<void> {
    await this.prisma.device.update({
      where: { id: deviceId },
      data: { lastSeenAt: new Date(), ...(lastIp ? { lastIp } : {}) },
    });
  }
}

export function generateDeviceToken(): string {
  // 32 bytes -> 43-char base64url. Effectively 256 bits of entropy.
  return randomBytes(32).toString('base64url');
}

export function hashDeviceToken(token: string): string {
  return createHash('sha256').update(token, 'utf8').digest('hex');
}

import {
  CanActivate,
  ExecutionContext,
  HttpException,
  HttpStatus,
  Injectable,
  Logger,
} from '@nestjs/common';
import type { Device } from '@prisma/client';
import { buildErrorEnvelope, ErrorCode } from '../common/errors';
import { DevicesService } from './devices.service';
import type { AuthenticatedRequest } from '../auth/auth.guard';

export interface DeviceAuthenticatedRequest extends AuthenticatedRequest {
  device: Device;
}

/**
 * Requires a valid `X-Device-Token` header resolving to a non-revoked device
 * owned by the authenticated account. MUST be applied AFTER `AuthGuard` —
 * relies on `request.account` already being set.
 *
 * Used for endpoints that require an active device slot (playback, source
 * mutations, etc). Not applied yet to /v1/devices/* — those operate at the
 * account level.
 */
@Injectable()
export class DeviceGuard implements CanActivate {
  private readonly logger = new Logger(DeviceGuard.name);

  constructor(private readonly devices: DevicesService) {}

  async canActivate(context: ExecutionContext): Promise<boolean> {
    const req = context.switchToHttp().getRequest<DeviceAuthenticatedRequest>();
    if (!req.account) {
      // Defensive: DeviceGuard must run AFTER AuthGuard.
      throw this.unauthorized('Device guard invoked without authenticated account');
    }

    const raw = req.headers['x-device-token'];
    const token = Array.isArray(raw) ? raw[0] : raw;
    if (!token) {
      throw this.unauthorized('Missing X-Device-Token header');
    }

    const device = await this.devices.findByToken(token);
    if (!device || device.accountId !== req.account.id) {
      throw this.unauthorized('Unknown or mismatched device token');
    }
    if (device.revokedAt !== null) {
      throw this.unauthorized('Device token has been revoked');
    }
    if (device.deletedAt !== null) {
      throw this.unauthorized('Device has been deleted');
    }

    req.device = device;
    // Fire-and-forget touch — don't block on it.
    void this.devices
      .touch(device.id, typeof req.ip === 'string' ? req.ip : undefined)
      .catch((err) =>
        this.logger.warn(
          `Failed to touch device ${device.id}: ${
            err instanceof Error ? err.message : String(err)
          }`,
        ),
      );

    return true;
  }

  private unauthorized(message: string): HttpException {
    return new HttpException(
      buildErrorEnvelope(ErrorCode.UNAUTHORIZED, message),
      HttpStatus.UNAUTHORIZED,
    );
  }
}

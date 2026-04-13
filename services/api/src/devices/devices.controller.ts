import {
  Body,
  Controller,
  Get,
  HttpCode,
  HttpStatus,
  Param,
  ParseUUIDPipe,
  Post,
  Req,
  UseGuards,
} from '@nestjs/common';
import type { Device, DevicePlatform } from '@prisma/client';
import { AuthGuard, type AuthenticatedRequest } from '../auth/auth.guard';
import { DevicesService } from './devices.service';
import { RegisterDeviceDto } from './dto';

export interface DeviceView {
  id: string;
  name: string;
  platform: DevicePlatform;
  appVersion: string | null;
  osVersion: string | null;
  lastSeenAt: string | null;
  revokedAt: string | null;
  createdAt: string;
  isCurrent: boolean;
  isRevoked: boolean;
}

export interface RegisterDeviceResponse {
  device: DeviceView;
  /** Plaintext token — MUST be stored by the client; never returned again. */
  deviceToken: string;
}

export interface ListDevicesResponse {
  devices: DeviceView[];
}

@Controller({ path: 'devices' })
@UseGuards(AuthGuard)
export class DevicesController {
  constructor(private readonly devices: DevicesService) {}

  @Post('register')
  @HttpCode(HttpStatus.CREATED)
  async register(
    @Req() req: AuthenticatedRequest,
    @Body() body: RegisterDeviceDto,
  ): Promise<RegisterDeviceResponse> {
    const { device, deviceToken } = await this.devices.register(req.account, {
      name: body.name,
      platform: body.platform,
      appVersion: body.appVersion,
      osVersion: body.osVersion,
      lastIp: body.lastIp ?? (typeof req.ip === 'string' ? req.ip : undefined),
    });
    return {
      device: toView(device, device.id),
      deviceToken,
    };
  }

  @Get()
  async list(@Req() req: AuthenticatedRequest): Promise<ListDevicesResponse> {
    const items = await this.devices.listForAccount(req.account.id);
    return {
      devices: items.map((d) => toView(d, null, d.isCurrent, d.isRevoked)),
    };
  }

  @Post(':id/revoke')
  @HttpCode(HttpStatus.OK)
  async revoke(
    @Req() req: AuthenticatedRequest,
    @Param('id', new ParseUUIDPipe()) id: string,
  ): Promise<{ device: DeviceView }> {
    const device = await this.devices.revoke(req.account.id, id);
    return { device: toView(device, null, false, true) };
  }
}

function toView(
  device: Device,
  currentDeviceId?: string | null,
  isCurrent?: boolean,
  isRevoked?: boolean,
): DeviceView {
  return {
    id: device.id,
    name: device.deviceName,
    platform: device.platform,
    appVersion: device.appVersion,
    osVersion: device.osVersion,
    lastSeenAt: device.lastSeenAt?.toISOString() ?? null,
    revokedAt: device.revokedAt?.toISOString() ?? null,
    createdAt: device.createdAt.toISOString(),
    isCurrent:
      isCurrent ??
      (currentDeviceId !== null && currentDeviceId !== undefined
        ? device.id === currentDeviceId
        : false),
    isRevoked: isRevoked ?? device.revokedAt !== null,
  };
}

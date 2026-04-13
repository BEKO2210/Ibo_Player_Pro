import {
  Body,
  Controller,
  Delete,
  Get,
  HttpCode,
  HttpStatus,
  Param,
  ParseUUIDPipe,
  Post,
  Put,
  Req,
  UseGuards,
} from '@nestjs/common';
import type { Profile } from '@prisma/client';
import { AuthGuard, type AuthenticatedRequest } from '../auth/auth.guard';
import { ProfileService } from './profiles.service';
import { PinService } from './pin.service';
import { CreateProfileDto, UpdateProfileDto, VerifyPinDto } from './dto';

export interface ProfileView {
  id: string;
  name: string;
  isKids: boolean;
  ageLimit: number | null;
  isDefault: boolean;
  hasPin: boolean;
  createdAt: string;
}

@Controller({ path: 'profiles' })
@UseGuards(AuthGuard)
export class ProfilesController {
  constructor(
    private readonly profiles: ProfileService,
    private readonly pin: PinService,
    // PrismaService injection only used for the hasPin lookup; small enough
    // not to warrant a dedicated method on the service.
  ) {}

  @Get()
  async list(
    @Req() req: AuthenticatedRequest,
  ): Promise<{ profiles: ProfileView[] }> {
    const items = await this.profiles.listForAccount(req.account.id);
    const views = await Promise.all(items.map((p) => this.toView(p)));
    return { profiles: views };
  }

  @Post()
  @HttpCode(HttpStatus.CREATED)
  async create(
    @Req() req: AuthenticatedRequest,
    @Body() body: CreateProfileDto,
  ): Promise<{ profile: ProfileView }> {
    const created = await this.profiles.create(req.account, {
      name: body.name,
      isKids: body.isKids,
      ageLimit: body.ageLimit,
      pin: body.pin,
      isDefault: body.isDefault,
    });
    return { profile: await this.toView(created) };
  }

  @Put(':id')
  async update(
    @Req() req: AuthenticatedRequest,
    @Param('id', new ParseUUIDPipe()) id: string,
    @Body() body: UpdateProfileDto,
  ): Promise<{ profile: ProfileView }> {
    const updated = await this.profiles.update(req.account, id, body);
    return { profile: await this.toView(updated) };
  }

  @Delete(':id')
  @HttpCode(HttpStatus.NO_CONTENT)
  async remove(
    @Req() req: AuthenticatedRequest,
    @Param('id', new ParseUUIDPipe()) id: string,
  ): Promise<void> {
    await this.profiles.softDelete(req.account, id);
  }

  @Post(':id/verify-pin')
  @HttpCode(HttpStatus.OK)
  async verifyPin(
    @Req() req: AuthenticatedRequest,
    @Param('id', new ParseUUIDPipe()) id: string,
    @Body() body: VerifyPinDto,
  ): Promise<{
    ok: boolean;
    reason?: string;
    failedAttemptCount?: number;
    lockedUntil?: string | null;
  }> {
    await this.profiles.requireOwned(req.account.id, id);
    const result = await this.pin.verify(id, body.pin);
    if (result.ok) return { ok: true };
    if (result.reason === 'no_pin') {
      return { ok: false, reason: 'no_pin' };
    }
    if (result.reason === 'locked') {
      return {
        ok: false,
        reason: 'locked',
        lockedUntil: result.lockedUntil.toISOString(),
      };
    }
    return {
      ok: false,
      reason: 'mismatch',
      failedAttemptCount: result.failedAttemptCount,
      lockedUntil: result.lockedUntil ? result.lockedUntil.toISOString() : null,
    };
  }

  private async toView(p: Profile): Promise<ProfileView> {
    const hasPin = await this.pin.hasPin(p.id);
    return {
      id: p.id,
      name: p.name,
      isKids: p.isKids,
      ageLimit: p.ageLimit,
      isDefault: p.isDefault,
      hasPin,
      createdAt: p.createdAt.toISOString(),
    };
  }
}

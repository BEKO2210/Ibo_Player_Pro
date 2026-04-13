import {
  Controller,
  Get,
  HttpCode,
  HttpStatus,
  Post,
  Req,
  UseGuards,
} from '@nestjs/common';
import { AuthGuard, type AuthenticatedRequest } from '../auth/auth.guard';
import { EntitlementService } from './entitlement.service';

export interface EntitlementSnapshotResponse {
  entitlement: {
    state: string;
    trialStartedAt: string | null;
    trialEndsAt: string | null;
    activatedAt: string | null;
    expiresAt: string | null;
    revokedAt: string | null;
  };
}

@Controller({ path: 'entitlement' })
@UseGuards(AuthGuard)
export class EntitlementController {
  constructor(private readonly entitlement: EntitlementService) {}

  @Get('status')
  async status(
    @Req() req: AuthenticatedRequest,
  ): Promise<EntitlementSnapshotResponse> {
    const ent = await this.entitlement.getOrInitialize(req.account.id);
    return serialize(ent);
  }

  @Post('trial/start')
  @HttpCode(HttpStatus.OK)
  async startTrial(
    @Req() req: AuthenticatedRequest,
  ): Promise<EntitlementSnapshotResponse> {
    const ent = await this.entitlement.startTrial(req.account);
    return serialize(ent);
  }
}

function serialize(ent: {
  state: string;
  trialStartedAt: Date | null;
  trialEndsAt: Date | null;
  activatedAt: Date | null;
  expiresAt: Date | null;
  revokedAt: Date | null;
}): EntitlementSnapshotResponse {
  return {
    entitlement: {
      state: ent.state,
      trialStartedAt: ent.trialStartedAt?.toISOString() ?? null,
      trialEndsAt: ent.trialEndsAt?.toISOString() ?? null,
      activatedAt: ent.activatedAt?.toISOString() ?? null,
      expiresAt: ent.expiresAt?.toISOString() ?? null,
      revokedAt: ent.revokedAt?.toISOString() ?? null,
    },
  };
}

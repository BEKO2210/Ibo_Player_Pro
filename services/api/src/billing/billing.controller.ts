import {
  Body,
  Controller,
  HttpCode,
  HttpStatus,
  Post,
  Req,
  UseGuards,
} from '@nestjs/common';
import type { Entitlement } from '@prisma/client';
import { AuthGuard, type AuthenticatedRequest } from '../auth/auth.guard';
import { BillingService } from './billing.service';
import { VerifyPurchaseDto } from './dto';

export interface EntitlementStatusResponse {
  entitlement: {
    state: string;
    trialStartedAt: string | null;
    trialEndsAt: string | null;
    activatedAt: string | null;
    expiresAt: string | null;
    revokedAt: string | null;
  };
}

@Controller({ path: 'billing' })
@UseGuards(AuthGuard)
export class BillingController {
  constructor(private readonly billing: BillingService) {}

  @Post('verify')
  @HttpCode(HttpStatus.OK)
  async verify(
    @Req() req: AuthenticatedRequest,
    @Body() body: VerifyPurchaseDto,
  ): Promise<EntitlementStatusResponse> {
    const result = await this.billing.verifyAndApply(req.account, {
      purchaseToken: body.purchaseToken,
      productId: body.productId,
    });
    return serialize(result.entitlement);
  }

  @Post('restore')
  @HttpCode(HttpStatus.OK)
  async restore(
    @Req() req: AuthenticatedRequest,
  ): Promise<EntitlementStatusResponse> {
    const ent = await this.billing.restore(req.account);
    return serialize(ent);
  }
}

function serialize(ent: Entitlement): EntitlementStatusResponse {
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

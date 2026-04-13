import { Body, Controller, HttpCode, HttpStatus, Post } from '@nestjs/common';
import { AuthService } from './auth.service';
import type { AccountSnapshot } from './accounts.service';
import { LoginDto, RefreshDto, RegisterDto } from './dto';

/**
 * Auth endpoints — server-side sync of Firebase-authenticated callers.
 *
 * Response shape for all three endpoints is `AccountSnapshotResponse`:
 *   { account: Account, entitlement: Entitlement }
 *
 * This deviates from the current OpenAPI `AuthResponse` (which includes
 * custom access/refresh/device tokens). Token issuance + device slots land
 * in Run 8 (Devices + Entitlement) and the OpenAPI is reconciled there.
 */
@Controller({ path: 'auth' })
export class AuthController {
  constructor(private readonly auth: AuthService) {}

  @Post('register')
  @HttpCode(HttpStatus.OK)
  async register(@Body() body: RegisterDto): Promise<AccountSnapshotResponse> {
    const snap = await this.auth.register(body.firebaseIdToken, body.locale);
    return serialize(snap);
  }

  @Post('login')
  @HttpCode(HttpStatus.OK)
  async login(@Body() body: LoginDto): Promise<AccountSnapshotResponse> {
    const snap = await this.auth.login(body.firebaseIdToken, body.locale);
    return serialize(snap);
  }

  @Post('refresh')
  @HttpCode(HttpStatus.OK)
  async refresh(@Body() body: RefreshDto): Promise<AccountSnapshotResponse> {
    const snap = await this.auth.refresh(body.firebaseIdToken);
    return serialize(snap);
  }
}

interface AccountSnapshotResponse {
  account: {
    id: string;
    email: string;
    emailVerified: boolean;
    locale: string;
    createdAt: string;
  };
  entitlement: {
    state: string;
    trialEndsAt: string | null;
    expiresAt: string | null;
  };
}

function serialize(snap: AccountSnapshot): AccountSnapshotResponse {
  return {
    account: {
      id: snap.account.id,
      email: snap.account.email,
      emailVerified: snap.account.emailVerified,
      locale: snap.account.locale,
      createdAt: snap.account.createdAt.toISOString(),
    },
    entitlement: {
      state: snap.entitlement.state,
      trialEndsAt: snap.entitlement.trialEndsAt?.toISOString() ?? null,
      expiresAt: snap.entitlement.expiresAt?.toISOString() ?? null,
    },
  };
}

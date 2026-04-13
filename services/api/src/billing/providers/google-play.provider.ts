import { Injectable, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { GoogleAuth, type JWT } from 'google-auth-library';
import type { AppConfig } from '../../config/configuration';
import {
  purchaseStateColumn,
  type NormalizedPurchaseState,
  type ProviderVerificationClient,
  type VerifiedPurchase,
} from './provider.interface';

/**
 * Production Google Play Developer API implementation.
 *
 * Uses the "purchases.products.get" endpoint for one-time products (Premium TV
 * Player only sells one-time SKUs — `lifetime_single` and `lifetime_family`).
 *
 *   GET https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{pkg}/purchases/products/{sku}/tokens/{token}
 *
 * The response shape documented by Google:
 *   {
 *     "purchaseTimeMillis": "1712000000000",
 *     "purchaseState": 0,        // 0=purchased, 1=canceled, 2=pending
 *     "consumptionState": 0,
 *     "developerPayload": "...",
 *     "orderId": "GPA.xxxx-xxxx-xxxx-xxxxx",
 *     "acknowledgementState": 0, // 0=not acked, 1=acked
 *     "productId": "lifetime_family",
 *     "purchaseToken": "...",
 *     "refundableQuantity": 0    // non-zero when partially refunded
 *   }
 */
@Injectable()
export class GooglePlayProvider implements ProviderVerificationClient {
  private readonly logger = new Logger(GooglePlayProvider.name);
  private authClient: JWT | null = null;

  constructor(private readonly config: ConfigService<AppConfig, true>) {}

  private requirePackageName(): string {
    const pkg = this.config.get('billing.androidPackageName', { infer: true });
    if (!pkg) {
      throw new Error(
        'BILLING_ANDROID_PACKAGE_NAME is not set; Google Play provider cannot verify purchases.',
      );
    }
    return pkg;
  }

  private async getAuthedClient(): Promise<JWT> {
    if (this.authClient) return this.authClient;

    const creds = this.config.get('firebase.credentials', { infer: true });
    this.requirePackageName();
    if (!creds) {
      throw new Error(
        'Google Play provider is not configured. Reuses Firebase service-account credentials (need androidpublisher scope) and BILLING_ANDROID_PACKAGE_NAME.',
      );
    }

    const auth = new GoogleAuth({
      credentials: {
        client_email: creds.clientEmail,
        private_key: creds.privateKey,
      },
      scopes: ['https://www.googleapis.com/auth/androidpublisher'],
    });
    this.authClient = (await auth.getClient()) as JWT;
    return this.authClient;
  }

  async verify(args: {
    productId: string;
    purchaseToken: string;
  }): Promise<VerifiedPurchase> {
    const pkg = this.requirePackageName();
    const url =
      `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/` +
      `${encodeURIComponent(pkg)}/purchases/products/` +
      `${encodeURIComponent(args.productId)}/tokens/` +
      `${encodeURIComponent(args.purchaseToken)}`;

    const client = await this.getAuthedClient();
    const res = await client.request<Record<string, unknown>>({
      url,
      method: 'GET',
    });
    const body = res.data ?? {};

    const state = mapGoogleState(body);
    const purchasedAtMs = Number(body['purchaseTimeMillis'] ?? 0);
    const purchasedAt = Number.isFinite(purchasedAtMs) && purchasedAtMs > 0
      ? new Date(purchasedAtMs)
      : null;

    return {
      provider: 'google_play',
      productId: args.productId,
      purchaseToken: args.purchaseToken,
      orderId: typeof body['orderId'] === 'string' ? (body['orderId'] as string) : null,
      state,
      purchasedAt,
      acknowledged: Number(body['acknowledgementState'] ?? 0) === 1,
      raw: body,
    };
  }

  async acknowledge(args: {
    productId: string;
    purchaseToken: string;
  }): Promise<void> {
    const pkg = this.requirePackageName();
    const url =
      `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/` +
      `${encodeURIComponent(pkg)}/purchases/products/` +
      `${encodeURIComponent(args.productId)}/tokens/` +
      `${encodeURIComponent(args.purchaseToken)}:acknowledge`;

    const client = await this.getAuthedClient();
    await client.request({ url, method: 'POST', data: {} });
    this.logger.log(
      `Acknowledged Google Play purchase product=${args.productId} tokenPrefix=${args.purchaseToken.slice(0, 8)}...`,
    );
  }
}

function mapGoogleState(body: Record<string, unknown>): NormalizedPurchaseState {
  const purchaseState = Number(body['purchaseState'] ?? 0);
  const refundableQty = Number(body['refundableQuantity'] ?? 0);

  if (purchaseState === 1) {
    // canceled / refunded. Google's SubscriptionsV2 has richer signals; for
    // one-time products we conservatively treat this as refunded.
    return 'refunded';
  }
  if (purchaseState === 2) {
    return 'pending';
  }
  if (refundableQty === 0 && body['orderId'] && purchaseState === 0) {
    return 'purchased';
  }
  return 'purchased';
}

// Re-export for consumers that want the DB-column helper.
export { purchaseStateColumn };

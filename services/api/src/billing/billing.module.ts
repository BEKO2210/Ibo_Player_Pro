import { Module } from '@nestjs/common';
import { AuthModule } from '../auth/auth.module';
import { EntitlementModule } from '../entitlement/entitlement.module';
import { BillingController } from './billing.controller';
import { BillingService } from './billing.service';
import { GooglePlayProvider } from './providers/google-play.provider';
import { PROVIDER_VERIFICATION_CLIENT } from './providers/provider.interface';

@Module({
  imports: [AuthModule, EntitlementModule],
  controllers: [BillingController],
  providers: [
    BillingService,
    GooglePlayProvider,
    {
      provide: PROVIDER_VERIFICATION_CLIENT,
      useExisting: GooglePlayProvider,
    },
  ],
  exports: [BillingService, PROVIDER_VERIFICATION_CLIENT],
})
export class BillingModule {}

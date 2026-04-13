import { Module } from '@nestjs/common';
import { AuthModule } from '../auth/auth.module';
import { EntitlementModule } from '../entitlement/entitlement.module';
import { ProfilesController } from './profiles.controller';
import { ProfileService } from './profiles.service';
import { PinService } from './pin.service';

@Module({
  imports: [AuthModule, EntitlementModule],
  controllers: [ProfilesController],
  providers: [ProfileService, PinService],
  exports: [ProfileService, PinService],
})
export class ProfilesModule {}

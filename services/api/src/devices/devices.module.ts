import { Module } from '@nestjs/common';
import { AuthModule } from '../auth/auth.module';
import { EntitlementModule } from '../entitlement/entitlement.module';
import { DevicesController } from './devices.controller';
import { DevicesService } from './devices.service';
import { DeviceGuard } from './device.guard';

@Module({
  imports: [AuthModule, EntitlementModule],
  controllers: [DevicesController],
  providers: [DevicesService, DeviceGuard],
  exports: [DevicesService, DeviceGuard],
})
export class DevicesModule {}

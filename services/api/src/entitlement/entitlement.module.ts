import { Module } from '@nestjs/common';
import { AuthModule } from '../auth/auth.module';
import { EntitlementController } from './entitlement.controller';
import { EntitlementService } from './entitlement.service';

@Module({
  imports: [AuthModule],
  controllers: [EntitlementController],
  providers: [EntitlementService],
  exports: [EntitlementService],
})
export class EntitlementModule {}

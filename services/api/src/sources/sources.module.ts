import { Module } from '@nestjs/common';
import { AuthModule } from '../auth/auth.module';
import { EntitlementModule } from '../entitlement/entitlement.module';
import { SourcesController } from './sources.controller';
import { SourceService } from './sources.service';
import { SourceCryptoService } from './source-crypto.service';

@Module({
  imports: [AuthModule, EntitlementModule],
  controllers: [SourcesController],
  providers: [SourceService, SourceCryptoService],
  exports: [SourceService, SourceCryptoService],
})
export class SourcesModule {}

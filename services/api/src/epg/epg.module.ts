import { Module } from '@nestjs/common';
import { AuthModule } from '../auth/auth.module';
import { EpgController } from './epg.controller';
import { EpgService } from './epg.service';

@Module({
  imports: [AuthModule],
  controllers: [EpgController],
  providers: [EpgService],
  exports: [EpgService],
})
export class ApiEpgModule {}

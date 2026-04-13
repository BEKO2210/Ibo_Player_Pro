import { Module } from '@nestjs/common';
import { AccountsService } from './accounts.service';
import { AuthController } from './auth.controller';
import { AuthService } from './auth.service';
import { AuthGuard } from './auth.guard';

@Module({
  controllers: [AuthController],
  providers: [AuthService, AccountsService, AuthGuard],
  exports: [AccountsService, AuthGuard],
})
export class AuthModule {}

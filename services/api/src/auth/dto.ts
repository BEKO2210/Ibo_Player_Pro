import { IsOptional, IsString, MaxLength, MinLength } from 'class-validator';

export class FirebaseTokenDto {
  @IsString()
  @MinLength(20)
  @MaxLength(4096)
  firebaseIdToken!: string;

  @IsOptional()
  @IsString()
  @MaxLength(16)
  locale?: string;
}

export class RegisterDto extends FirebaseTokenDto {}
export class LoginDto extends FirebaseTokenDto {}
export class RefreshDto extends FirebaseTokenDto {}

import { IsNotEmpty, IsString, MaxLength } from 'class-validator';

export class VerifyPurchaseDto {
  @IsString()
  @IsNotEmpty()
  @MaxLength(4096)
  purchaseToken!: string;

  @IsString()
  @IsNotEmpty()
  @MaxLength(128)
  productId!: string;
}

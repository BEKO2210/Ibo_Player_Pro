import {
  IsBoolean,
  IsInt,
  IsOptional,
  IsString,
  Length,
  Matches,
  Max,
  MaxLength,
  Min,
  MinLength,
} from 'class-validator';

const PIN_REGEX = /^[0-9]{4,10}$/;

export class CreateProfileDto {
  @IsString()
  @MinLength(1)
  @MaxLength(50)
  name!: string;

  @IsBoolean()
  isKids!: boolean;

  @IsOptional()
  @IsInt()
  @Min(0)
  @Max(21)
  ageLimit?: number;

  @IsOptional()
  @IsString()
  @Matches(PIN_REGEX, { message: 'pin must be 4-10 digits' })
  pin?: string;

  @IsOptional()
  @IsBoolean()
  isDefault?: boolean;
}

export class UpdateProfileDto {
  @IsOptional()
  @IsString()
  @MinLength(1)
  @MaxLength(50)
  name?: string;

  @IsOptional()
  @IsInt()
  @Min(0)
  @Max(21)
  ageLimit?: number;

  @IsOptional()
  @IsString()
  @Matches(PIN_REGEX, { message: 'pin must be 4-10 digits' })
  pin?: string;

  /** Pass `null` to remove the existing PIN. */
  @IsOptional()
  clearPin?: boolean;

  @IsOptional()
  @IsBoolean()
  isDefault?: boolean;
}

export class VerifyPinDto {
  @IsString()
  @Length(4, 10)
  @Matches(PIN_REGEX)
  pin!: string;
}

import { Inject, Injectable } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { createCipheriv, createDecipheriv, randomBytes } from 'node:crypto';
import type { AppConfig } from '../config/configuration';

/**
 * AES-256-GCM envelope encryption for `source_credentials` fields.
 *
 * Wire format (per encrypted blob, stored as a single BYTEA column):
 *
 *   [ version: u8 ][ iv: 12 bytes ][ tag: 16 bytes ][ ciphertext: ... ]
 *
 * - `version` = 1 today; bumped when key rotation requires a different
 *   layout.
 * - `iv` (nonce) is fresh per blob, never reused.
 * - GCM `tag` is 16 bytes (default).
 *
 * The KMS key id is metadata stored on the row (`kms_key_id` column) so we
 * can rotate keys later without losing audit trail.
 */
@Injectable()
export class SourceCryptoService {
  private static readonly VERSION = 1;
  private static readonly IV_LEN = 12;
  private static readonly TAG_LEN = 16;

  constructor(
    @Inject(ConfigService)
    private readonly config: ConfigService<AppConfig, true>,
  ) {}

  get kmsKeyId(): string {
    return this.config.get('sourceCrypto.kmsKeyId', { infer: true });
  }

  get encryptionVersion(): number {
    return SourceCryptoService.VERSION;
  }

  isConfigured(): boolean {
    return this.config.get('sourceCrypto.key', { infer: true }) !== null;
  }

  encrypt(plaintext: string): Buffer {
    const key = this.requireKey();
    const iv = randomBytes(SourceCryptoService.IV_LEN);
    const cipher = createCipheriv('aes-256-gcm', key, iv);
    const ct = Buffer.concat([cipher.update(plaintext, 'utf8'), cipher.final()]);
    const tag = cipher.getAuthTag();
    const version = Buffer.from([SourceCryptoService.VERSION]);
    return Buffer.concat([version, iv, tag, ct]);
  }

  decrypt(blob: Buffer): string {
    if (blob.length < 1 + SourceCryptoService.IV_LEN + SourceCryptoService.TAG_LEN) {
      throw new Error('Encrypted blob is too short to be valid.');
    }
    const version = blob[0];
    if (version !== SourceCryptoService.VERSION) {
      throw new Error(`Unsupported encryption version: ${version}`);
    }
    const key = this.requireKey();
    const iv = blob.subarray(1, 1 + SourceCryptoService.IV_LEN);
    const tagStart = 1 + SourceCryptoService.IV_LEN;
    const tag = blob.subarray(tagStart, tagStart + SourceCryptoService.TAG_LEN);
    const ct = blob.subarray(tagStart + SourceCryptoService.TAG_LEN);
    const decipher = createDecipheriv('aes-256-gcm', key, iv);
    decipher.setAuthTag(tag);
    return Buffer.concat([decipher.update(ct), decipher.final()]).toString('utf8');
  }

  encryptOptional(plaintext: string | null | undefined): Buffer | null {
    if (plaintext === null || plaintext === undefined || plaintext === '') return null;
    return this.encrypt(plaintext);
  }

  decryptOptional(blob: Buffer | null | undefined): string | null {
    if (!blob) return null;
    return this.decrypt(blob);
  }

  private requireKey(): Buffer {
    const key = this.config.get('sourceCrypto.key', { infer: true });
    if (!key) {
      throw new Error(
        'SOURCE_ENCRYPTION_KEY is not configured. Set a 32-byte hex value (64 chars) in the env.',
      );
    }
    if (key.length !== 32) {
      throw new Error(
        `SOURCE_ENCRYPTION_KEY must be 32 bytes; got ${key.length}.`,
      );
    }
    return key;
  }
}

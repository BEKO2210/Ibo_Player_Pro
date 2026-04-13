import { randomBytes } from 'node:crypto';
import type { ConfigService } from '@nestjs/config';
import { SourceCryptoService } from './source-crypto.service';
import type { AppConfig } from '../config/configuration';

describe('SourceCryptoService', () => {
  function svcWithKey(key: Buffer | null = randomBytes(32)): SourceCryptoService {
    const config = {
      get: jest.fn((k: string) => {
        if (k === 'sourceCrypto.key') return key;
        if (k === 'sourceCrypto.kmsKeyId') return 'local-test';
        return undefined;
      }),
    } as unknown as ConfigService<AppConfig, true>;
    return new SourceCryptoService(config);
  }

  it('round-trips a UTF-8 string', () => {
    const svc = svcWithKey();
    const blob = svc.encrypt('https://provider.example/playlist.m3u');
    expect(blob.length).toBeGreaterThan(1 + 12 + 16);
    expect(blob[0]).toBe(1); // version byte
    expect(svc.decrypt(blob)).toBe('https://provider.example/playlist.m3u');
  });

  it('produces fresh ciphertext on every encrypt (random IV)', () => {
    const svc = svcWithKey();
    const a = svc.encrypt('same plaintext');
    const b = svc.encrypt('same plaintext');
    expect(Buffer.compare(a, b)).not.toBe(0);
    expect(svc.decrypt(a)).toBe('same plaintext');
    expect(svc.decrypt(b)).toBe('same plaintext');
  });

  it('rejects tampered ciphertext (GCM auth tag mismatch)', () => {
    const svc = svcWithKey();
    const blob = svc.encrypt('secret');
    // Flip a byte in the ciphertext region (after version+iv+tag).
    const tampered = Buffer.from(blob);
    tampered[tampered.length - 1] ^= 0x01;
    expect(() => svc.decrypt(tampered)).toThrow();
  });

  it('rejects unknown version byte', () => {
    const svc = svcWithKey();
    const blob = svc.encrypt('secret');
    const bad = Buffer.from(blob);
    bad[0] = 99;
    expect(() => svc.decrypt(bad)).toThrow(/Unsupported encryption version/);
  });

  it('refuses to encrypt without a configured key', () => {
    const svc = svcWithKey(null);
    expect(svc.isConfigured()).toBe(false);
    expect(() => svc.encrypt('anything')).toThrow(/SOURCE_ENCRYPTION_KEY is not configured/);
  });

  it('encryptOptional returns null for empty / undefined / null inputs', () => {
    const svc = svcWithKey();
    expect(svc.encryptOptional(null)).toBeNull();
    expect(svc.encryptOptional(undefined)).toBeNull();
    expect(svc.encryptOptional('')).toBeNull();
    expect(svc.encryptOptional('x')).toBeInstanceOf(Buffer);
  });

  it('decryptOptional handles null / Buffer paths', () => {
    const svc = svcWithKey();
    expect(svc.decryptOptional(null)).toBeNull();
    expect(svc.decryptOptional(undefined)).toBeNull();
    const blob = svc.encrypt('hi');
    expect(svc.decryptOptional(blob)).toBe('hi');
  });
});

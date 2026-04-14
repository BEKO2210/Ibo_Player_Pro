import { Inject, Injectable, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import * as argon2 from 'argon2';
import type { ProfilePin } from '@prisma/client';
import { PrismaService } from '../prisma/prisma.service';
import type { AppConfig } from '../config/configuration';

export type PinVerifyOutcome =
  | { ok: true }
  | { ok: false; reason: 'no_pin' }
  | { ok: false; reason: 'mismatch'; failedAttemptCount: number; lockedUntil: Date | null }
  | { ok: false; reason: 'locked'; lockedUntil: Date };

/**
 * Owns Argon2id PIN hash + verify lifecycle for `profile_pins`.
 *
 * Lockout policy (configurable):
 *   - PIN_MAX_FAILED_ATTEMPTS consecutive misses → lock for PIN_LOCKOUT_DURATION_MS
 *   - on lock, `lock_until` is set; subsequent verify calls return `locked`
 *     until the wall-clock crosses lock_until
 *   - any successful verify resets the counter and clears lock_until
 */
@Injectable()
export class PinService {
  private readonly logger = new Logger(PinService.name);

  constructor(
    private readonly prisma: PrismaService,
    @Inject(ConfigService)
    private readonly config: ConfigService<AppConfig, true>,
  ) {}

  /** Set or replace a profile's PIN. Resets failed-attempt + lockout state. */
  async setPin(profileId: string, plaintext: string): Promise<ProfilePin> {
    const hash = await argon2.hash(plaintext, { type: argon2.argon2id });
    return this.prisma.profilePin.upsert({
      where: { profileId },
      create: { profileId, pinHash: hash },
      update: {
        pinHash: hash,
        pinUpdatedAt: new Date(),
        failedAttemptCount: 0,
        lockUntil: null,
      },
    });
  }

  /** Remove a profile's PIN entirely. */
  async clearPin(profileId: string): Promise<void> {
    await this.prisma.profilePin.deleteMany({ where: { profileId } });
  }

  /** True when a PIN is currently set for the profile. */
  async hasPin(profileId: string): Promise<boolean> {
    const row = await this.prisma.profilePin.findUnique({
      where: { profileId },
      select: { profileId: true },
    });
    return row !== null;
  }

  /** Verify a PIN. Manages the failed-attempt counter + lockout window. */
  async verify(profileId: string, plaintext: string): Promise<PinVerifyOutcome> {
    const stored = await this.prisma.profilePin.findUnique({ where: { profileId } });
    if (!stored) return { ok: false, reason: 'no_pin' };

    const now = new Date();
    if (stored.lockUntil && stored.lockUntil.getTime() > now.getTime()) {
      return { ok: false, reason: 'locked', lockedUntil: stored.lockUntil };
    }

    const ok = await argon2.verify(stored.pinHash, plaintext).catch(() => false);
    if (ok) {
      // Reset counter on success.
      if (stored.failedAttemptCount > 0 || stored.lockUntil) {
        await this.prisma.profilePin.update({
          where: { profileId },
          data: { failedAttemptCount: 0, lockUntil: null },
        });
      }
      return { ok: true };
    }

    const max = this.config.get('pin.maxFailedAttempts', { infer: true });
    const lockMs = this.config.get('pin.lockoutDurationMs', { infer: true });
    const newCount = stored.failedAttemptCount + 1;
    const reachedLimit = newCount >= max;
    const lockUntil = reachedLimit ? new Date(now.getTime() + lockMs) : null;

    await this.prisma.profilePin.update({
      where: { profileId },
      data: {
        failedAttemptCount: reachedLimit ? 0 : newCount,
        lockUntil,
      },
    });

    if (reachedLimit) {
      this.logger.warn(
        `PIN locked for profile=${profileId} until ${lockUntil!.toISOString()}`,
      );
      return { ok: false, reason: 'mismatch', failedAttemptCount: newCount, lockedUntil: lockUntil };
    }
    return {
      ok: false,
      reason: 'mismatch',
      failedAttemptCount: newCount,
      lockedUntil: null,
    };
  }
}

import { HttpException, HttpStatus, type ExecutionContext } from '@nestjs/common';
import type { Account, Device } from '@prisma/client';
import { DeviceGuard, type DeviceAuthenticatedRequest } from './device.guard';
import type { DevicesService } from './devices.service';

describe('DeviceGuard', () => {
  const account: Account = {
    id: 'a1111111-1111-1111-1111-111111111111',
    firebaseUid: 'f',
    email: 'u@e.com',
    emailVerified: true,
    locale: 'en',
    trialConsumed: false,
    status: 'active',
    createdAt: new Date(),
    updatedAt: new Date(),
    deletedAt: null,
  };

  function makeDevice(overrides: Partial<Device> = {}): Device {
    return {
      id: 'd1',
      accountId: account.id,
      deviceTokenHash: 'h',
      deviceName: 'TV',
      platform: 'android_tv',
      appVersion: null,
      osVersion: null,
      lastIp: null,
      lastSeenAt: null,
      revokedAt: null,
      createdAt: new Date(),
      updatedAt: new Date(),
      deletedAt: null,
      ...overrides,
    };
  }

  function makeCtx(
    headers: Record<string, string>,
    acc: Account | null = account,
  ): ExecutionContext {
    const req: Partial<DeviceAuthenticatedRequest> = {
      headers: headers as never,
      ...(acc ? { account: acc } : {}),
    };
    return {
      switchToHttp: () => ({
        getRequest: () => req,
        getResponse: () => ({}),
      }),
    } as unknown as ExecutionContext;
  }

  function mockDevices(result: Device | null): {
    findByToken: jest.Mock;
    touch: jest.Mock;
  } {
    return {
      findByToken: jest.fn().mockResolvedValue(result),
      touch: jest.fn().mockResolvedValue(undefined),
    };
  }

  it('attaches req.device on success and fires touch in background', async () => {
    const device = makeDevice();
    const devices = mockDevices(device);
    const guard = new DeviceGuard(devices as unknown as DevicesService);
    const ctx = makeCtx({ 'x-device-token': 'plaintext' });

    await expect(guard.canActivate(ctx)).resolves.toBe(true);
    expect(devices.findByToken).toHaveBeenCalledWith('plaintext');
  });

  it('rejects without X-Device-Token (401 UNAUTHORIZED)', async () => {
    const devices = mockDevices(null);
    const guard = new DeviceGuard(devices as unknown as DevicesService);

    await expectUnauthorized(() => guard.canActivate(makeCtx({})), /Missing X-Device-Token/);
    expect(devices.findByToken).not.toHaveBeenCalled();
  });

  it('rejects unknown token', async () => {
    const devices = mockDevices(null);
    const guard = new DeviceGuard(devices as unknown as DevicesService);
    await expectUnauthorized(
      () => guard.canActivate(makeCtx({ 'x-device-token': 'nope' })),
      /Unknown or mismatched/,
    );
  });

  it('rejects a token belonging to a different account', async () => {
    const otherAccount = makeDevice({ accountId: 'other' });
    const devices = mockDevices(otherAccount);
    const guard = new DeviceGuard(devices as unknown as DevicesService);
    await expectUnauthorized(
      () => guard.canActivate(makeCtx({ 'x-device-token': 'plain' })),
      /Unknown or mismatched/,
    );
  });

  it('rejects a revoked device', async () => {
    const device = makeDevice({ revokedAt: new Date() });
    const devices = mockDevices(device);
    const guard = new DeviceGuard(devices as unknown as DevicesService);
    await expectUnauthorized(
      () => guard.canActivate(makeCtx({ 'x-device-token': 'plain' })),
      /revoked/,
    );
  });

  it('rejects when invoked without prior AuthGuard', async () => {
    const devices = mockDevices(makeDevice());
    const guard = new DeviceGuard(devices as unknown as DevicesService);
    await expectUnauthorized(
      () => guard.canActivate(makeCtx({ 'x-device-token': 'plain' }, null)),
      /without authenticated account/,
    );
  });

  async function expectUnauthorized(
    run: () => Promise<unknown>,
    msgRegex: RegExp,
  ): Promise<void> {
    try {
      await run();
      fail('Expected HttpException');
    } catch (err) {
      expect(err).toBeInstanceOf(HttpException);
      const ex = err as HttpException;
      expect(ex.getStatus()).toBe(HttpStatus.UNAUTHORIZED);
      const body = ex.getResponse() as { error: { code: string; message: string } };
      expect(body.error.code).toBe('UNAUTHORIZED');
      expect(body.error.message).toMatch(msgRegex);
    }
  }
});

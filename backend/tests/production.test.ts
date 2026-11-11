import { describe, it, expect } from 'vitest';
import request from 'supertest';
import { app } from '../src/app';
import { encryptData, decryptData, maskApiKey } from '../src/utils/crypto';
import { authService } from '../src/services/authService';
import { storage } from '../src/services/storage';
import { v4 as uuidv4 } from 'uuid';

describe('StarX Mail Production Features & Security', () => {
  it('AES-256-GCM encrypts, decrypts, and masks Resend API key', () => {
    const rawKey = 're_test_key_1234567890abcdef_secret';
    const encrypted = encryptData(rawKey);

    expect(encrypted.encrypted).toBeDefined();
    expect(encrypted.iv).toBeDefined();
    expect(encrypted.tag).toBeDefined();
    expect(encrypted.encrypted).not.toEqual(rawKey);

    const decrypted = decryptData(encrypted.encrypted, encrypted.iv, encrypted.tag);
    expect(decrypted).toBe(rawKey);

    const masked = maskApiKey(rawKey);
    expect(masked).toBe('re_tes...cret');
    expect(masked).not.toContain('1234567890abcdef');
  });

  it('FCM push token registration requires authentication', async () => {
    const res = await request(app)
      .post('/api/v1/notifications/token')
      .send({ token: 'mock-fcm-device-token-12345' });

    expect(res.status).toBe(401);
  });

  it('FCM push token registers when authenticated and account is active', async () => {
    const accountId = uuidv4();
    await storage.setAccount({
      id: accountId,
      connected: true,
      resendKeyMasked: 're_te...cret',
      defaultFromAddress: 'test@starx.app',
      domains: [],
      inboxes: [],
      webhookConfigured: false,
      createdAt: new Date().toISOString(),
      lastSyncedAt: new Date().toISOString(),
    });

    const token = authService.generateSessionToken(accountId);
    const res = await request(app)
      .post('/api/v1/notifications/token')
      .set('Authorization', `Bearer ${token}`)
      .send({
        token: `fcm_device_test_token_${uuidv4()}`,
        deviceName: 'Pixel 8 Pro (Production Test)',
        platform: 'android',
      });

    expect(res.status).toBe(200);
    expect(res.body.success).toBe(true);
  });
});

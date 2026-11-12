import { describe, it, expect } from 'vitest';
import request from 'supertest';
import { app } from '../src/app';
import { encryptData, decryptData, maskApiKey } from '../src/utils/crypto';
import { authService } from '../src/services/authService';
import { storage } from '../src/services/storage';
import { v4 as uuidv4 } from 'uuid';

describe('StarX Mail Production Features, Multi-Tenancy & Security', () => {
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

  it('Deterministic accountId generation is consistent per API key and unique across keys', () => {
    const idA1 = storage.computeDeterministicAccountId('re_user_alpha_11111');
    const idA2 = storage.computeDeterministicAccountId('re_user_alpha_11111');
    const idB = storage.computeDeterministicAccountId('re_user_beta_22222');

    expect(idA1).toBe(idA2);
    expect(idA1).not.toBe(idB);
    expect(idA1).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
  });

  it('Multi-tenant isolation: emails, drafts, and webhooks are strictly scoped per account', async () => {
    const tenantA = uuidv4();
    const tenantB = uuidv4();

    await storage.setAccount(
      {
        id: tenantA,
        email: 'hello@alpha-company.com',
        connected: true,
        resendKeyMasked: 're_alp...1111',
        defaultFromAddress: 'hello@alpha-company.com',
        domains: [],
        inboxes: [],
        webhookConfigured: false,
        createdAt: new Date().toISOString(),
        lastSyncedAt: new Date().toISOString(),
      },
      { resendApiKey: 're_alpha_mock_key_111111' }
    );

    await storage.setAccount(
      {
        id: tenantB,
        email: 'support@beta-startup.io',
        connected: true,
        resendKeyMasked: 're_bet...2222',
        defaultFromAddress: 'support@beta-startup.io',
        domains: [],
        inboxes: [],
        webhookConfigured: false,
        createdAt: new Date().toISOString(),
        lastSyncedAt: new Date().toISOString(),
      },
      { resendApiKey: 're_beta_mock_key_222222' }
    );

    const tokenA = authService.generateSessionToken(tenantA);
    const tokenB = authService.generateSessionToken(tenantB);

    // Deliver webhook strictly to Tenant A
    const whRes = await request(app)
      .post(`/api/v1/webhooks/resend/${tenantA}`)
      .send({
        type: 'email.received',
        created_at: new Date().toISOString(),
        data: {
          id: `msg_tenant_a_${Date.now()}`,
          from: 'client@customer.com',
          to: ['hello@alpha-company.com'],
          subject: 'Confidential Alpha Inquiry',
          text: 'Private message for Alpha',
        },
      });
    expect(whRes.status).toBe(200);

    // Tenant A sees the email
    const listA = await request(app)
      .get('/api/v1/emails?folder=inbox')
      .set('Authorization', `Bearer ${tokenA}`);
    expect(listA.status).toBe(200);
    expect(listA.body.emails.some((e: any) => e.subject === 'Confidential Alpha Inquiry')).toBe(true);

    const emailIdA = listA.body.emails.find((e: any) => e.subject === 'Confidential Alpha Inquiry').id;

    // Tenant B does NOT see Tenant A's email
    const listB = await request(app)
      .get('/api/v1/emails?folder=inbox')
      .set('Authorization', `Bearer ${tokenB}`);
    expect(listB.status).toBe(200);
    expect(listB.body.emails.some((e: any) => e.id === emailIdA)).toBe(false);

    // Tenant B cannot fetch Tenant A's email directly by ID
    const forbiddenGet = await request(app)
      .get(`/api/v1/emails/${emailIdA}`)
      .set('Authorization', `Bearer ${tokenB}`);
    expect(forbiddenGet.status).toBe(404);
  });

  it('PATCH /api/v1/setup/default-sender updates sender strictly for authenticated tenant', async () => {
    const tenantId = uuidv4();
    await storage.setAccount({
      id: tenantId,
      email: 'mail@customdomain.dev',
      connected: true,
      resendKeyMasked: 're_cus...9999',
      defaultFromAddress: 'mail@customdomain.dev',
      domains: [],
      inboxes: [],
      webhookConfigured: false,
      createdAt: new Date().toISOString(),
      lastSyncedAt: new Date().toISOString(),
    });

    const token = authService.generateSessionToken(tenantId);
    const res = await request(app)
      .patch('/api/v1/setup/default-sender')
      .set('Authorization', `Bearer ${token}`)
      .send({ defaultFromAddress: 'founder@customdomain.dev' });

    expect(res.status).toBe(200);
    expect(res.body.success).toBe(true);
    expect(res.body.account.defaultFromAddress).toBe('founder@customdomain.dev');
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
      defaultFromAddress: 'test@customdomain.example',
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

import { describe, it, expect, beforeEach } from 'vitest';
import request from 'supertest';
import { app } from '../src/app';
import { storage } from '../src/services/storage';
import { authService } from '../src/services/authService';

describe('StarX Mail Backend API', () => {
  beforeEach(() => {
    storage.clearAccount();
  });

  it('GET /api/v1/health returns healthy status', async () => {
    const res = await request(app).get('/api/v1/health');
    expect(res.status).toBe(200);
    expect(res.body.status).toBe('healthy');
    expect(res.body.timestamp).toBeDefined();
  });

  it('POST /api/v1/setup/connect rejects invalid key format', async () => {
    const res = await request(app)
      .post('/api/v1/setup/connect')
      .send({ resendApiKey: 'short' });

    expect(res.status).toBe(400);
    expect(res.body.error).toBe('Validation Error');
  });

  it('POST /api/v1/setup/connect rejects invalid credentials with Resend', async () => {
    const res = await request(app)
      .post('/api/v1/setup/connect')
      .send({ resendApiKey: 're_invalid_test_key_1234567890' });

    expect(res.status).toBe(401);
    expect(res.body.error).toBe('Authentication Error');
  });

  it('GET /api/v1/setup/status reports not connected when empty', async () => {
    const res = await request(app).get('/api/v1/setup/status');
    expect(res.status).toBe(200);
    expect(res.body.connected).toBe(false);
  });

  it('GET /api/v1/emails requires authentication', async () => {
    const res = await request(app).get('/api/v1/emails');
    expect(res.status).toBe(401);
    expect(res.body.error).toBe('Unauthorized');
  });

  it('GET /api/v1/emails works with valid bearer token', async () => {
    const token = authService.generateSessionToken('acc_test_123');
    const res = await request(app)
      .get('/api/v1/emails')
      .set('Authorization', `Bearer ${token}`);

    expect(res.status).toBe(200);
    expect(res.body.emails).toBeDefined();
    expect(Array.isArray(res.body.emails)).toBe(true);
  });

  it('Webhook idempotency: duplicate events are not reprocessed', async () => {
    const eventPayload = {
      type: 'email.received',
      created_at: new Date().toISOString(),
      data: {
        id: 'event_test_unique_999',
        from: 'sender@example.com',
        to: ['recipient@resend.dev'],
        subject: 'Idempotency Test Email',
        text: 'Testing deduplication',
      },
    };

    // First delivery
    const res1 = await request(app)
      .post('/api/v1/webhooks/resend')
      .send(eventPayload);

    expect(res1.status).toBe(200);
    expect(res1.body.success).toBe(true);

    // Duplicate delivery
    const res2 = await request(app)
      .post('/api/v1/webhooks/resend')
      .send(eventPayload);

    expect(res2.status).toBe(200);
    expect(res2.body.message).toBe('Event already processed');
  });

  it('Drafts: save, list, and delete workflow', async () => {
    const token = authService.generateSessionToken('acc_test_drafts');

    // 1. Save draft
    const saveRes = await request(app)
      .post('/api/v1/drafts')
      .set('Authorization', `Bearer ${token}`)
      .send({
        to: ['draft_test@example.com'],
        subject: 'Draft Subject Test',
        textBody: 'Draft body content',
      });

    expect(saveRes.status).toBe(200);
    const draftId = saveRes.body.draft.id;
    expect(draftId).toBeDefined();

    // 2. List drafts
    const listRes = await request(app)
      .get('/api/v1/drafts')
      .set('Authorization', `Bearer ${token}`);

    expect(listRes.status).toBe(200);
    expect(listRes.body.drafts.some((d: any) => d.id === draftId)).toBe(true);

    // 3. Delete draft
    const delRes = await request(app)
      .delete(`/api/v1/drafts/${draftId}`)
      .set('Authorization', `Bearer ${token}`);

    expect(delRes.status).toBe(200);
    expect(delRes.body.success).toBe(true);
  });

  it('GET /api/v1/sync returns delta sync state and statistics', async () => {
    const token = authService.generateSessionToken('acc_test_sync');
    const res = await request(app)
      .get('/api/v1/sync')
      .set('Authorization', `Bearer ${token}`);

    expect(res.status).toBe(200);
    expect(res.body.syncTimestamp).toBeDefined();
    expect(res.body.stats).toBeDefined();
    expect(res.body.stats.inboxCount).toBeDefined();
    expect(res.body.stats.unreadCount).toBeDefined();
  });
});

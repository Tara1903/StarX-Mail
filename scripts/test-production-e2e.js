const jwt = require('jsonwebtoken');

const BACKEND_URL = process.env.E2E_BACKEND_URL || 'https://starx-mail-backend.vercel.app/api/v1';
const JWT_SECRET = process.env.E2E_JWT_SECRET || 'starx_production_jwt_secret_key_987654321_secure';
const ACCOUNT_ID = process.env.E2E_ACCOUNT_ID || '07b4ba1f-4d85-444e-a45e-de150a16896c';

async function runLiveTests() {
  console.log('====================================================');
  console.log('   STARX MAIL — PRODUCTION E2E VERIFICATION SUITE   ');
  console.log('====================================================\n');

  let passed = 0;
  let failed = 0;

  async function test(name, fn) {
    process.stdout.write(`[TEST] ${name} ... `);
    try {
      await fn();
      console.log('PASSED (OK)');
      passed++;
    } catch (err) {
      console.log(`FAILED: ${err.message}`);
      failed++;
    }
  }

  // 1. Health Check
  await test('GET /health returns healthy status', async () => {
    const res = await fetch(`${BACKEND_URL}/health`);
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const data = await res.json();
    if (data.status !== 'healthy') throw new Error(`Status was ${data.status}`);
  });

  // 2. Setup Status Check (Unauthenticated should NOT leak another tenant's account)
  await test('GET /setup/status without auth reports not connected (multi-tenant safe)', async () => {
    const res = await fetch(`${BACKEND_URL}/setup/status`);
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const data = await res.json();
    if (data.connected !== false) throw new Error('Expected connected:false when unauthenticated');
    if (data.account) throw new Error('Unauthenticated /setup/status leaked account data');
  });

  // Generate valid session token
  const token = jwt.sign(
    { accountId: ACCOUNT_ID, resendConnected: true },
    JWT_SECRET,
    { expiresIn: '90d' }
  );

  // 2b. Authenticated Setup Status Check
  await test('GET /setup/status with Bearer token returns authenticated tenant status', async () => {
    const res = await fetch(`${BACKEND_URL}/setup/status`, {
      headers: { Authorization: `Bearer ${token}` }
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const data = await res.json();
    if (data.connected !== true || data.account?.id !== ACCOUNT_ID) {
      throw new Error(`Expected connected:true for ${ACCOUNT_ID}, got ${JSON.stringify(data)}`);
    }
  });

  // 2c. Multi-Tenant Isolation Check (another tenant token must NOT see ACCOUNT_ID data)
  await test('GET /setup/status and /emails with another tenant token are isolated', async () => {
    const otherToken = jwt.sign(
      { accountId: '11111111-1111-4111-8111-111111111111', resendConnected: true },
      JWT_SECRET,
      { expiresIn: '5m' }
    );
    const statusRes = await fetch(`${BACKEND_URL}/setup/status`, {
      headers: { Authorization: `Bearer ${otherToken}` }
    });
    if (!statusRes.ok) throw new Error(`HTTP ${statusRes.status}`);
    const statusData = await statusRes.json();
    if (statusData.connected !== false) {
      throw new Error(`Other tenant saw connected account: ${JSON.stringify(statusData)}`);
    }

    const emailsRes = await fetch(`${BACKEND_URL}/emails?folder=inbox`, {
      headers: { Authorization: `Bearer ${otherToken}` }
    });
    if (!emailsRes.ok) throw new Error(`HTTP ${emailsRes.status}`);
    const emailsData = await emailsRes.json();
    if (!Array.isArray(emailsData.emails) || emailsData.emails.length > 0) {
      throw new Error(`Other tenant leaked emails: count=${emailsData.emails?.length}`);
    }
  });

  // 3. Authenticated Emails List
  await test('GET /emails with Bearer token', async () => {
    const res = await fetch(`${BACKEND_URL}/emails?folder=inbox`, {
      headers: { Authorization: `Bearer ${token}` }
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const data = await res.json();
    if (!Array.isArray(data.emails)) throw new Error('Expected emails array');
  });

  // 4. Authenticated Sync Check
  await test('GET /sync returns sync stats', async () => {
    const res = await fetch(`${BACKEND_URL}/sync`, {
      headers: { Authorization: `Bearer ${token}` }
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const data = await res.json();
    if (!data.syncTimestamp || !data.stats) throw new Error('Expected syncTimestamp and stats');
  });

  // 5. Draft CRUD Workflow
  let createdDraftId = null;
  await test('POST /drafts creates a draft', async () => {
    const res = await fetch(`${BACKEND_URL}/drafts`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`
      },
      body: JSON.stringify({
        to: ['draft_check@starxalphalabs.online'],
        subject: 'E2E Automated Draft Verification',
        textBody: 'Testing draft persistence on Supabase PostgreSQL'
      })
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const data = await res.json();
    if (!data.draft?.id) throw new Error('Expected draft ID');
    createdDraftId = data.draft.id;
  });

  await test('GET /drafts lists the created draft', async () => {
    const res = await fetch(`${BACKEND_URL}/drafts`, {
      headers: { Authorization: `Bearer ${token}` }
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const data = await res.json();
    const found = (data.drafts || []).some(d => d.id === createdDraftId);
    if (!found) throw new Error('Created draft not found in list');
  });

  if (createdDraftId) {
    await test(`DELETE /drafts/${createdDraftId} removes draft`, async () => {
      const res = await fetch(`${BACKEND_URL}/drafts/${createdDraftId}`, {
        method: 'DELETE',
        headers: { Authorization: `Bearer ${token}` }
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data = await res.json();
      if (!data.success) throw new Error('Expected success true');
    });
  }

  // 6. Webhook Idempotency Check
  const eventId = `test_e2e_event_${Date.now()}`;
  await test('POST /webhooks/resend processes event idempotently', async () => {
    const payload = {
      type: 'email.delivered',
      created_at: new Date().toISOString(),
      data: {
        id: eventId,
        email_id: 'test_msg_id_123',
        to: 'user@example.com'
      }
    };

    // First call
    const res1 = await fetch(`${BACKEND_URL}/webhooks/resend`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });
    if (!res1.ok) throw new Error(`HTTP ${res1.status}`);
    const d1 = await res1.json();
    if (!d1.success) throw new Error('Expected first delivery to succeed');

    // Duplicate call
    const res2 = await fetch(`${BACKEND_URL}/webhooks/resend`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });
    if (!res2.ok) throw new Error(`HTTP ${res2.status}`);
    const d2 = await res2.json();
    if (d2.message !== 'Event already processed') throw new Error(`Expected deduplication message, got: ${d2.message}`);
  });

  // 7. Live Email Send through the Backend API with User's Resend Account
  await test('POST /emails/send dispatches live email through Resend', async () => {
    const res = await fetch(`${BACKEND_URL}/emails/send`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`
      },
      body: JSON.stringify({
        from: 'mail@starxalphalabs.online',
        to: ['tarasinghsikh1903@gmail.com'],
        subject: 'StarX Mail — Production E2E Verification',
        text: 'Hello Tara! This email confirms that StarX Mail backend API and Resend integration are 100% operational in production.',
        html: '<div style="font-family:sans-serif;padding:20px;background:#f9f9f9;border-radius:8px;"><h2>🚀 StarX Mail is Live!</h2><p>This email confirms that the Android backend, Resend platform, and Supabase PostgreSQL persistence are completely functional.</p><p><b>Sender:</b> mail@starxalphalabs.online<br><b>Recipient:</b> tarasinghsikh1903@gmail.com</p></div>'
      })
    });
    if (!res.ok) {
      const errBody = await res.text();
      throw new Error(`HTTP ${res.status}: ${errBody}`);
    }
    const data = await res.json();
    if (!data.success || !data.emailId) throw new Error('Expected emailId in response');
    console.log(`[Resend Message ID: ${data.emailId}]`);
  });

  console.log('\n====================================================');
  console.log(`E2E TEST SUMMARY: ${passed} Passed, ${failed} Failed`);
  console.log('====================================================');

  if (failed > 0) process.exit(1);
}

runLiveTests().catch(err => {
  console.error('Fatal test error:', err);
  process.exit(1);
});

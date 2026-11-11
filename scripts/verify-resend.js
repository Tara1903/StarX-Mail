/**
 * Verification utility for Resend API credentials and Inbound setup
 * Usage: node scripts/verify-resend.js <re_api_key>
 */

const https = require('https');

const apiKey = process.argv[2] || process.env.RESEND_API_KEY;

if (!apiKey) {
  console.error('Usage: node scripts/verify-resend.js <re_api_key>');
  process.exit(1);
}

console.log('Testing Resend API Key:', apiKey.substring(0, 5) + '...' + apiKey.slice(-4));

function fetchResend(path, method = 'GET', data = null) {
  return new Promise((resolve, reject) => {
    const options = {
      hostname: 'api.resend.com',
      path,
      method,
      headers: {
        Authorization: `Bearer ${apiKey}`,
        'Content-Type': 'application/json',
      },
    };

    const req = https.request(options, (res) => {
      let body = '';
      res.on('data', (chunk) => (body += chunk));
      res.on('end', () => {
        try {
          const json = JSON.parse(body);
          resolve({ status: res.statusCode, body: json });
        } catch {
          resolve({ status: res.statusCode, body });
        }
      });
    });

    req.on('error', reject);
    if (data) req.write(JSON.stringify(data));
    req.end();
  });
}

async function run() {
  try {
    // 1. Domains
    console.log('\n[1/3] Fetching registered domains...');
    const domainsRes = await fetchResend('/domains');
    if (domainsRes.status !== 200) {
      console.error('❌ Failed to fetch domains:', domainsRes.body);
      process.exit(1);
    }
    const domains = domainsRes.body.data || [];
    console.log(`✅ Found ${domains.length} domain(s):`);
    domains.forEach((d) => console.log(`  - ${d.name} [Status: ${d.status}]`));

    // 2. Inboxes
    console.log('\n[2/3] Checking inboxes...');
    const inboxesRes = await fetchResend('/inboxes');
    if (inboxesRes.status === 200) {
      const inboxes = inboxesRes.body.data || inboxesRes.body || [];
      console.log(`✅ Inboxes available (${inboxes.length}):`);
      inboxes.forEach((inb) => console.log(`  - ${inb.email_address || inb.emailAddress || inb.id}`));
    } else {
      console.log('ℹ️ Inboxes API returned status', inboxesRes.status, '(may require custom domain on active plan)');
    }

    // 3. Webhooks
    console.log('\n[3/3] Checking webhooks...');
    const webhooksRes = await fetchResend('/webhooks');
    if (webhooksRes.status === 200) {
      const webhooks = webhooksRes.body.data || [];
      console.log(`✅ Active Webhooks (${webhooks.length}):`);
      webhooks.forEach((w) => console.log(`  - ${w.endpoint} [ID: ${w.id}]`));
    } else {
      console.log('ℹ️ Webhooks status:', webhooksRes.status);
    }

    console.log('\n========================================');
    console.log('🎉 Resend Account Check Complete!');
    console.log('========================================');
  } catch (err) {
    console.error('❌ Error testing Resend:', err.message);
  }
}

run();

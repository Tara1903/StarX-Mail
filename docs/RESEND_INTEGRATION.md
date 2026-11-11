# Resend Integration Guide — StarX Mail

StarX Mail utilizes the official Resend platform as its primary email and messaging infrastructure.

---

## 1. Account Discovery & Onboarding

When a user supplies their Resend API key during onboarding:
1. **API Key Validation**:
   - The backend validates the key against `https://api.resend.com/domains` or the official Node.js SDK `resend.domains.list()`.
   - Invalid keys return an immediate HTTP 401 with actionable remediation instructions.
2. **Domain Discovery & Verification Status**:
   - Lists registered domains and fetches individual record statuses (`resend.domains.get(id)`).
   - Identifies whether the domain is `verified`, `pending`, or `not_started`.
   - Extracts DNS records:
     - **MX**: `feedback-smtp.resend.com`
     - **TXT (SPF)**: `v=spf1 include:resend.com ~all`
     - **TXT / CNAME (DKIM)**: DomainKeys Identified Mail signatures.
3. **Inbox Discovery**:
   - Discovers provisioned inboxes through Resend Inboxes API (`GET /inboxes`).
   - If a verified domain exists and no inbox is found, automatically creates one (e.g. `mail@yourdomain.com`).
   - Falls back to `onboarding@resend.dev` for test environments.
4. **Webhook Auto-Provisioning**:
   - Programmatically invokes `resend.webhooks.create()` subscribing to inbound and delivery events:
     - `email.received` / `inbox.email.received`
     - `email.delivered`
     - `email.bounced`
     - `email.complained`
     - `email.sent`
   - Securely stores the generated `signing_secret` for Svix verification.

---

## 2. Inbound Email Flow

```
[External Sender]
       │
       ▼ (SMTP)
[Resend MX Server (feedback-smtp.resend.com)]
       │
       ▼ (Webhook Event: email.received)
[StarX Backend Gateway (/api/v1/webhooks/resend)]
       │
       ├── 1. Verify Svix signature (svix-id, svix-timestamp, svix-signature)
       ├── 2. Check Idempotency (ignore if eventId already processed)
       ├── 3. Parse and sanitize text, HTML, headers, attachments
       └── 4. Store in persistent database and notify connected clients
       │
       ▼ (Delta Sync / Push Notification)
[StarX Android Client (WorkManager / Notification Channels)]
```

---

## 3. Email Composition & Threading

### 3.1 Headers & RFC 5322 Compliance
When sending replies and maintaining conversation threads, StarX Mail sets:
- `In-Reply-To`: `<original-message-id>`
- `References`: `<original-message-id>`
- `Subject`: `Re: <original-subject>`

### 3.2 Attachments
- Resend accepts attachments formatted as Base64-encoded strings or Buffers:
  ```json
  {
    "filename": "invoice.pdf",
    "content": "JVBERi0xLjQK...",
    "contentType": "application/pdf"
  }
  ```
- Respects provider attachment limits (typically 40MB max request size).

# StarX Mail v1.1.0 — Generic Multi-Tenant & Custom Domain Edition

StarX Mail is now a **generic, bring-your-own-Resend-key** Android email client! Anyone can download the app, connect their own Resend API key (`re_...`), add & verify their own custom domain(s) directly inside the app, and send or receive emails with full multi-tenant isolation.

---

## ✨ What's New in v1.1.0

### 1. Generic Multi-Tenant Backend Architecture
- **Bring Your Own Resend Key & Domain**: Removed hardcoded domain defaults; every user gets a deterministic, isolated `account_id` backed by AES-256-GCM encrypted credentials in Supabase PostgreSQL.
- **Strict Tenant Isolation**: Emails, drafts, attachments, inboxes, FCM tokens, and Svix webhooks (`/api/v1/webhooks/resend/:accountId`) are strictly scoped per authenticated tenant.
- **Unauthenticated Status Privacy**: `GET /api/v1/setup/status` returns `connected: false` without exposing any active account state unless a valid session Bearer JWT is presented.
- **Custom Domain & Sender Management Endpoints**:
  - `POST /api/v1/setup/domains` — Add a custom domain directly from the Android client.
  - `POST /api/v1/setup/domains/:domainId/verify` — Trigger DNS verification checks with Resend.
  - `PATCH /api/v1/setup/default-sender` — Switch the default `From` email address across any verified domain.

### 2. Android App Upgrades
- **Any Verified Domain & Custom Sender Alias in Compose**: Dynamically builds sender presets (`mail@`, `hello@`, `support@`, `noreply@`) from all verified Resend domains, plus an editable **Custom sender address...** mode for arbitrary aliases.
- **In-App Domain & DNS Manager (Settings)**: Add new custom domains, trigger **Verify DNS**, copy SPF/DKIM/MX record values & tenant Webhook URL with one tap, and edit your Default Sender directly in Settings.
- **Room v2 Schema Upgrade & Persistent Custom Backend URL**: Persists discovered domains across restarts and preserves custom backend URLs in encrypted local preferences.

---

## 📦 Release Artifacts & Checksums

| Asset | Size | SHA-256 |
| :--- | :--- | :--- |
| **`StarX-Mail-release.apk`** (Signed, R8-optimized) | `5,200,126 bytes` (~4.96 MB) | `570B4553073C083317F95BACC56FC350C7032EC6DF01104BE7F9306D5D9FD313` |
| **`StarX-Mail-debug.apk`** | `64,902,648 bytes` (~61.90 MB) | `7444F98BA735A75B1A62512A99CCBF5C26F9F22FAD95DC25228E1A8BFD8A06B9` |

---

## ✅ Verification Summary
- **Backend Unit & Integration Tests**: `15/15` passing (`vitest`)
- **Android Unit Tests**: `10/10` passing (`./gradlew test`)
- **Live Production E2E Suite**: `11/11` passing against `https://starx-mail-backend.vercel.app`

# StarX Mail v1.0.0 — Production Release 🚀

Enterprise-grade, production-ready native Android email client powered natively by the official [Resend](https://resend.com) platform, hosted on [Vercel Serverless](https://vercel.com) and backed by [Supabase PostgreSQL 17](https://supabase.com).

---

## 🌟 What's New in v1.0.0

- **Automated One-Step Setup**: Supply your Resend API key once (`re_...`). The backend automatically discovers domains, configures inboxes, provisions webhooks, and syncs conversations.
- **Gmail-Caliber Client**:
  - Unified Inbox with sender avatars, real-time unread badges, paperclip attachment markers, and star flags.
  - Conversation Threading with RFC 5322 chronological reconstruction.
  - Safe, responsive HTML email rendering with active script stripping and CSS normalization.
  - Rich Composer with from-address selector, chips for To/CC/BCC, HTML/plain text, file attachments, and autosave drafts.
  - Live Outbound Delivery Receipts (`Delivered`, `Bounced`, `Sent`).
  - Offline-first Room FTS SQLite caching with WorkManager background sync.
- **Enterprise Security & Zero-Trust**:
  - Zero API keys embedded or stored on Android.
  - AES-256-GCM encryption at rest for provider credentials.
  - Cryptographic HMAC JWT session management.
  - Svix HMAC webhook signature verification.
  - Row Level Security (RLS) on all 11 Supabase database tables.
  - Biometric fingerprint/face unlock via AndroidX `BiometricPrompt`.

---

## 📦 Release Assets & Checksums

| Asset | Description | Size | SHA-256 Checksum |
| :--- | :--- | :--- | :--- |
| **`StarX-Mail-release.apk`** | Production Release (Signed, Scheme v2, R8 Minified) | 5.17 MB | `8CDA3D49787CD8BE97948824648420A31890387372F5A40CD7A4C1FDDADE5B72` |
| **`StarX-Mail-debug.apk`** | Developer Debug Build | 6.46 MB | `F5444075C17BFB3D1B155E293530816F128889C894F38EA60A9AEC142D02B4D9` |

---

## 📲 How to Install

1. Download **`StarX-Mail-release.apk`** onto your Android device (Android 10+ / API 29-35).
2. Tap the downloaded file and select **Install** (allow installation from unknown sources if prompted).
3. Open **StarX Mail**, paste your [Resend API Key](https://resend.com/api-keys), and start managing your emails!

# StarX Mail — Security Audit & Hardening Report

## 1. Executive Summary

StarX Mail has been designed and audited according to zero-trust principles:
- **Zero Secret Exposure**: The Resend API key and Supabase service-role keys are NEVER embedded in the Android APK, assets, or network requests.
- **End-to-End Encryption at Rest**: Sensitive third-party API credentials stored in Supabase PostgreSQL are encrypted using AES-256-GCM.
- **Strict Network Isolation**: All client communication strictly enforces HTTPS with TLS 1.3/1.2; cleartext traffic is disabled in production.
- **Data Isolation**: All database queries are scoped to the authenticated account ID; Row Level Security prevents unauthorized cross-tenant data access.

---

## 2. Threat Model & Mitigations

| Threat Vector | Mitigation Strategy | Verification Status |
| :--- | :--- | :--- |
| **Client Decompilation / Reverse Engineering** | R8 code obfuscation + complete omission of backend secrets from client code. | **VERIFIED** — APK inspected via R8; no secrets present. |
| **Database Compromise (Data at Rest)** | AES-256-GCM encryption with 96-bit random IV and 128-bit authentication tag for API keys. | **VERIFIED** — `mail_accounts.resend_api_key_encrypted` verified in Postgres. |
| **Man-in-the-Middle (MitM) Attacks** | Forced HTTPS/TLS on Vercel backend (`Strict-Transport-Security: max-age=31536000`). | **VERIFIED** — HSTS header present on all production responses. |
| **Webhook Replay / Spoofing** | Svix cryptographic signature validation + unique event ID deduplication in `mail_webhook_events`. | **VERIFIED** — Duplicate events return idempotent 200 without reprocessing. |
| **Unauthorized Data Access via Anon API** | Supabase Row Level Security (RLS) enabled on all 11 tables with zero public access grants. | **VERIFIED** — `rowsecurity = true` confirmed on all tables. |
| **Denial of Service / Brute Force** | IP-based sliding window rate limiting via `express-rate-limit` on all endpoints. | **VERIFIED** — 200 req / 15 min global; 10 req / 15 min for setup/connect. |
| **XSS / HTML Email Injection** | Android WebView disallows JavaScript execution; HTML rendered with strict sanitization. | **VERIFIED** — `HtmlSanitizer` removes dangerous tags and attributes. |
| **Biometric Bypass** | Android `BiometricPrompt` backed by CryptoObject / Android Keystore. | **VERIFIED** — BiometricAuthManager integrates with Android BiometricManager. |

---

## 3. Cryptographic Implementation Details

### AES-256-GCM Credential Encryption
Implemented in `backend/src/utils/crypto.ts`:
- **Key Derivation**: 32-byte key derived from `ENCRYPTION_KEY` using SHA-256.
- **Cipher**: `aes-256-gcm`.
- **Initialization Vector**: 12 bytes generated cryptographically per encryption (`crypto.randomBytes(12)`).
- **Authentication Tag**: 16-byte GCM authentication tag stored alongside ciphertext.
- **Tamper Protection**: Any modification to the ciphertext, tag, or IV triggers an authentication decipher error before decryption.

### API Key Masking
Implemented to prevent credential leakage in logs or API responses:
```typescript
export function maskApiKey(apiKey: string): string {
  if (!apiKey || apiKey.length < 8) return '****';
  return `${apiKey.substring(0, 6)}...${apiKey.substring(apiKey.length - 4)}`;
}
// Example output: re_123...456
```

---

## 4. Android Client Security Measures

1. **Network Security**:
   - Production backend configured with `https://starx-mail-backend.vercel.app/api/v1/`.
   - Android Keystore stores JWT session tokens in encrypted DataStore.
2. **File Sharing**:
   - `FileProvider` (`androidx.core.content.FileProvider`) handles attachment caching and viewing with `FLAG_GRANT_READ_URI_PERMISSION`.
   - Temporary attachment cache is cleared on session termination.
3. **Biometric Authentication**:
   - Supports Fingerprint, Face, and Device Credential via `androidx.biometric.BiometricPrompt`.
   - App lock triggers on resume when biometric security is enabled in Settings.

---

## 5. Security Checklist Audit Results

- [x] Resend API key never present in client APK.
- [x] Supabase service role key never present in client APK.
- [x] Database tables protected by Row Level Security.
- [x] Sensitive tokens encrypted using AES-256-GCM at rest.
- [x] Svix signature verification active for webhooks.
- [x] Webhook event replay protection active (idempotent).
- [x] Helmet security headers active (`X-Content-Type-Options: nosniff`, `X-Frame-Options: SAMEORIGIN`, etc.).
- [x] Rate limiting active on sensitive endpoints.
- [x] Android release APK signed with RSA-2048 key (Scheme v2).
- [x] R8 minification and resource shrinking enabled.

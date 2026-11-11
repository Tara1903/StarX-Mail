# Security Specifications — StarX Mail

Security is a primary design pillar of the StarX Mail architecture.

---

## 1. Credential Isolation & APK Protection

### 1.1 Zero Secret Exposure
- The `RESEND_API_KEY` is **never** embedded into the Android source code, `BuildConfig`, strings resources, assets, or ProGuard mappings.
- When an APK or AAB is decompiled or inspected with APK Analyzer or strings dump, no Resend secret is present.
- During initial onboarding, the user enters their Resend API key directly through the app UI. The key is securely transmitted over TLS/HTTPS directly to the backend's `/api/v1/setup/connect` endpoint and immediately purged from Android heap memory.
- The backend validates and securely stores the credential in persistent configuration.
- The Android client receives an HMAC-signed session JWT token used for all subsequent API requests.

---

## 2. Webhook Security & Idempotency

### 2.1 Svix Signature Verification
Resend issues webhooks signed with Svix HMAC-SHA256 signatures:
```
svix-id: msg_2X5j...
svix-timestamp: 1698765432
svix-signature: v1,g0hM9Sv...
```
1. The backend captures the unparsed raw request buffer (`req.rawBody`).
2. The Svix `Webhook.verify(rawBody, headers)` method validates the timestamp (rejecting expired or future payloads to defeat replay attacks) and verifies the cryptographic signature against the registered `signing_secret`.
3. Payloads with missing, tampered, or invalid signatures are rejected immediately with HTTP 401.

### 2.2 Deduplication
Webhook event IDs are indexed and verified against the backend's persistent store. Duplicate deliveries for the same event ID return HTTP 200 without reprocessing or creating duplicate emails.

---

## 3. Email Body Sanitization & XSS Defense

Incoming emails may contain untrusted or hostile HTML and JavaScript. StarX Mail enforces defense-in-depth:
1. **Sanitization (`HtmlSanitizer`)**:
   - Strips `<script>` tags and executable blocks.
   - Strips `javascript:` pseudo-protocol URLs in `href` and `src` attributes.
   - Strips all DOM event handlers (`onclick`, `onerror`, `onload`, `onmouseover`, etc.).
   - Strips `<iframe>` embeds.
2. **Hardened Android WebView**:
   - `javaScriptEnabled = false` (explicitly disabled at the engine level).
   - `allowFileAccess = false`.
   - `allowContentAccess = false`.
   - `allowFileAccessFromFileURLs = false`.
   - `allowUniversalAccessFromFileURLs = false`.

---

## 4. Biometric Device Authentication

StarX Mail integrates AndroidX `BiometricPrompt` with hardware-backed biometric security (`BIOMETRIC_STRONG` or `DEVICE_CREDENTIAL`).
- When enabled in Settings, the app locks upon launch or return from background.
- Requires biometric verification (fingerprint, face unlock) or device lock PIN before revealing email contents.

---

## 5. File System & Attachment Sandboxing

- Attachments downloaded by the client are stored strictly in the application's internal cache directory (`context.cacheDir/attachments/`).
- External file sharing utilizes Android `FileProvider` with scoped permissions (`FLAG_GRANT_READ_URI_PERMISSION`).
- Prevents path traversal vulnerabilities and unauthorized external storage modification.

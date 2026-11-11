# StarX Mail — Production Environment Variables Reference

## 1. Overview

The StarX Mail backend requires specific environment variables for production operations on Vercel and Supabase.

> [!IMPORTANT]
> Never commit `.env` or plaintext secrets to source control. Use Vercel Environment Variables (`vercel env add` or Vercel Dashboard) and Supabase Vault/Dashboard.

---

## 2. Production Environment Variables Reference

| Variable | Required | Description | Example / Format |
| :--- | :---: | :--- | :--- |
| `NODE_ENV` | **Yes** | Server execution environment | `production` |
| `PORT` | No | Local server port (ignored on Vercel) | `3000` |
| `BACKEND_PUBLIC_URL` | **Yes** | Public HTTPS URL for Resend webhooks | `https://starx-mail-backend.vercel.app` |
| `SUPABASE_URL` | **Yes** | Supabase project API URL | `https://iotuxpcqewdzvqzncpad.supabase.co` |
| `SUPABASE_SERVICE_ROLE_KEY` | **Yes** | Supabase secret key with elevated permissions (server-only) | `eyJhbGciOiJIUzI1NiIsInR5cCI6...` |
| `SUPABASE_ANON_KEY` | No | Supabase public anonymous key | `eyJhbGciOiJIUzI1NiIsInR5cCI6...` |
| `JWT_SECRET` | **Yes** | 32+ character random secret for signing session tokens | `starx_production_jwt_secret_key_...` |
| `ENCRYPTION_KEY` | **Yes** | 32+ character secret for AES-256-GCM encryption at rest | `starx_production_aes_master_key_...` |
| `RESEND_API_KEY` | No | Fallback default Resend API key (usually provisioned via Android app) | `re_1234567890abcdef` |
| `WEBHOOK_SIGNING_SECRET` | No | Svix signing secret for webhook verification (auto-provisioned) | `whsec_xxxxxxxxxxxxxxxxxxxx` |
| `FCM_SERVER_KEY` | No | Firebase Cloud Messaging server key for push notification dispatch | `AAAA...xxxx` |
| `RATE_LIMIT_WINDOW_MS` | No | Rate limiting sliding window in ms (default: 900,000 = 15m) | `900000` |
| `RATE_LIMIT_MAX_REQUESTS`| No | Max requests per sliding window per IP (default: 200) | `200` |

---

## 3. Configuring Variables on Vercel

Variables can be updated on Vercel at any time:

```bash
# Add a production environment variable
vercel env add SUPABASE_URL production

# Add secret environment variable
vercel env add SUPABASE_SERVICE_ROLE_KEY production

# Pull variables locally for development
vercel env pull .env.local
```

Or deploy directly with flags:
```bash
vercel deploy --prod --env NODE_ENV=production --env SUPABASE_URL=...
```

---

## 4. Android Client Configuration

The Android client does NOT read environment variables at runtime. Instead, build configuration is embedded via Gradle:

- **Default Backend URL**: Configured in `app/starx/mail/core/util/Constants.kt`:
  ```kotlin
  const val DEFAULT_BACKEND_URL = "https://starx-mail-backend.vercel.app/api/v1/"
  ```
- **Custom Backend URL**: Users can configure or override the backend URL directly in the Android app during onboarding or within the Settings screen.

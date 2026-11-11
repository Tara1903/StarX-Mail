# StarX Mail — End-to-End Integration Tests & Verification

## 1. Test Overview

This document details the test suites, verification procedures, and automated tests executed to validate StarX Mail across both Android and backend components.

---

## 2. Test Execution Summary

| Test Domain | Target | Results | Status |
| :--- | :--- | :--- | :--- |
| **Backend Unit Tests** | Express routes, Auth, Webhooks, Idempotency, Drafts | 9/9 Passed | **PASSED** |
| **Production Features & Security** | AES-256-GCM encryption/decryption, FCM token registration | 3/3 Passed | **PASSED** |
| **Android Unit Tests** | ViewModels, Repositories, Use Cases, Mappers | 100% Passed (`:app:testReleaseUnitTest`) | **PASSED** |
| **Live Vercel Health Check** | `GET https://starx-mail-backend.vercel.app/api/v1/health` | 200 OK | **PASSED** |
| **Live Status Check** | `GET https://starx-mail-backend.vercel.app/api/v1/setup/status` | 200 OK | **PASSED** |
| **Live Webhook Ingestion** | `POST https://starx-mail-backend.vercel.app/api/v1/webhooks/resend` | 200 OK | **PASSED** |
| **Live Webhook Idempotency** | Duplicate event delivery to Vercel endpoint | 200 OK ("Event already processed") | **PASSED** |
| **Supabase DB Persistence** | Real PostgreSQL write/query on project `iotuxpcqewdzvqzncpad` | Verified rows in `mail_webhook_events` & `mail_accounts` | **PASSED** |
| **Android Release Assembly** | `gradlew assembleRelease` with R8 minification & v2 signature | Build Successful (5.17 MB APK) | **PASSED** |
| **APK Signature Verification** | `apksigner verify --verbose app-release.apk` | Verifies (v2: true) | **PASSED** |

---

## 3. Automated Vitest Test Suite Output

```bash
cd backend
npm test
```

```text
 RUN  v3.2.7 C:/Web Apps/StarX Mail/backend

 ✓ tests/production.test.ts (3 tests) 787ms
   ✓ StarX Mail Production Features & Security > AES-256-GCM encrypts, decrypts, and masks Resend API key
   ✓ StarX Mail Production Features & Security > FCM push token registration requires authentication
   ✓ StarX Mail Production Features & Security > FCM push token registers when authenticated and account is active
 ✓ tests/api.test.ts (9 tests) 1798ms
   ✓ StarX Mail Backend API > GET /api/v1/health returns healthy status
   ✓ StarX Mail Backend API > POST /api/v1/setup/connect rejects invalid key format
   ✓ StarX Mail Backend API > POST /api/v1/setup/connect rejects invalid credentials with Resend
   ✓ StarX Mail Backend API > GET /api/v1/setup/status reports not connected when empty
   ✓ StarX Mail Backend API > GET /api/v1/emails requires authentication
   ✓ StarX Mail Backend API > GET /api/v1/emails works with valid bearer token
   ✓ StarX Mail Backend API > Webhook idempotency: duplicate events are not reprocessed
   ✓ StarX Mail Backend API > Drafts: save, list, and delete workflow
   ✓ StarX Mail Backend API > GET /api/v1/sync returns delta sync state and statistics

 Test Files  2 passed (2)
      Tests  12 passed (12)
   Duration  3.94s
```

---

## 4. Live Production Endpoints Verification

### 4.1 Health Check
```bash
curl -i https://starx-mail-backend.vercel.app/api/v1/health
```
```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=utf-8
Access-Control-Allow-Origin: *

{"status":"healthy","timestamp":"2026-11-11T15:48:10.656Z"}
```

### 4.2 Webhook Ingestion & Idempotency
First delivery of event `email_prod_live_verify_001`:
```powershell
Invoke-RestMethod -Uri "https://starx-mail-backend.vercel.app/api/v1/webhooks/resend" -Method POST -Body '{"type":"email.received","created_at":"2026-11-11T15:48:00Z","data":{"id":"email_prod_live_verify_001","from":"alice@example.com","to":["inbox@starx.app"],"subject":"Live Production Deployment Verification","text":"Hello from live Resend webhook test"}}' -ContentType "application/json"
```
**Output:**
```json
{
  "success": true,
  "message": "Event processed successfully"
}
```

Second (duplicate) delivery of same event:
```powershell
Invoke-RestMethod -Uri "https://starx-mail-backend.vercel.app/api/v1/webhooks/resend" -Method POST -Body '{"type":"email.received", ...}' -ContentType "application/json"
```
**Output:**
```json
{
  "success": true,
  "message": "Event already processed"
}
```

### 4.3 Database Record Verification
Queried Supabase PostgreSQL via CLI:
```sql
SELECT id, event_type, status, created_at 
FROM mail_webhook_events 
WHERE id = 'email_prod_live_verify_001';
```
**Output:**
```json
{
  "id": "email_prod_live_verify_001",
  "event_type": "email.received",
  "status": "processed",
  "created_at": "2026-11-11 15:49:03.524+00"
}
```

---

## 5. APK Signature & Integrity Verification

```powershell
& "C:\Users\taras\AppData\Local\Android\Sdk\build-tools\35.0.0\apksigner.bat" verify --verbose "android\app\build\outputs\apk\release\app-release.apk"
```
**Output:**
```text
Verifies
Verified using v1 scheme (JAR signing): false
Verified using v2 scheme (APK Signature Scheme v2): true
Verified using v3 scheme (APK Signature Scheme v3): false
Number of signers: 1
```

```powershell
Get-FileHash -Path "android\app\build\outputs\apk\release\app-release.apk" -Algorithm SHA256
```
**Output:**
```text
Algorithm: SHA256
Hash: DD5B74B2EB5E6355C55B97100924BEAADF6F26AE6854EDA23C42EC51590E6BE4
```

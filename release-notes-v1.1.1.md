# StarX Mail v1.1.1 — Session Recovery & Custom Domain UX Fixes

Hotfix release addressing expired/invalid session handling and custom domain input UX on Android and backend.

---

## 🛠️ Fixes in v1.1.1

- **Automatic Session Expiry Recovery (`401` Handling)**: When an existing device session token expires or becomes invalid (`401 Unauthorized`), StarX Mail now automatically clears the stale session and returns to the Resend connect screen instead of staying stuck showing `"Connected"`.
- **Settings Status & Reconnect Banner**: Accurately reflects `"Reconnect Needed"` with a direct reconnect button if account state is missing/disconnected, and fixes two-line text wrapping on the `+ Add Domain` button.
- **Smart Domain Input Normalization**: Automatically strips `https://`, `http://`, `@` prefixes, paths, and whitespace on both Android and the backend (`POST /api/v1/setup/domains`) so pasting a URL or email domain works cleanly.
- **Backend Vercel Export Fix**: Added default export to `backend/src/app.ts` and bumped Android `versionCode = 2` (`1.1.1`).

---

## 📦 Release Artifacts & Checksums

| Asset | Size | SHA-256 |
| :--- | :--- | :--- |
| **`app-release.apk`** (Signed, R8-optimized) | `5,200,133 bytes` (~4.96 MB) | `8231E8F92FFF5B735C2377CBFFA956334EE081EC2A3EA951B2C387DEAE3251B0` |
| **`app-debug.apk`** | `64,769,128 bytes` (~61.77 MB) | `A5F53D7F63738B950A07027E2AA9069768128E033D8DD5443C2EAEEED530AE1F` |

---

## ✅ Verification Summary
- **Backend Unit & Integration Tests**: `15/15` passing (`vitest`)
- **Android Unit Tests**: `10/10` passing (`./gradlew test`)
- **Live Production E2E Suite**: `11/11` passing against `https://starx-mail-backend.vercel.app`

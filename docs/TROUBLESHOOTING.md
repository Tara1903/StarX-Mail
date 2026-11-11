# Troubleshooting Guide — StarX Mail

This guide addresses common configuration and operational issues.

---

## 1. Authentication & Setup Issues

### "Invalid API key format. Key must start with re_"
- **Cause**: Resend API keys always begin with the prefix `re_`.
- **Solution**: Check your Resend Dashboard at [https://resend.com/api-keys](https://resend.com/api-keys) and copy the full key.

### "Authentication Error: The provided Resend API key is invalid or revoked"
- **Cause**: The key was either revoked, expired, or generated for a restricted team.
- **Solution**: Generate a new "Full Access" or "Sending & Inboxes" API key in the Resend dashboard.

---

## 2. Inbound Email & Receiving

### Incoming emails do not appear immediately in the app
- **Check 1: Webhook URL**: Ensure the backend's `BACKEND_PUBLIC_URL` is reachable from the public internet. If testing locally, use a tunneling service such as ngrok or cloudflared:
  ```bash
  ngrok http 3000
  ```
  Then enter the ngrok HTTPS URL in Advanced Settings during onboarding or in `.env`.
- **Check 2: Domain MX Records**: For custom domains, verify that your DNS registrar has configured:
  - **Type**: `MX`
  - **Host**: `@` (or subdomain)
  - **Value**: `feedback-smtp.resend.com`
  - **Priority**: `10`
- **Check 3: Background Sync**: When FCM is not configured, the app uses periodic background synchronization with `WorkManager`. Pull down to refresh in the inbox to trigger an instant delta sync.

---

## 3. Android Network Connectivity

### "Unable to resolve host" or Connection Refused in Android Emulator
- **Cause**: `localhost` inside the Android emulator refers to the emulator device itself, not your host computer.
- **Solution**: Use `http://10.0.2.2:3000/api/v1/` which maps directly to `localhost:3000` on your development workstation. This is configured as the default backend URL in StarX Mail.
- For physical Android devices connected via Wi-Fi or USB debugging, use your machine's local LAN IP address (e.g. `http://192.168.1.150:3000/api/v1/`).

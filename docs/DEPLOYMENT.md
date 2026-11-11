# Deployment Guide — StarX Mail

This guide explains how to deploy the StarX Mail backend and compile the production Android release APK.

---

## 1. Backend Deployment

The backend service is designed to be deployed to **Vercel**, **Docker**, or any Node.js environment.

### 1.1 Vercel Deployment (Recommended Serverless)
1. Install the Vercel CLI:
   ```bash
   npm install -g vercel
   ```
2. In the `backend/` directory, log in and deploy:
   ```bash
   cd backend
   vercel
   ```
3. Set the production environment variables in your Vercel Project Settings:
   - `NODE_ENV`: `production`
   - `JWT_SECRET`: A secure 32+ character random string (e.g. `openssl rand -hex 32`)
   - `BACKEND_PUBLIC_URL`: Your Vercel deployment URL (e.g. `https://starx-mail-backend.vercel.app`)
   - `RESEND_API_KEY`: *(Optional)* If pre-configuring a master key for your organization.
4. Deploy to production:
   ```bash
   vercel --prod
   ```

### 1.2 Docker / Self-Hosted Deployment
You can run the backend as a containerized service:
```dockerfile
FROM node:20-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM node:20-alpine AS runner
WORKDIR /app
ENV NODE_ENV=production
COPY package*.json ./
RUN npm ci --only=production
COPY --from=builder /app/dist ./dist
EXPOSE 3000
CMD ["node", "dist/index.js"]
```

Run with Docker:
```bash
docker build -t starx-mail-backend .
docker run -d -p 3000:3000 \
  -e BACKEND_PUBLIC_URL="https://mail.yourcompany.com" \
  -v /var/data/starx:/app/data \
  starx-mail-backend
```

---

## 2. Android Build & Release Configuration

### 2.1 Release Signing Keystore
To generate a production release keystore:
```bash
keytool -genkey -v -keystore starx-release.keystore \
  -alias starx-mail -keyalg RSA -keysize 2048 -validity 10000
```

Add your keystore credentials into `android/local.properties` (never commit this file):
```properties
RELEASE_STORE_FILE=starx-release.keystore
RELEASE_STORE_PASSWORD=your_keystore_password
RELEASE_KEY_ALIAS=starx-mail
RELEASE_KEY_PASSWORD=your_key_password
```

### 2.2 Compile Release APK
```bash
cd android
./gradlew assembleRelease
```
The resulting signed release APK will be generated at:
`android/app/build/outputs/apk/release/app-release-unsigned.apk` (or signed if credentials are provided).

### 2.3 Compile Debug APK
To build and install the debug APK directly onto a connected device or emulator:
```bash
cd android
./gradlew assembleDebug
```
The debug APK is located at:
`android/app/build/outputs/apk/debug/app-debug.apk`

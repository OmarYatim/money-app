# Environments — Secrets, Config, Powens

Read this file when touching environment variables, secrets, deployment config, or Powens integration.

---

## Environment Map

| | Local Dev | Live (Render) | Live (Azure) |
|---|---|---|---|
| **Backend** | `localhost:8080` | Render Web Service | Azure App Service B1 |
| **Public tunnel** | `local.nexioo.me` (Cloudflare) | — | — |
| **Database** | Docker PostgreSQL | Render PostgreSQL | Azure PostgreSQL Flexible |
| **Frontend** | `localhost:4200` | Vercel (`main`) | Vercel (`main`) |
| **Domain** | `local.nexioo.me` (backend only) | `nexioo.me` | `nexioo.me` |
| **API domain** | `local.nexioo.me` | `api.nexioo.me` | `api.nexioo.me` |
| **Spring profile** | `dev` | env vars on platform | env vars on platform |
| **Deploy trigger** | manual | merge to `main` | merge to `main` |

The Cloudflare tunnel (`local.nexioo.me`) is only active when `cloudflared tunnel run moneyapp-local` is running on your machine. Start it before running the backend when you need Powens flows (Webview redirect, webhooks). It is not needed for backend tasks that do not involve Powens.

---

## Config Files

| File | Committed? | Purpose |
|---|---|---|
| `backend/src/main/resources/application.yml` | ✅ Yes | Shared config, `${ENV_VAR}` placeholders only |
| `backend/src/main/resources/application-dev.yml` | ❌ No — gitignored | Real local dev values |

**Never create, overwrite, or read `application-dev.yml`.**  
**Never hardcode any value that belongs in an environment variable.**

Run locally with: `mvn spring-boot:run -Dspring-boot.run.profiles=dev`

The plugin flag `-Dspring-boot.run.profiles=dev` forwards the profile to the app's forked JVM. The plain `-Dspring.profiles.active=dev` form sets the property on Maven's JVM only — the app boots without the dev profile and Hikari fails with `'url' must start with "jdbc"`. Use the env var `SPRING_PROFILES_ACTIVE=dev` if you prefer.

### `.env` key format

`backend/.env` is loaded as a Java properties file via `spring.config.import`. Keys are taken **literally** — Spring's relaxed binding (uppercase-underscore → dotted-lowercase) only applies to actual shell env vars and system properties, NOT to keys read from a properties file. So:

```
spring.security.user.name=dev@nexioo.me      ✅ picked up
SPRING_SECURITY_USER_NAME=dev@nexioo.me      ❌ ignored
```

Use the dotted form for any Spring property you want to override from `.env`. Bare `${PLACEHOLDER}` lookups in `application-dev.yml` (like `${DEV_DB_URL}`) work either way because they match the literal key.

`POWENS_DOMAIN` is the bare host (`xxx-sandbox.biapi.pro`) — no `https://`, no trailing slash. The code prepends `https://` and appends `/2.0`.

### Temporary Dev Auth Flag

```properties
APP_AUTH_ENABLED=false
```

This flag keeps backend reads/writes pinned to the single seeded local app user while account authentication is still unfinished. Set it to `true` only when real authenticated user resolution is in place, then remove the flag entirely once the auth flow has been fully developed.

---

## Required Environment Variables

```
DB_URL              jdbc:postgresql://host:5432/moneyapp
DB_USERNAME
DB_PASSWORD
APP_FRONTEND_URL    https://nexioo.me
APP_CORS_ALLOWED_ORIGINS https://nexioo.me,https://www.nexioo.me
JWT_SECRET          minimum 256-bit random string
POWENS_DOMAIN       myapp-sandbox.biapi.pro
POWENS_CLIENT_ID
POWENS_CLIENT_SECRET
POWENS_MANAGE_TOKEN
POWENS_REDIRECT_URL https://api.nexioo.me/api/bank/callback
```

All of these must exist as GitHub Secrets for CI/CD, and as platform environment variables on Render/Azure.

---

## Angular Environment Files

```typescript
// src/environments/environment.ts (dev — proxy handles routing)
export const environment = {
  production: false,
  apiBaseUrl: ''
};

// src/environments/environment.production.ts
export const environment = {
  production: true,
  apiBaseUrl: 'https://api.nexioo.me'
};
```

Never hardcode `https://api.nexioo.me` directly in a service — always use `environment.apiBaseUrl`.

---

## Powens Integration

**Base URL:** `https://{POWENS_DOMAIN}/2.0/` — domain from config, never hardcoded.

**Auth header on every Powens call:**
```
Authorization: Bearer {user.powensToken}
```
Token is stored in `AppUser.powensToken` in the DB. Never hardcode it.

**Webview flow:**
```
Backend generates temp code via POST /auth/token/code
  → builds redirect URL
  → returns it to Angular
Angular does: window.location.href = webviewUrl   ← NOT Angular Router
  → user completes bank consent on Powens-hosted page
  → Powens redirects to: https://api.nexioo.me/api/bank/callback
```

**Registered URLs in Powens Console — register all four upfront:**

| Environment | Redirect URI | Webhook URL |
|---|---|---|
| **Local dev** | `https://local.nexioo.me/api/bank/callback` | `https://local.nexioo.me/webhooks/powens` |
| **Production** | `https://api.nexioo.me/api/bank/callback` | `https://api.nexioo.me/webhooks/powens` |

`local.nexioo.me` is a Cloudflare Tunnel pointing to `localhost:8080`. See [`powens.md`](powens.md) for setup.  
The `POWENS_REDIRECT_URL` environment variable must match the environment: `local.nexioo.me` locally, `api.nexioo.me` on deployed platforms.

---

## GitHub Secrets — Required for CI/CD

```
DB_URL
DB_PASSWORD
JWT_SECRET
POWENS_CLIENT_ID
POWENS_CLIENT_SECRET
POWENS_MANAGE_TOKEN
POWENS_REDIRECT_URL
APP_FRONTEND_URL
APP_CORS_ALLOWED_ORIGINS
GHCR_TOKEN                    GitHub Personal Access Token (read:packages + write:packages)
RENDER_DEPLOY_HOOK_URL        From Render dashboard → Settings → Deploy Hook
AZURE_WEBAPP_PUBLISH_PROFILE  From Azure App Service → Deployment Center (end of project only)
```

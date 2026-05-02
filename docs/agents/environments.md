# Environments — Secrets, Config, Powens

Read this file when touching environment variables, secrets, deployment config, or Powens integration.

---

## Environment Map

| | Local Dev | Live (Render) | Live (Azure) |
|---|---|---|---|
| **Backend** | `localhost:8080` | Render Web Service | Azure App Service B1 |
| **Public tunnel** | `local.moneyapp.me` (Cloudflare) | — | — |
| **Database** | Docker PostgreSQL | Render PostgreSQL | Azure PostgreSQL Flexible |
| **Frontend** | `localhost:4200` | Vercel (`main`) | Vercel (`main`) |
| **Domain** | `local.moneyapp.me` (backend only) | `moneyapp.me` | `moneyapp.me` |
| **API domain** | `local.moneyapp.me` | `api.moneyapp.me` | `api.moneyapp.me` |
| **Spring profile** | `dev` | env vars on platform | env vars on platform |
| **Deploy trigger** | manual | merge to `main` | merge to `main` |

The Cloudflare tunnel (`local.moneyapp.me`) is only active when `cloudflared tunnel run moneyapp-local` is running on your machine. Start it before running the backend when you need Powens flows (Webview redirect, webhooks). It is not needed for backend tasks that do not involve Powens.

---

## Config Files

| File | Committed? | Purpose |
|---|---|---|
| `backend/src/main/resources/application.yml` | ✅ Yes | Shared config, `${ENV_VAR}` placeholders only |
| `backend/src/main/resources/application-dev.yml` | ❌ No — gitignored | Real local dev values |

**Never create, overwrite, or read `application-dev.yml`.**  
**Never hardcode any value that belongs in an environment variable.**

Run locally with: `mvn spring-boot:run -Dspring.profiles.active=dev`

---

## Required Environment Variables

```
DB_URL              jdbc:postgresql://host:5432/moneyapp
DB_USERNAME
DB_PASSWORD
JWT_SECRET          minimum 256-bit random string
POWENS_DOMAIN       myapp-sandbox.biapi.pro
POWENS_CLIENT_ID
POWENS_CLIENT_SECRET
POWENS_MANAGE_TOKEN
POWENS_REDIRECT_URL https://api.moneyapp.me/api/bank/callback
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
  apiBaseUrl: 'https://api.moneyapp.me'
};
```

Never hardcode `https://api.moneyapp.me` directly in a service — always use `environment.apiBaseUrl`.

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
  → Powens redirects to: https://api.moneyapp.me/api/bank/callback
```

**Registered URLs in Powens Console — register all four upfront:**

| Environment | Redirect URI | Webhook URL |
|---|---|---|
| **Local dev** | `https://local.moneyapp.me/api/bank/callback` | `https://local.moneyapp.me/webhooks/powens` |
| **Production** | `https://api.moneyapp.me/api/bank/callback` | `https://api.moneyapp.me/webhooks/powens` |

`local.moneyapp.me` is a Cloudflare Tunnel pointing to `localhost:8080`. See [`powens.md`](powens.md) for setup.  
The `POWENS_REDIRECT_URL` environment variable must match the environment: `local.moneyapp.me` locally, `api.moneyapp.me` on deployed platforms.

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
GHCR_TOKEN                    GitHub Personal Access Token (read:packages + write:packages)
RENDER_DEPLOY_HOOK_URL        From Render dashboard → Settings → Deploy Hook
AZURE_WEBAPP_PUBLISH_PROFILE  From Azure App Service → Deployment Center (end of project only)
```

# Powens Integration Rules

Read this file for any task that involves the Powens Open Finance API.

---

## What Powens Is

Powens is a third-party Open Finance API (PSD2-compliant). It handles bank selection, user authentication, and consent. We call it to read financial data.  
We are **read-only** — no payments, no bank credentials ever touch our code.  
We use the **sandbox environment** for all current development.

---

## Local Development — Full Flow Testing

Testing the complete Powens flow locally (Webview redirect + webhooks + real bank data) requires a permanent stable HTTPS URL pointing to `localhost:8080`. Without it, Powens cannot redirect back after bank consent and cannot deliver webhooks.

**The solution is a Cloudflare Tunnel at `local.nexioo.me`.** Setup is a one-time task — see the **🟠 LOCAL DEV SETUP** checklist on the INIT-02 Trello card for the full step-by-step instructions.

Once set up, your daily workflow is:

```bash
# Start before the backend whenever you need to test Powens flows
cloudflared tunnel run nexioo-local
# local.nexioo.me → localhost:8080 is now live
```

You do not need the tunnel running for tasks that do not involve Powens — dashboard calculations, transaction filtering, categorisation, goal tracking, and reports all work on pure localhost.

### Registered URLs in Powens Console

| Environment | Redirect URI | Webhook URL |
|---|---|---|
| **Local dev** | `https://local.nexioo.me/api/bank/callback` | `https://local.nexioo.me/webhooks/powens` |
| **Production** | `https://api.nexioo.me/api/bank/callback` | `https://api.nexioo.me/webhooks/powens` |

Both redirect URIs co-exist — Powens accepts whichever matches the `redirect_uri` sent in the Webview request.  
Note: Powens supports only one active webhook URL at a time. Use the local URL during development, switch to production when deploying.

### Local Testing Sequence

```
1. docker-compose up -d
2. cloudflared tunnel run nexioo-local
3. mvn spring-boot:run -Dspring-boot.run.profiles=dev
4. Open browser → localhost:4200 → Connect Bank
5. Select "Connecteur de test" in the Powens Webview (sandbox fake bank)
6. Complete consent → Powens redirects to local.nexioo.me/api/bank/callback
7. Backend stores connection IDs → all subsequent data flows work locally
```

---

## Base URL

```
https://{POWENS_DOMAIN}/2.0/
```

`POWENS_DOMAIN` comes from config — `application-dev.yml` locally, environment variable on deployed platforms. Never hardcode.

---

## Authentication

Every call to Powens uses the user's permanent access token:

```
Authorization: Bearer {user.powensToken}
```

Stored in `AppUser.powensToken` in the database. Never hardcode. Never log. Never return to the frontend.

---

## HTTP Client — WebClient Only

Always use Spring's `WebClient`. Never use `RestTemplate`.

```java
powensWebClient.get()
    .uri("/users/me/accounts")
    .header(HttpHeaders.AUTHORIZATION, "Bearer " + user.getPowensToken())
    .retrieve()
    .bodyToMono(PowensAccountsResponse.class)
    .block();
```

The `WebClient` bean is in `com.moneyapp.config.WebClientConfig`.

---

## Calls From Backend Only

Never call the Powens API from Angular. All Powens calls go through a backend `Service` class.

```
Angular → Spring Controller → Spring Service → Powens API
```

---

## User Provisioning

Before the webview flow, every new `AppUser` needs a permanent Powens identity. Endpoint is `POST /auth/init` (NOT `/auth/token/access` — that one is for OAuth code exchange and will return `400 invalid_client` here).

```
POST /auth/init
Authorization: Bearer {POWENS_MANAGE_TOKEN}
Body: { "client_id": ..., "client_secret": ... }   ← both required
Response (flat): { "auth_token": "...", "type": "permanent", "id_user": 123 }
```

Persist `auth_token` as `AppUser.powensToken` and `id_user` as `AppUser.powensUserId`.

---

## Webview Flow — Bank Connection

```
1. Backend calls GET /auth/token/code with user's permanent token → gets temp code
   (it's GET — POST returns 405)
2. Backend builds Webview URL — note this is a different host:
   https://webview.powens.com/{LOCALE}/connect
     ?domain={POWENS_DOMAIN}            ← required, the API domain
     &client_id={CLIENT_ID}
     &redirect_uri={REDIRECT_URI}       ← local.nexioo.me or api.nexioo.me
     &code={temp_code}
     &state={csrf_state}
3. Backend returns URL to Angular
4. Angular: window.location.href = webviewUrl  ← external redirect, NOT Angular Router
5. User completes bank consent on Powens-hosted page
6. Powens redirects to the registered redirect_uri with:
     ?connection_id=14         ← singular for one connection
     ?connection_ids=14,15     ← plural for multiple — accept both forms
     &state=...
7. Backend callback handler stores connection IDs and fetches accounts
```

Do NOT build the webview URL on `https://{POWENS_DOMAIN}/auth/webview/connect` — that path 301-redirects to a broken target inside `webview.powens.com` and the SPA renders "Missing or invalid 'client_id' parameter" no matter what you pass.

---

## Webhooks — Do You Need Them During Development?

**No — webhooks are optional until you work on US-07 (Reliable Data Syncing).**

For all earlier features (dashboard, transactions, goals, categories, reports), you pull data manually by calling the Powens API directly from your backend. The data is there — you just fetch it on demand rather than receiving it pushed. No tunnel needed for this phase.

**When to register the webhook URL:**
Only when you start implementing the sync feature (US-07). At that point register `https://local.nexioo.me/webhooks/powens` in the Powens Console and use it for local testing.

**Important — webhooks only fire for permanent users:**
Powens only sends webhook events for users created via `POST /auth/init` (permanent auth token). If you test with a temporary session, no webhook will ever arrive — no error, just silence. Always make sure the test user has a permanent token before expecting webhooks.

**How to test the webhook handler locally:**
1. Start the tunnel: `cloudflared tunnel run nexioo-local`
2. Start the backend: `mvn spring-boot:run -Dspring-boot.run.profiles=dev`
3. Register `https://local.nexioo.me/webhooks/powens` in the Powens Console → Webhooks section for the `CONNECTION_SYNCED` event
4. In the Powens Console, click **"Send Test"** next to the webhook — this fires a test payload to your local backend
5. Check your backend logs — you should see the webhook received and a `SyncEvent` row created in the database
6. If nothing arrives: confirm the tunnel is running, confirm the URL is correctly registered, confirm the test user has a permanent token



**Hard rule: return HTTP 200 immediately. All processing is asynchronous.**

```java
@PostMapping("/webhooks/powens")
public ResponseEntity<Void> handleWebhook(@RequestBody Map<String, Object> payload) {
    syncService.processAsync(payload);   // @Async — returns before processing completes
    return ResponseEntity.ok().build();  // always 200, immediately
}
```

If our server takes too long, Powens retries and gives up after 10 consecutive failures. Never block the webhook endpoint.

---

## Sandbox vs Production

Only the domain and credentials differ. No code changes needed to switch environments.

| | Local dev | Production |
|---|---|---|
| Domain | `myapp-sandbox.biapi.pro` | same sandbox for now |
| Redirect URI | `https://local.nexioo.me/api/bank/callback` | `https://api.nexioo.me/api/bank/callback` |
| Webhook | `https://local.nexioo.me/webhooks/powens` | `https://api.nexioo.me/webhooks/powens` |
| Config source | `application-dev.yml` | Platform environment variables |

---

## Response Shape Gotchas

- **Timestamps:** Powens returns `last_update` (and similar fields) as `"2026-05-03 01:10:14"` — space separator, no `T`. Jackson's default ISO-8601 parser rejects it. Annotate `LocalDateTime` fields with `@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")`.
- **Nullable "primitives":** Fields that look booleans/numbers (e.g. `disabled`) can come back as JSON `null`. Declare them as boxed types (`Boolean`, `Long`) in DTOs and coalesce to a default at the mapper boundary, not as Java primitives — Jackson will throw `Cannot map null into type boolean` otherwise.
- **Object-shaped "scalars":** Some fields that *look* scalar are actually nested objects. `currency` is the prime example — Powens returns `{"id":"EUR","symbol":"€",…}`, not the bare `"EUR"`. Model them as a small nested record on the Powens DTO and project to a flat field in the entity at the mapper boundary.
- **Aliased ID fields:** Powens uses `id_user` in some responses, `user_id` in others. Use `@JsonProperty` + `@JsonAlias` to accept both.

---

## Do Not

- Call the Powens API from Angular
- Hardcode `POWENS_DOMAIN` or any credential
- Log `powensToken`, `client_secret`, or `manage_token`
- Return the user's `powensToken` to the frontend
- Block the webhook endpoint while processing — always `@Async`
- Use `RestTemplate` — always `WebClient`
- Store full IBAN or account numbers — store last 4 digits only (GDPR)

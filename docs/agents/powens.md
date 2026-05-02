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

**The solution is a Cloudflare Tunnel at `local.moneyapp.me`.** Setup is a one-time task — see the **🟠 LOCAL DEV SETUP** checklist on the INIT-02 Trello card for the full step-by-step instructions.

Once set up, your daily workflow is:

```bash
# Start before the backend whenever you need to test Powens flows
cloudflared tunnel run moneyapp-local
# local.moneyapp.me → localhost:8080 is now live
```

You do not need the tunnel running for tasks that do not involve Powens — dashboard calculations, transaction filtering, categorisation, goal tracking, and reports all work on pure localhost.

### Registered URLs in Powens Console

| Environment | Redirect URI | Webhook URL |
|---|---|---|
| **Local dev** | `https://local.moneyapp.me/api/bank/callback` | `https://local.moneyapp.me/webhooks/powens` |
| **Production** | `https://api.moneyapp.me/api/bank/callback` | `https://api.moneyapp.me/webhooks/powens` |

Both redirect URIs co-exist — Powens accepts whichever matches the `redirect_uri` sent in the Webview request.  
Note: Powens supports only one active webhook URL at a time. Use the local URL during development, switch to production when deploying.

### Local Testing Sequence

```
1. docker-compose up -d
2. cloudflared tunnel run moneyapp-local
3. mvn spring-boot:run -Dspring.profiles.active=dev
4. Open browser → localhost:4200 → Connect Bank
5. Select "Connecteur de test" in the Powens Webview (sandbox fake bank)
6. Complete consent → Powens redirects to local.moneyapp.me/api/bank/callback
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

## Webview Flow — Bank Connection

```
1. Backend calls POST /auth/token/code with user's permanent token → gets temp code
2. Backend builds Webview URL:
   https://{POWENS_DOMAIN}/auth/webview/connect
     ?client_id={CLIENT_ID}
     &redirect_uri={REDIRECT_URI}    ← local.moneyapp.me or api.moneyapp.me
     &code={temp_code}
3. Backend returns URL to Angular
4. Angular: window.location.href = webviewUrl  ← external redirect, NOT Angular Router
5. User completes bank consent on Powens-hosted page
6. Powens redirects to the registered redirect_uri + ?connection_ids=...
7. Backend callback handler stores connection IDs and fetches accounts
```

---

## Webhooks

Powens sends `POST` to our registered webhook URL when new bank data is available.

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
| Redirect URI | `https://local.moneyapp.me/api/bank/callback` | `https://api.moneyapp.me/api/bank/callback` |
| Webhook | `https://local.moneyapp.me/webhooks/powens` | `https://api.moneyapp.me/webhooks/powens` |
| Config source | `application-dev.yml` | Platform environment variables |

---

## Do Not

- Call the Powens API from Angular
- Hardcode `POWENS_DOMAIN` or any credential
- Log `powensToken`, `client_secret`, or `manage_token`
- Return the user's `powensToken` to the frontend
- Block the webhook endpoint while processing — always `@Async`
- Use `RestTemplate` — always `WebClient`
- Store full IBAN or account numbers — store last 4 digits only (GDPR)

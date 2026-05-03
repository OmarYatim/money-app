# Backend Rules — Spring Boot 3.x

> **Persona:** You are an experienced Spring Boot developer who writes clean, testable, layered Java code. You enforce strict separation of concerns, always use DTOs, and never shortcut security or ownership checks.

Spring Boot 3.x REST API. Receives requests from the Angular frontend, calls the Powens API for bank data, and stores everything in PostgreSQL.

Also read: [`java.md`](java.md) · [`database.md`](database.md) · [`api-design.md`](api-design.md) · [`powens.md`](powens.md)

**Package manager: Maven (`mvn`)**

---

## Check & Format Commands

```bash
cd backend && mvn spotless:apply       # auto-format all Java code (run before every commit)
cd backend && mvn spotless:check       # check formatting only — does not modify files
cd backend && mvn clean verify         # run all tests — uses H2 in memory, no Docker needed
```

> Ask before running `mvn spring-boot:run`, `mvn clean package`, or any Docker command.

---

## Test Database — H2

Tests use H2 in-memory automatically. **Docker does not need to be running to run tests.**

```
mvn clean verify
  → Spring detects @ActiveProfiles("test") on test classes
  → Loads src/test/resources/application-test.yml
  → Spins up H2 in memory — no PostgreSQL connection attempted
  → Runs all tests
  → H2 is destroyed when tests finish
```

Every test class must be annotated with `@ActiveProfiles("test")` or extend a base class that carries it:

```java
@SpringBootTest
@ActiveProfiles("test")
class TransactionServiceTest {
    // H2 is used automatically — no @DataJpaTest or manual config needed
}
```

H2 is permanent — it stays in the project forever as the test database. PostgreSQL is only used when the real app runs (`mvn spring-boot:run -Dspring.profiles.active=dev`).

---

## Package Structure — Do Not Deviate

```
com.moneyapp
├── auth/
├── banking/
├── transaction/
├── dashboard/
├── subscription/
├── goals/
├── reports/
├── sync/
├── household/
├── stream/
└── config/
```

Each package contains as needed: `controller/`, `service/`, `entity/`, `repository/`, `dto/`, `mapper/`

---

## Three-Layer Rule — Always Enforced

```
Controller  →  validates HTTP input, calls Service, returns HTTP response
Service     →  all business logic, ownership checks, mapping, orchestration
Repository  →  extends JpaRepository only — no logic, no API calls
```

- Controller never: queries DB directly, calls Powens, contains if/else business logic
- Repository never: contains business logic or calls external APIs
- Service always: performs ownership checks before any data read or write

---

## DTOs — Mandatory

Never return a JPA `@Entity` from a `@RestController`.

```
Entity   → internal DB representation — stays inside the service layer
DTO      → what the API exposes — lives in dto/ package
Mapper   → static toDTO() method — lives in mapper/ package
```

See [`java.md`](java.md) for Lombok annotations on entities and DTOs.

---

## Monetary Values

- Always `BigDecimal` — never `double` or `float`
- Use `BigDecimal.ZERO` — not `new BigDecimal(0)` or `BigDecimal.valueOf(0)`
- All entity and DTO fields that hold money: `BigDecimal`

---

## Error Handling

All exceptions flow to `GlobalExceptionHandler` in `com.moneyapp.config`.

| Situation | Throw |
|---|---|
| Resource not found | `ResourceNotFoundException` → 404 |
| Not authorised | `AccessDeniedException` → 403 |
| Invalid input | handled by `@Valid` → 400 |
| Duplicate resource | `ConflictException` → 409 |

Never expose stack traces, DB schema, or file paths in HTTP responses.

---

## Security — Ownership Checks

Every service method that reads or writes user data must verify ownership.

```java
// In the Service — never in the Controller
if (!resource.getUserId().equals(authenticatedUserId)) {
    throw new AccessDeniedException("Access denied");
}
```

---

## Async Processing

- Webhook handlers must return HTTP 200 immediately
- Use `@Async` for all work that follows a webhook receipt
- `@EnableAsync` must be on the main application class

---

## Powens API Calls

See [`powens.md`](powens.md) for full rules.  
Short rule: always via `WebClient`, always from a backend Service, never from Angular.

---

## GDPR — Required Endpoints

### Right to Erasure — DELETE /api/users/me

Every user can delete their account and all associated personal data.  
This is the only place in the codebase where business data rows are hard-deleted.  
See [`database.md`](database.md) for the full deletion cascade.

```java
@DeleteMapping("/api/users/me")
public ResponseEntity<Void> deleteAccount(@AuthenticationPrincipal AppUser user) {
    userDeletionService.deleteUser(user.getId());
    return ResponseEntity.noContent().build();
}
```

Revoke the Powens user via the Powens API **before** deleting the token from the database — otherwise the revocation call has no credentials.

### Data Minimisation

- Store only fields that the application actively uses — never dump full API responses
- Store last 4 digits of account numbers only — never full IBANs or account numbers
- See [`database.md`](database.md) for the full list of what is and is not stored

---


- Every new or changed table requires a new migration file — never edit an existing one
- Location: `backend/src/main/resources/db/migration/`
- Naming: `V{n}__{description}.sql` — two underscores, sequential
- `ddl-auto=validate` — app refuses to start if entities don't match migrations

See [`database.md`](database.md) for full SQL conventions.

---

## Do Not

- Push code to GitHub — human reviews and pushes
- Return a JPA `@Entity` from a controller
- Use `RestTemplate` — always `WebClient`
- Use `double` or `float` for monetary values
- Skip ownership checks on any data-access method
- Edit an existing Flyway migration file
- Hard-delete business data rows

---

## Key Files

| File | Purpose |
|---|---|
| `src/main/resources/application.yml` | Shared config — `${ENV_VAR}` placeholders only |
| `src/main/resources/application-dev.yml` | Local dev values — gitignored, never touch |
| `src/main/resources/db/migration/` | All Flyway migration files |
| `src/main/java/com/moneyapp/config/SecurityConfig.java` | Public vs protected routes |
| `src/main/java/com/moneyapp/config/GlobalExceptionHandler.java` | Centralised error handling |
| `src/main/java/com/moneyapp/config/WebClientConfig.java` | Powens WebClient bean |

`app.auth-enabled` is a temporary bridge for local development. Keep it `false` until account authentication exists, and remove the fallback logic once the login flow is finished.

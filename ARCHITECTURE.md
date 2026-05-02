# 🏗️ Money App — Architecture Guide & Coding Standards

> **Who is this document for?**
> Every developer on the team — especially interns and junior developers joining the project for the first time. Read this before writing a single line of code. It explains how the project is structured, why decisions were made, and the rules we all follow to keep the codebase clean and consistent.

---

## Table of Contents
1. [Project Overview](#1-project-overview)
2. [Tech Stack](#2-tech-stack)
3. [System Architecture](#3-system-architecture)
4. [Backend Architecture (Spring Boot)](#4-backend-architecture-spring-boot)
5. [Frontend Architecture (Angular)](#5-frontend-architecture-angular)
6. [Database Architecture](#6-database-architecture)
7. [Authentication & Security](#7-authentication--security)
8. [API Design Rules](#8-api-design-rules)
9. [Coding Standards & Best Practices](#9-coding-standards--best-practices)
10. [Git & Branching Strategy](#10-git--branching-strategy)
11. [Testing Strategy](#11-testing-strategy)
12. [Common Mistakes to Avoid](#12-common-mistakes-to-avoid)

---

## 1. Project Overview

Money App is a personal finance management application that connects to users' bank accounts via the **Powens Open Finance API**, aggregates their financial data, and presents it through a clean dashboard with transaction tracking, budgeting, savings goals, and reports.

### What We Build
- A **Spring Boot** REST API backend
- An **Angular** single-page application frontend
- A **PostgreSQL** database for persistent storage
- Integration with the **Powens** banking API (we never touch bank credentials directly)

### What We Do NOT Build
- A mobile app (the Angular web app is responsive and works on mobile browsers)
- Our own bank connector (Powens handles all bank communication)
- Any payment processing (we are read-only for all banking data)

---

## 2. Tech Stack

| Layer | Technology | Version | Why |
|---|---|---|---|
| Backend language | Java | 17 (LTS) | Stable, widely used, strong typing |
| Backend framework | Spring Boot | 3.x | Industry standard for Java REST APIs |
| Frontend framework | Angular | 17+ | Structured, opinionated, great for teams |
| UI component library | Angular Material | Latest | Pre-built, accessible, consistent UI |
| Database | PostgreSQL | 15 | Production-grade, free, excellent for complex queries |
| DB migrations | Flyway | Latest | Version-controls every database change |
| HTTP client (backend) | Spring WebClient | Built-in | Reactive HTTP client for calling Powens API |
| HTTP client (frontend) | Angular HttpClient | Built-in | Built into Angular, works with interceptors |
| Authentication | JWT (JJWT library) | 0.12.x | Stateless, works well with REST APIs |
| State management | NgRx | Latest | Predictable state for complex Angular apps |
| Charts | ng2-charts (Chart.js) | Latest | Works natively with Angular |
| Containerisation | Docker | Latest | Consistent environments across all machines |
| CI/CD | GitHub Actions | N/A | Free, integrates directly with GitHub |

---

## 3. System Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        USER'S BROWSER                            │
│                                                                   │
│              Angular SPA (http://localhost:4200)                  │
│         All HTTP requests go to /api/* via proxy                 │
└───────────────────────┬─────────────────────────────────────────┘
                        │ HTTP (JSON)
                        ▼
┌─────────────────────────────────────────────────────────────────┐
│                  Spring Boot API (port 8080)                      │
│                                                                   │
│  ┌─────────────┐   ┌─────────────┐   ┌────────────────────────┐  │
│  │ Controllers │──▶│  Services   │──▶│     Repositories       │  │
│  │ (HTTP layer)│   │(business    │   │  (database queries)    │  │
│  │             │   │  logic)     │   │                        │  │
│  └─────────────┘   └──────┬──────┘   └───────────┬────────────┘  │
│                           │                       │               │
│                           │ (calls Powens API)    │ (JPA/SQL)     │
└───────────────────────────┼───────────────────────┼───────────────┘
                            │                       │
                            ▼                       ▼
┌─────────────────────┐    ┌────────────────────────────────────┐
│   Powens Platform   │    │       PostgreSQL Database           │
│  (external API)     │    │   (all our stored data lives here)  │
│  - /users/me/accounts│   └────────────────────────────────────┘
│  - /users/me/txns   │
│  - Webview (consent)│
└─────────────────────┘
```

### The Golden Rule of Data Flow
```
Angular → Spring Controller → Spring Service → Repository → PostgreSQL
                                    ↕
                              Powens API (only during sync)
```

Angular **never** calls Powens directly. Everything goes through our backend.

---

## 4. Backend Architecture (Spring Boot)

### 4.1 Package Structure

Every feature has its own package. Do not mix code from different features.

```
com.moneyapp
├── auth/                    ← Login, JWT, user registration
│   ├── controller/
│   ├── service/
│   ├── entity/
│   └── repository/
├── banking/                 ← Powens connection, bank account linking
│   ├── controller/
│   ├── service/
│   ├── entity/
│   └── repository/
├── transaction/             ← Transactions, categories
│   ├── controller/
│   ├── service/
│   ├── entity/
│   ├── repository/
│   ├── dto/
│   └── spec/               ← JPA Specifications for dynamic filtering
├── dashboard/               ← Summary metrics
│   ├── controller/
│   ├── service/
│   └── dto/
├── subscription/            ← Recurring payments
│   ├── controller/
│   ├── service/
│   ├── entity/
│   └── repository/
├── goals/                   ← Savings goals
│   ├── controller/
│   ├── service/
│   ├── entity/
│   └── repository/
├── reports/                 ← Charts and analytics
│   ├── controller/
│   ├── service/
│   ├── scheduler/
│   └── dto/
├── sync/                    ← Webhook handler, scheduled sync
│   ├── controller/
│   ├── service/
│   ├── scheduler/
│   ├── entity/
│   └── repository/
├── household/               ← Collaboration and sharing
│   ├── controller/
│   ├── service/
│   ├── entity/
│   └── repository/
├── stream/                  ← Server-Sent Events
│   ├── controller/
│   └── service/
└── config/                  ← Spring Security, WebClient, CORS config
```

### 4.2 The Three-Layer Rule

Every backend feature follows exactly three layers. Never skip or merge them.

```
Layer 1: Controller  → Receives HTTP requests, validates input, calls Service, returns HTTP response
Layer 2: Service     → Contains all business logic, calls Repository or external APIs
Layer 3: Repository  → Talks to the database only, no business logic allowed here
```

**Example — correct:**
```java
// TransactionController.java (Layer 1)
@GetMapping("/{id}")
public ResponseEntity<TransactionDTO> getTransaction(@PathVariable Long id) {
    return ResponseEntity.ok(transactionService.findById(id)); // delegates to service
}

// TransactionService.java (Layer 2)
public TransactionDTO findById(Long id) {
    Transaction t = transactionRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));
    return TransactionMapper.toDTO(t); // business logic: map entity to DTO
}

// TransactionRepository.java (Layer 3)
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    // only database operations here
}
```

**Example — wrong (never do this):**
```java
// WRONG: business logic inside a controller
@GetMapping("/{id}")
public ResponseEntity<Transaction> getTransaction(@PathVariable Long id) {
    Transaction t = transactionRepository.findById(id).orElseThrow(); // ❌ bypass service
    t.setReviewed(true); // ❌ business logic in controller
    return ResponseEntity.ok(t); // ❌ returning raw entity, not DTO
}
```

### 4.3 DTOs — Always Use Them

A **DTO (Data Transfer Object)** is what we send to the frontend. It is NOT the same as the database entity.

- **Entity** = what's in the database (may have sensitive fields, internal fields)
- **DTO** = what we send to the API caller (only the fields they need)

```java
// Transaction.java (Entity — has all DB columns)
@Entity
public class Transaction {
    private Long id;
    private Long userId;          // ← NEVER expose this to frontend
    private String powensRawData; // ← internal field, don't expose
    private String label;
    private BigDecimal value;
    private String category;
    private boolean reviewed;
    // ... more fields
}

// TransactionDTO.java (DTO — only what the frontend needs)
public class TransactionDTO {
    private Long id;
    private String label;
    private BigDecimal value;
    private String category;
    private boolean reviewed;
    private LocalDate date;
    // No userId, no powensRawData
}
```

Create a `TransactionMapper` class with a static `toDTO(Transaction t)` method. Never do the mapping inside a controller or repository.

### 4.4 Error Handling

Create a single `GlobalExceptionHandler` class in `com.moneyapp.config` with `@RestControllerAdvice`. This class catches exceptions thrown anywhere in the app and converts them to clean JSON error responses.

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(404).body(new ErrorResponse("NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(AccessDeniedException ex) {
        return ResponseEntity.status(403).body(new ErrorResponse("FORBIDDEN", "You do not have permission"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        // Log the full stack trace here, but don't expose it to the client
        log.error("Unexpected error", ex);
        return ResponseEntity.status(500).body(new ErrorResponse("INTERNAL_ERROR", "Something went wrong"));
    }
}
```

**Rule:** Never let a raw Java exception message reach the client. Always use the `GlobalExceptionHandler`.

---

## 5. Frontend Architecture (Angular)

### 5.1 Folder Structure

```
src/app/
├── core/                    ← Singleton services used across the whole app
│   ├── auth/
│   │   ├── auth.service.ts          ← Manages login state and JWT tokens
│   │   ├── auth.interceptor.ts      ← Attaches JWT to every request
│   │   └── auth.guard.ts            ← Protects routes that need login
│   └── sse/
│       └── sse.service.ts           ← Manages the SSE connection
│
├── shared/                  ← Reusable components, pipes, and directives
│   ├── components/
│   │   ├── loading-spinner/         ← Reusable loading indicator
│   │   ├── empty-state/             ← "No data yet" placeholder
│   │   └── currency-display/        ← Formats and colours monetary values
│   └── pipes/
│       └── category-color.pipe.ts   ← Maps CategoryType → CSS class
│
├── features/                ← One folder per user story / feature
│   ├── accounts/
│   │   ├── account-list/
│   │   ├── account-connect/
│   │   ├── accounts.module.ts
│   │   └── account.service.ts
│   ├── transactions/
│   ├── dashboard/
│   ├── subscriptions/
│   ├── goals/
│   ├── reports/
│   └── household/
│
└── app-routing.module.ts    ← Defines all routes
```

### 5.2 Component Rules

**Smart vs Dumb Components:**
- **Smart components** (also called "container" components): fetch data from services, manage state, handle user events.
- **Dumb components** (also called "presentational" components): only receive data via `@Input()` and emit events via `@Output()`. They have no services injected.

```
dashboard.component.ts (smart) → fetches data from DashboardService
  → passes data down via @Input() to:
     summary-card.component.ts (dumb) → only displays, no HTTP calls
     net-worth-chart.component.ts (dumb) → only renders chart
```

**Why?** Dumb components are easy to test and easy to reuse. Smart components are easier to find when debugging data issues.

### 5.3 Services Rule

Every feature has ONE service file for all its HTTP calls. No component should call `HttpClient` directly — always go through a service.

```typescript
// transaction.service.ts
@Injectable({ providedIn: 'root' })
export class TransactionService {
  constructor(private http: HttpClient) {}

  getTransactions(params: TransactionFilter): Observable<Page<TransactionDTO>> {
    return this.http.get<Page<TransactionDTO>>('/api/transactions', { params: { ...params } });
  }

  markReviewed(id: number, reviewed: boolean): Observable<TransactionDTO> {
    return this.http.patch<TransactionDTO>(`/api/transactions/${id}/reviewed`, { reviewed });
  }
}
```

### 5.4 State Management with NgRx

We use NgRx for features with complex state (e.g. transaction filters that persist across navigation). For simple features (e.g. subscriptions list), using the service directly with an `Observable` is fine.

**Use NgRx when:**
- State is shared across multiple components
- State needs to persist when the user navigates away and comes back
- There are multiple actions that can change the same piece of state

**Skip NgRx when:**
- A component only needs data for itself and discards it when closed
- The data is simple and doesn't change often

### 5.5 Loading & Error States

Every component that makes an HTTP call must handle three states: loading, success, and error. Never show a blank screen.

```typescript
// In the component
loading = true;
error: string | null = null;
data: TransactionDTO[] = [];

ngOnInit() {
  this.transactionService.getTransactions({}).subscribe({
    next: (result) => {
      this.data = result.content;
      this.loading = false;
    },
    error: (err) => {
      this.error = 'Failed to load transactions. Please try again.';
      this.loading = false;
    }
  });
}
```

```html
<!-- In the template -->
<app-loading-spinner *ngIf="loading"></app-loading-spinner>
<app-empty-state *ngIf="!loading && data.length === 0 && !error"></app-empty-state>
<div class="error-banner" *ngIf="error">{{ error }}</div>
<div *ngIf="!loading && !error">
  <!-- actual content -->
</div>
```

---

## 6. Database Architecture

### 6.1 Migration Rules (Flyway)

Every change to the database (new table, new column, new index) must be a Flyway migration file. **Never use `ddl-auto: create` or `ddl-auto: update` in production — only `validate`.**

Migration files live in: `src/main/resources/db/migration/`

Naming convention: `V{number}__{description}.sql`
- `V1__create_app_user.sql`
- `V2__create_account_table.sql`
- `V3__add_reviewed_to_transaction.sql`

**Golden rules:**
1. Never edit an existing migration file after it has been run — Flyway will detect the checksum mismatch and refuse to start.
2. Always add a new migration file for every change — even adding a single column.
3. Write your SQL to be safe: use `IF NOT EXISTS` for table creation, `ALTER TABLE ADD COLUMN IF NOT EXISTS` for columns.

### 6.2 Key Tables Overview

```
app_user              ← Core user account (email, powens_token)
user_connection       ← Links user to Powens connection IDs
account               ← Bank accounts (balance, type, IBAN)
transaction           ← All transactions (synced from Powens)
subscription          ← Recurring charges (synced from Powens)
goal                  ← Savings goals
goal_contribution     ← Manual contributions to goals
goal_snapshot         ← Daily snapshots of goal progress
goal_milestone        ← Tracks which milestones have been celebrated
net_worth_snapshot    ← Daily net worth history (for reports)
sync_event            ← Log of every sync attempt
household_member      ← Collaboration invitations and roles
refresh_token         ← Stored refresh tokens (for secure logout)
```

### 6.3 Column Naming Convention

Use `snake_case` for all database column names (PostgreSQL convention):
- `created_at`, `updated_at` — on all tables
- `user_id` — foreign key to app_user
- `is_reviewed`, `is_disabled` — boolean columns prefixed with `is_`

In Java, JPA will map `created_at` → `createdAt` automatically.

---

## 7. Authentication & Security

### 7.1 How JWT Works in This Project

```
Login request → AuthController
  → Verify email + password
  → Generate access token (15-min expiry) + refresh token (30-day expiry)
  → Store refresh token in DB (refresh_token table)
  → Return access token in response body
     (on web: also set HttpOnly cookie with refresh token)

Every subsequent API request:
  → Angular's AuthInterceptor reads access token from memory
  → Adds "Authorization: Bearer {token}" header
  → Spring Security's JwtAuthFilter validates the token
  → Extracts userId from the token
  → Sets the authenticated user in Spring's SecurityContext

Access token expires:
  → Backend returns 401
  → AuthInterceptor catches it
  → Calls POST /api/auth/refresh with the refresh token
  → Gets new access token
  → Retries the original request
```

### 7.2 What Must Be Secured

| Endpoint pattern | Security requirement |
|---|---|
| `POST /api/auth/login` | Public — no auth needed |
| `POST /api/auth/refresh` | Public — no auth needed |
| `GET /invite/accept` | Public — no auth needed |
| `POST /webhooks/powens` | No user auth — verify Powens webhook signature instead |
| Everything else under `/api/**` | Requires valid JWT |

### 7.3 Security Rules — Non-Negotiable

1. **Never store JWT access tokens in `localStorage`** — it is vulnerable to XSS attacks. Store in memory (a private variable in `AuthService`).
2. **Never log JWT tokens or Powens tokens** — treat them as passwords.
3. **Never skip ownership checks** — every service method that accesses user data must verify that the requesting user owns that data.
4. **Always use parameterised queries** — Spring's JPA and JPQL handle this automatically. Never concatenate user input into SQL strings.
5. **Never expose internal error details** — use `GlobalExceptionHandler` to return generic error messages to the client.

---

## 8. API Design Rules

### 8.1 URL Conventions

All API URLs follow REST conventions:

| Pattern | Meaning | Example |
|---|---|---|
| `GET /api/{resources}` | Get a list | `GET /api/transactions` |
| `GET /api/{resources}/{id}` | Get one item | `GET /api/transactions/42` |
| `POST /api/{resources}` | Create new | `POST /api/goals` |
| `PUT /api/{resources}/{id}` | Replace entirely | `PUT /api/goals/5` |
| `PATCH /api/{resources}/{id}/{action}` | Partial update | `PATCH /api/transactions/42/reviewed` |
| `DELETE /api/{resources}/{id}` | Delete (or soft-delete) | `DELETE /api/goals/5` |

- Always use **plural nouns** for resource names: `/transactions`, `/accounts`, `/goals`
- Never use verbs in URLs: NOT `/getTransactions`, NOT `/markReviewed`
- Use **kebab-case** for multi-word resources: `/net-worth-history`, `/spending-by-category`

### 8.2 Response Format

Always return consistent JSON structures:

**Success (list):**
```json
{
  "content": [...],
  "totalElements": 247,
  "totalPages": 13,
  "page": 0,
  "size": 20
}
```

**Success (single object):**
```json
{
  "id": 42,
  "label": "Amazon Prime",
  "value": -8.99,
  "category": "SUBSCRIPTION"
}
```

**Error:**
```json
{
  "code": "NOT_FOUND",
  "message": "Transaction with id 42 not found"
}
```

### 8.3 HTTP Status Codes

| Code | When to use |
|---|---|
| `200 OK` | Successful GET, PATCH, PUT |
| `201 Created` | Successful POST (new resource created) |
| `204 No Content` | Successful DELETE |
| `400 Bad Request` | Invalid input (e.g. missing required field) |
| `401 Unauthorized` | Not logged in / invalid token |
| `403 Forbidden` | Logged in but not allowed to access this resource |
| `404 Not Found` | Resource doesn't exist |
| `409 Conflict` | Duplicate (e.g. inviting the same email twice) |
| `500 Internal Server Error` | Unexpected error (always log the stack trace) |

---

## 9. Coding Standards & Best Practices

### 9.1 Java / Spring Boot

**Naming conventions:**
- Classes: `PascalCase` — `TransactionService`, `GoalController`
- Methods and variables: `camelCase` — `findByUserId()`, `currentAmount`
- Constants: `UPPER_SNAKE_CASE` — `MAX_RETRY_ATTEMPTS = 3`
- Database columns: `snake_case` — `created_at`, `category_overridden`
- Packages: `lowercase.dot.separated` — `com.moneyapp.transaction`

**Code organisation:**
- One class per file — always.
- Keep methods short: if a method is longer than 30 lines, it is doing too much — split it.
- Every class and public method must have a one-line Javadoc comment explaining what it does.
- Use `@Slf4j` (Lombok) for logging. Never use `System.out.println()`.
- Use `log.info()` for normal operations, `log.warn()` for unexpected but handled situations, `log.error()` for failures that need attention.

**What Lombok annotations to use:**
```java
@Data           // generates getters, setters, equals, hashCode, toString
@NoArgsConstructor  // generates empty constructor
@AllArgsConstructor // generates constructor with all fields
@Builder        // enables builder pattern: MyClass.builder().field(value).build()
@Slf4j          // injects a `log` variable for logging
@RequiredArgsConstructor  // use on @Service classes — injects final fields via constructor
```

**Monetary values:** Always use `BigDecimal` for money. Never use `double` or `float` — they have rounding errors that will cause incorrect balances.

### 9.2 Angular / TypeScript

**Naming conventions:**
- Components: `PascalCase` + suffix — `TransactionListComponent`
- Services: `PascalCase` + suffix — `TransactionService`
- Files: `kebab-case` + type — `transaction-list.component.ts`, `transaction.service.ts`
- Variables and methods: `camelCase`
- Constants: `UPPER_SNAKE_CASE`
- Interfaces/models: `PascalCase` with no suffix — `Transaction`, `Goal`, `DashboardSummary`

**TypeScript rules:**
- Always declare the type of variables — never use `any`. Use `unknown` if you truly don't know the type.
- Every model/interface must be in a dedicated file in `src/app/shared/models/`
- Use `readonly` for properties that should not change after being set
- Use `async/await` or RxJS `Observables` consistently — don't mix them

**Template rules:**
- Always use `trackBy` with `*ngFor` for lists of items: `*ngFor="let t of transactions; trackBy: trackById"`
- Never put complex logic in templates — move it to the component class
- Use Angular's `CurrencyPipe` for all monetary display: `{{ amount | currency:'EUR' }}`
- Use Angular's `DatePipe` for all date display: `{{ date | date:'dd MMM yyyy' }}`

**RxJS rules:**
- Always unsubscribe from Observables to prevent memory leaks. The easiest way: use the `async` pipe in templates, or use `takeUntil(this.destroy$)` pattern in components.
- Use `switchMap` when a new event should cancel the previous request (e.g. search bar)
- Use `mergeMap` when requests should run in parallel and all results are needed

---

## 10. Git & Branching Strategy

### 10.1 Branches

```
main          ← Production code only. Only updated via release PR. Protected.
develop       ← Integration branch. All features merge here. Protected (requires PR + CI pass).
feature/...   ← Individual feature branches. Created from develop.
hotfix/...    ← Emergency bug fixes on production. Created from main.
```

### 10.2 Branch Naming

```
feature/US-01-bank-connection
feature/US-04-transaction-list
bugfix/US-03-wrong-net-worth-calculation
hotfix/auth-token-expiry-crash
```

### 10.3 Commit Message Format

Use the Conventional Commits format:

```
type(scope): short description

feat(transactions): add keyword search filter
fix(auth): handle token refresh on concurrent requests
refactor(goals): extract progress calculation to GoalProgressService
test(dashboard): add unit tests for net worth calculation
docs(readme): update local setup instructions
```

Types: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`, `style`

### 10.4 Pull Request Rules

1. **Every piece of code goes through a PR** — no direct pushes to `develop` or `main`.
2. **PR description must include:** what was done, how to test it, and a link to the Trello card.
3. **Keep PRs small** — one feature or one bug fix per PR. A PR touching 20 files is too big to review properly.
4. **CI must pass** before requesting review — don't waste a reviewer's time on broken code.
5. **At least 1 approval** required before merging.
6. **Delete the feature branch** after merging — keep the repo clean.

---

## 11. Testing Strategy

### 11.1 Backend Testing

**Unit tests** — test one class in isolation. Mock all dependencies.
- Every `Service` class must have unit tests.
- Target: test all business logic paths, including edge cases and error paths.
- Tool: JUnit 5 + Mockito (already included with `spring-boot-starter-test`)

```java
@ExtendWith(MockitoExtension.class)
class GoalProgressServiceTest {

    @Mock
    private GoalRepository goalRepository;

    @InjectMocks
    private GoalProgressService goalProgressService;

    @Test
    void shouldReturnZeroProgressWhenNoContributions() {
        Goal goal = new Goal();
        goal.setTargetAmount(new BigDecimal("1000"));
        goal.setCurrentAmount(BigDecimal.ZERO);

        GoalProgressDTO result = goalProgressService.computeProgress(goal);

        assertEquals(0.0, result.getProgressPercent());
        assertNull(result.getProjectedCompletionDate()); // no rate = no projection
    }
}
```

**Integration tests** — test the full stack with a real database.
- Use `@SpringBootTest` with an in-memory H2 database for integration tests.
- Test the most critical flows: account sync, transaction import, authentication.

### 11.2 Frontend Testing

**Unit tests** — test components and services in isolation.
- Every service must have unit tests (mock `HttpClient`).
- Test component logic, not the DOM.
- Tool: Jasmine + Karma (included with Angular CLI)

**Minimum coverage targets:**
- Services: 80% line coverage
- Service methods with conditional logic: 100% branch coverage

---

## 12. Common Mistakes to Avoid

### Backend
| ❌ Don't | ✅ Do instead |
|---|---|
| Put business logic in a Controller | Move it to a Service |
| Return a raw JPA Entity from an endpoint | Return a DTO |
| Use `double` for money | Use `BigDecimal` |
| Hardcode configuration values | Use `application.yml` + `@ConfigurationProperties` |
| Call Powens API on every dashboard load | Sync to local DB and query the DB |
| Edit an existing Flyway migration file | Create a new migration file |
| Use `System.out.println()` for logging | Use `@Slf4j` and `log.info()` |
| Expose stack traces in API error responses | Use `GlobalExceptionHandler` |
| Skip ownership checks | Always verify the requesting user owns the resource |

### Frontend
| ❌ Don't | ✅ Do instead |
|---|---|
| Call `HttpClient` directly from a component | Use a feature service |
| Store JWT in `localStorage` | Store in memory (AuthService) or HttpOnly cookie |
| Use `any` as a type | Define a proper TypeScript interface |
| Put complex logic in templates | Move to component class |
| Forget to unsubscribe from Observables | Use `async` pipe or `takeUntil` pattern |
| Hardcode API URLs in components | Use environment files (`environment.ts`) |
| Show a blank screen when data is loading | Always show a loading spinner |
| Show a blank screen when data is empty | Always show an empty-state component |

---

*This document is a living guide. If you discover something that should be added or corrected, create a PR with your suggested change. Every developer is responsible for keeping it up to date.*

**Last updated:** April 2026
**Maintained by:** Technical Lead

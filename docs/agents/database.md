# Database Rules — PostgreSQL + Flyway

Read this file for any task that involves creating or modifying database tables, columns, or indexes.

---

## Check Commands

```bash
# Flyway runs automatically on Spring Boot startup — no manual command needed
# Verify migrations match entities by starting the backend:
cd backend && mvn spring-boot:run -Dspring.profiles.active=dev
# Watch startup logs for "Successfully applied N migrations" or "Schema is up to date"

# Run tests — uses H2 in memory automatically, no Docker needed
cd backend && mvn clean verify
```

> Ask before running `mvn spring-boot:run`, `mvn clean package`, or any Docker command.

---

## Two Databases — Two Jobs

| | H2 | PostgreSQL |
|---|---|---|
| **When** | `mvn clean verify` / `mvn test` only | `mvn spring-boot:run` — the real app |
| **Lives** | In memory, destroyed after tests | On disk — Docker locally, Render/Azure on deployed |
| **Schema** | `ddl-auto: create-drop` — built from JPA entities | Flyway migrations — versioned SQL files |
| **Flyway** | Disabled in `application-test.yml` | Enabled |
| **Config** | `src/test/resources/application-test.yml` | `application-dev.yml` or platform env vars |

H2 is permanent — it stays in the project forever. Never write Flyway migrations targeting H2.

---

## Flyway — Non-Negotiable Rules

- Every new table or column requires a **new** migration file — never edit an existing one
- Location: `backend/src/main/resources/db/migration/`
- Naming: `V{n}__{description}.sql` — two underscores, sequential, never skip numbers
- `spring.jpa.hibernate.ddl-auto=validate` — Spring Boot **refuses to start** if entities don't match migrations
- Flyway runs automatically on startup

```
V1__create_app_user.sql
V2__create_account.sql
V3__create_transaction.sql
V4__add_reviewed_to_transaction.sql   ← new column = new file, always
```

### Migration file template

```sql
-- V4__add_reviewed_to_transaction.sql
ALTER TABLE transaction
    ADD COLUMN IF NOT EXISTS reviewed         BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS reviewed_at      TIMESTAMP;
```

Use `IF NOT EXISTS` and `IF EXISTS` guards — safer in development where migrations may be partially applied.

---

## Column Naming — snake_case Always

```sql
user_id, created_at, updated_at, category_overridden, flagged_for_cancellation
```

JPA maps `snake_case` → `camelCase` automatically via Spring's default naming strategy.

---

## Timestamps — Every Table Must Have

```sql
created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
updated_at  TIMESTAMP NOT NULL DEFAULT NOW()
```

Handle `updated_at` auto-update via `@PreUpdate` in JPA entity lifecycle hooks.

---

## Soft Deletes — Never Hard Delete Business Data

Use `archived = true` or `disabled = true` — never `DELETE` on business rows.

**Exceptions — hard delete is allowed:**
- `refresh_token` rows on user logout
- `sync_event` rows older than 90 days (scheduled cleanup job)
- All user data on GDPR erasure request (see GDPR section below)

---

## GDPR — Required Data Practices

Financial data is personal data under GDPR. These rules are mandatory.

### What data we hold and why

| Table | What we store | Lawful basis |
|---|---|---|
| `app_user` | email, powens_token, powens_user_id | Contract performance |
| `account` | name, type, last 4 digits, balance, currency | Contract performance |
| `transaction` | label, value, date, category | Contract performance |
| `subscription` | label, period, subscriber | Contract performance |
| `goal`, `goal_contribution` | user-entered data | Contract performance |
| `net_worth_snapshot` | computed aggregate, no raw bank data | Contract performance |
| `sync_event` | operational log, no personal financial data | Legitimate interest |
| `household_member` | invited email, role | Consent |

### Never store

- Full IBAN or full account number — store **last 4 digits only**
- Bank login credentials of any kind
- Full card numbers
- Raw Powens API responses — extract and map only the fields you need

```java
// Correct — store only what is needed
account.setAccountNumberLastFour(powensAccount.getNumber().slice(-4));

// Wrong — storing more than needed
account.setIban(powensAccount.getIban());           // ❌ full IBAN
account.setRawApiResponse(response.toString());     // ❌ raw dump
```

### Right to Erasure — Delete Endpoint Required

Every user must be able to delete their account and all associated data.

Add a `DELETE /api/users/me` endpoint that:

```java
// In UserDeletionService — cascades through all user data
public void deleteUser(Long userId) {
    transactionRepository.deleteAllByUserId(userId);
    accountRepository.deleteAllByUserId(userId);
    subscriptionRepository.deleteAllByUserId(userId);
    goalContributionRepository.deleteAllByGoalUserId(userId);
    goalRepository.deleteAllByUserId(userId);
    netWorthSnapshotRepository.deleteAllByUserId(userId);
    syncEventRepository.deleteAllByUserId(userId);
    householdMemberRepository.deleteAllByOwnerIdOrInvitedUserId(userId, userId);
    refreshTokenRepository.deleteAllByUserId(userId);
    userConnectionRepository.deleteAllByUserId(userId);
    // Revoke Powens user via API before deleting the token
    powensAuthService.revokeUser(user.getPowensUserId(), user.getPowensToken());
    appUserRepository.deleteById(userId);
}
```

This is a **hard delete** — the only place in the codebase where real rows are deleted.

### Data Retention — Scheduled Cleanup

Add to `SyncScheduler` or a dedicated cleanup job:

```java
@Scheduled(cron = "0 0 2 * * *")   // 2am daily
public void cleanupOldData() {
    // Sync event logs older than 90 days — operational data, no personal content
    syncEventRepository.deleteByCreatedAtBefore(LocalDateTime.now().minusDays(90));

    // Transactions older than 2 years — configurable via application.yml
    if (retentionEnabled) {
        transactionRepository.deleteByDateBeforeAndUserId(
            LocalDate.now().minusYears(retentionYears), userId
        );
    }
}
```

### `deleted_at` Field on `app_user`

Add a nullable `deleted_at` timestamp to `app_user`. Set it during erasure so any orphaned foreign key references can be identified in audits.

```sql
-- V{n}__add_deleted_at_to_app_user.sql
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;
```

---

## All Tables in This Project

Do not recreate these — only add to them with new migration files.

```
app_user          goal_contribution    sync_event
user_connection   goal_snapshot        household_member
account           goal_milestone       refresh_token
transaction       net_worth_snapshot
subscription      goal
```

---

## Foreign Keys — Always Explicit

```sql
CREATE TABLE goal (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES app_user(id),
    name        VARCHAR(255) NOT NULL,
    archived    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW()
);
```

---

## Indexes — Add for Every FK and Frequently Queried Column

```sql
CREATE INDEX IF NOT EXISTS idx_transaction_user_id ON transaction(user_id);
CREATE INDEX IF NOT EXISTS idx_transaction_date    ON transaction(date DESC);
CREATE INDEX IF NOT EXISTS idx_goal_user_id        ON goal(user_id);
```

---

## JPA Entity Conventions

```java
@Entity
@Table(name = "transaction")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private BigDecimal value;                  // always BigDecimal for money

    @Column(name = "account_number_last_four", length = 4)
    private String accountNumberLastFour;     // GDPR: last 4 digits only

    @Column(name = "category_overridden", nullable = false)
    private boolean categoryOverridden = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
```

---

## Do Not

- Edit an existing Flyway migration file — create a new one
- Store full IBANs or account numbers — last 4 digits only
- Hard-delete business data rows except on GDPR erasure requests
- Store raw Powens API response payloads
- Push code to GitHub — human reviews and pushes

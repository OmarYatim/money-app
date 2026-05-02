# Java Rules

Read this file for any task that involves writing or modifying `.java` files.

---

## Check & Format Commands

```bash
cd backend && mvn spotless:apply       # auto-format (Spotless + Google Java Format)
cd backend && mvn spotless:check       # check without modifying
```

> Ask before running `mvn clean verify` or any other Maven lifecycle command.

---

## Lombok — Required Annotations

Use Lombok to eliminate boilerplate. Never write getters, setters, or constructors manually.

```java
// JPA Entity
@Entity
@Table(name = "transaction")
@Data
@NoArgsConstructor          // required by JPA
@AllArgsConstructor
@Builder
public class Transaction { ... }

// DTO
@Data
@AllArgsConstructor
@Builder                    // makes test data construction cleaner
public class TransactionDTO { ... }

// Service class
@Service
@RequiredArgsConstructor    // injects all final fields via constructor
@Slf4j
public class TransactionService {
    private final TransactionRepository repository;  // injected by @RequiredArgsConstructor
}
```

---

## Naming Conventions

| Thing | Convention | Example |
|---|---|---|
| Classes | PascalCase | `TransactionService` |
| Methods | camelCase | `findByUserId()` |
| Variables | camelCase | `currentAmount` |
| Constants | UPPER_SNAKE_CASE | `MAX_RETRY_ATTEMPTS` |
| Packages | lowercase | `com.moneyapp.transaction` |
| DB columns (via JPA) | snake_case via `@Column(name=...)` | `category_overridden` |

---

## Logging — Slf4j Only

Use `@Slf4j` on every class that logs. Never use `System.out.println()`.

```java
@Slf4j
public class TransactionService {

    public void sync() {
        log.info("Starting transaction sync for userId={}", userId);
        try {
            // ...
        } catch (Exception e) {
            log.error("Sync failed for userId={}", userId, e);  // always pass the exception object
        }
    }
}
```

| Level | When |
|---|---|
| `log.info()` | Normal operations, state changes |
| `log.warn()` | Unexpected but handled — something is off but recoverable |
| `log.error()` | Failure that needs attention — always include the exception |
| `log.debug()` | Verbose detail — only for local debugging, remove before committing |

---

## Optionals

Always handle `Optional` explicitly. Never call `.get()` without checking first.

```java
// Correct — throws a typed exception
Transaction t = transactionRepository.findById(id)
    .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + id));

// Also fine — when null is a valid outcome
Optional<Transaction> t = transactionRepository.findById(id);
if (t.isPresent()) { ... }

// Never — will throw NullPointerException if empty
Transaction t = transactionRepository.findById(id).get();  // ❌
```

---

## Streams and Collections

```java
// Prefer streams for collection transformation
List<TransactionDTO> dtos = transactions.stream()
    .filter(t -> t.getValue().compareTo(BigDecimal.ZERO) < 0)
    .map(TransactionMapper::toDTO)
    .collect(Collectors.toList());

// Use BigDecimal for all numeric aggregation on financial data
BigDecimal total = transactions.stream()
    .map(Transaction::getValue)
    .reduce(BigDecimal.ZERO, BigDecimal::add);
```

---

## Immutability

- Prefer `final` on fields that do not change after construction
- Prefer `List.of()` and `Map.of()` for constant collections
- Never modify a list that was passed as a parameter

---

## Method Length

If a method exceeds ~25 lines, it is doing too much. Extract into a private method with a descriptive name. Name the method after what it does, not how it does it.

```java
// Descriptive naming
private boolean isOwnedByUser(Transaction t, Long userId) {
    return t.getUserId().equals(userId);
}

// Not
private boolean check(Transaction t, Long userId) { ... }  // too vague
```

---

## Do Not

- Use `double` or `float` for monetary values — always `BigDecimal`
- Call `Optional.get()` without checking `isPresent()`
- Write getters, setters, or constructors manually when Lombok covers them
- Push code to GitHub — human reviews and pushes

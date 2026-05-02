# API Design Rules

Read this file when creating or modifying any REST endpoint.

---

## URL Conventions

- Plural nouns: `/api/transactions`, `/api/accounts`, `/api/goals`
- No verbs in URLs: NOT `/api/getTransactions`, NOT `/api/markReviewed`
- Sub-resources for actions: `/api/transactions/{id}/reviewed`, `/api/goals/{id}/contributions`
- kebab-case for multi-word paths: `/api/net-worth-history`, `/api/spending-by-category`

| Action | Method | URL |
|---|---|---|
| Get list | GET | `/api/transactions` |
| Get one | GET | `/api/transactions/42` |
| Create | POST | `/api/goals` |
| Full replace | PUT | `/api/goals/5` |
| Partial update | PATCH | `/api/transactions/42/reviewed` |
| Delete (soft) | DELETE | `/api/goals/5` |

---

## HTTP Status Codes

| Code | When | Body |
|---|---|---|
| 200 | Successful GET, PATCH, PUT | Resource or updated resource |
| 201 | Successful POST | The created resource |
| 204 | Successful DELETE | Empty |
| 400 | Invalid input | `{ "code": "VALIDATION_ERROR", "fields": {...} }` |
| 401 | Not authenticated | `{ "code": "UNAUTHORIZED" }` |
| 403 | Authenticated but not authorised | `{ "code": "FORBIDDEN" }` |
| 404 | Resource not found | `{ "code": "NOT_FOUND", "message": "..." }` |
| 409 | Conflict (duplicate) | `{ "code": "CONFLICT", "message": "..." }` |
| 500 | Unexpected server error | `{ "code": "INTERNAL_ERROR", "message": "Something went wrong" }` |

Never expose stack traces, DB schema details, or file paths in error responses.

---

## Pagination — Always for List Endpoints

Never return an unbounded list. Always use Spring's `Pageable` and `Page<T>`.

**Request:** `GET /api/transactions?page=0&size=20&keyword=amazon&category=SHOPPING`

**Response:**
```json
{
  "content": [...],
  "totalElements": 247,
  "totalPages": 13,
  "page": 0,
  "size": 20
}
```

```java
// Controller
@GetMapping
public ResponseEntity<Page<TransactionDTO>> getTransactions(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        TransactionFilter filter) {
    Pageable pageable = PageRequest.of(page, size, Sort.by("date").descending());
    return ResponseEntity.ok(transactionService.findAll(filter, pageable));
}
```

---

## Request Validation

Use `@Valid` on all request bodies. Never validate manually in a Controller.

```java
@PostMapping
public ResponseEntity<GoalDTO> createGoal(@Valid @RequestBody CreateGoalRequest request) {
    return ResponseEntity.status(201).body(goalService.create(request));
}

// Request class
public class CreateGoalRequest {
    @NotBlank(message = "name is required")
    private String name;

    @NotNull(message = "targetAmount is required")
    @Positive(message = "targetAmount must be greater than 0")
    private BigDecimal targetAmount;
}
```

Validation errors are caught automatically by `GlobalExceptionHandler` → HTTP 400.

---

## Response Structure — Consistency

All successful single-resource responses return the resource directly (no wrapper):
```json
{ "id": 42, "label": "Amazon", "value": -12.99 }
```

All error responses follow this shape:
```json
{ "code": "NOT_FOUND", "message": "Transaction with id 42 not found" }
```

---

## Ownership Check Pattern

Every endpoint that accesses user-specific data must go through a service ownership check.

```java
// In the Service
public TransactionDTO getById(Long id, Long authenticatedUserId) {
    Transaction t = transactionRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));

    if (!t.getUserId().equals(authenticatedUserId)) {
        throw new AccessDeniedException("Access denied");
    }

    return TransactionMapper.toDTO(t);
}
```

This check is in the **Service**, not the Controller.

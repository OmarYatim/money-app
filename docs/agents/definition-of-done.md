# Definition of Done

Run through this before marking any task complete.

---

## Step 1 — Auto-Fix First

Run these without asking — they only format and lint:

```bash
# Backend
cd backend && mvn spotless:apply

# Frontend
cd frontend && ng lint --fix
```

---

## Step 2 — Run Checks

Run all of these without asking:

```bash
# Backend — format and test (H2 used automatically, no Docker needed)
cd backend && mvn spotless:apply
cd backend && mvn clean verify

# Frontend — lint, type-check, and test
cd frontend && ng lint --fix
cd frontend && npx tsc --noEmit
cd frontend && ng test --watch=false --browsers=ChromeHeadless
```

Ask before running:

```bash
cd frontend && ng build --configuration=production   # ask first — compiles full app
```

---

## Step 3 — Checklist

### Code Quality
- [ ] `mvn spotless:apply` has been run — Java is formatted
- [ ] `ng lint --fix` has been run — TypeScript/HTML is linted
- [ ] `npx tsc --noEmit` passes — no TypeScript errors
- [ ] No `any` types introduced
- [ ] No hardcoded URLs, secrets, or environment-specific values

### Backend
- [ ] New service methods have unit tests (happy path + at least one error path)
- [ ] Ownership check present on every new data-access method
- [ ] DTOs used — no raw JPA entities returned from controllers
- [ ] `BigDecimal` used for all monetary fields
- [ ] New or changed table: Flyway migration file exists and Spring Boot starts cleanly

### Frontend
- [ ] All three UI states handled: loading, error, empty
- [ ] Signals used for component state
- [ ] `@if` / `@for` used — not `*ngIf` / `*ngFor`
- [ ] `standalone: true` NOT set in `@Component` decorators
- [ ] No manual `.subscribe()` without cleanup — `toSignal()` preferred
- [ ] `changeDetection: ChangeDetectionStrategy.OnPush` set on every component
- [ ] AXE accessibility checks pass
- [ ] New interfaces in `src/app/shared/models/` — not defined inline

### Git
- [ ] Branch named `feature/US-XX-short-description`
- [ ] Commit message follows Conventional Commits format
- [ ] `application-dev.yml` is NOT staged (`git status` confirms)
- [ ] Only files relevant to this task are staged
- [ ] Changes have been committed locally
- [ ] Commit hash has been reported to the human
- [ ] Code has NOT been pushed — human pushes

### Feature
- [ ] Feature works end-to-end (backend + frontend if applicable)
- [ ] Acceptance criteria in the Trello card are met

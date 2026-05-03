# AGENTS.md — Money App

Personal finance app. Users connect bank accounts via the Powens Open Finance API. Backend syncs and stores financial data in PostgreSQL. Frontend displays dashboards, transactions, goals, and reports. The app is **read-only** — no payments, no bank credentials ever handled.

---

## Monorepo Structure

```
/
├── backend/          ← Spring Boot 3.x, Java 17, Maven
├── frontend/         ← Angular 21, TypeScript, SCSS, npm
├── docker-compose.yml
├── docs/
│   └── agents/       ← extended rules — read on demand, not upfront
├── ARCHITECTURE.md
├── AGENTS.md
└── README.md
```

- Never create files outside `backend/` or `frontend/` unless explicitly told to
- Never modify `docker-compose.yml`, `ARCHITECTURE.md`, or `AGENTS.md` unless explicitly told to

---

## Package Managers

| Layer | Manager | Lockfile | Install command |
|---|---|---|---|
| Backend | Maven (`mvn`) | `pom.xml` | `mvn install` |
| Frontend | npm | `package-lock.json` | `npm ci` |

- Backend: always `mvn`, never Gradle
- Frontend: always `npm`, never `yarn` or `pnpm`
- Never add a dependency without stating it explicitly in your response

---

## Commands — Run Without Asking

```bash
# Backend — format, check, and test
cd backend && mvn spotless:apply       # auto-format Java code
cd backend && mvn spotless:check       # check only — does not modify
cd backend && mvn clean verify         # run all tests (uses H2 in memory — no Docker needed)

# Frontend — lint, type-check, and test
cd frontend && ng lint --fix           # auto-fix lint issues
cd frontend && ng lint                 # check only
cd frontend && npx tsc --noEmit        # type-check only — no output files
cd frontend && ng test --watch=false --browsers=ChromeHeadless   # unit tests
```

**After finishing any code generation**, always run lint automatically before reporting done:

```bash
# After generating backend code:
cd backend && mvn spotless:apply && mvn spotless:check

# After generating frontend code:
cd frontend && ng lint --fix && npx tsc --noEmit
```

---

## Ask Before Running These Commands

State what you want to run and **why**, then wait for approval:

```
# Requires approval:
mvn spring-boot:run ...       # starts the server
mvn clean package ...         # builds a JAR
ng serve                      # starts the dev server
ng build ...                  # compiles the app
ng generate ...               # scaffolds files
docker-compose up             # starts containers
docker build ...              # builds an image
```

---

## Git Workflow

When starting a new task, prepare a clean task branch without asking for separate approval:

```bash
git checkout main
git fetch
git pull
git checkout -b feature/US-XX-short-description
```

Use the Trello card or task name to choose the branch name. If no user story exists, use a short descriptive branch name such as `chore/update-agent-rules`.

**Commit every file edit before moving to the next step.** Do not batch edits across multiple steps and commit at the end — each logical change gets its own commit as soon as it is complete. Never end a turn with modified but uncommitted files.

After finishing the task:

1. Run the relevant checks.
2. Review `git status` and stage only task-relevant files.
3. Create a local Conventional Commit.
4. Report the commit hash and wait for human approval.

**Never push to GitHub.** This is the only hard Git prevention. The human reviews and pushes all changes.

---

## When You Are Stuck

Do not guess. Instead:

1. **Describe the issue** — what exactly is blocking you
2. **Explain your reasoning** — what you tried and why it doesn't work
3. **State what you need** — a decision, clarification, or permission to try a specific approach
4. Wait before proceeding

Example:
> "I'm stuck on the token refresh in `auth.interceptor.ts`. I tried catching 401s and retrying after `/auth/refresh`, but multiple concurrent failures create a race condition. I considered queuing requests with a `BehaviorSubject`, but I'm not sure that's the pattern you want. Should I implement the queue, or prefer a simpler retry-once approach?"

---

## Hard Stops — Global, Every Task

These four rules apply to every file in the entire project. All other constraints live in the relevant sub-file.

| Never | Why |
|---|---|
| Read or write `application-dev.yml` | Contains real secrets |
| Log any token, password, or secret | Security |
| Hardcode any URL, secret, or environment-specific value | Breaks multi-env setup |
| Push code to GitHub | Human reviews and pushes all changes |

---

## Extended Rules — Read When the Task Applies

| Working on... | Read |
|---|---|
| Any backend (Spring Boot) task | [`docs/agents/backend.md`](docs/agents/backend.md) |
| Any frontend (Angular) task | [`docs/agents/frontend.md`](docs/agents/frontend.md) |
| Any TypeScript file | [`docs/agents/typescript.md`](docs/agents/typescript.md) |
| Any Java file | [`docs/agents/java.md`](docs/agents/java.md) |
| Any SCSS / CSS file | [`docs/agents/css.md`](docs/agents/css.md) |
| Any database table or migration | [`docs/agents/database.md`](docs/agents/database.md) |
| Any REST endpoint | [`docs/agents/api-design.md`](docs/agents/api-design.md) |
| Powens API integration | [`docs/agents/powens.md`](docs/agents/powens.md) |
| Git commits, branches, PRs | [`docs/agents/git.md`](docs/agents/git.md) |
| Environment variables or secrets | [`docs/agents/environments.md`](docs/agents/environments.md) |
| Marking a task complete | [`docs/agents/definition-of-done.md`](docs/agents/definition-of-done.md) |

---

Temporary dev auth can use `APP_AUTH_ENABLED=false` so the backend falls back to the single seeded app user before full account authentication exists. Remove that shortcut once user login/account ownership is fully implemented.

*Full architecture, patterns, and examples: see `ARCHITECTURE.md`*

# Git Rules

Read this file when committing, branching, or preparing a PR.

---

## Hard Rules

- **Never push to GitHub** — the human reviews all changes and pushes
- **Never commit directly to `main`** — always use a feature branch
- **Create and switch branches as part of the normal task workflow**
- **Commit every file edit before moving to the next step** — each logical change gets its own commit as soon as it is complete; never end a turn with modified but uncommitted files
- **Commit locally after finishing a task, then wait for human approval**

---

## Branch Strategy

```
main            ← only long-lived branch, always deployable, CI-protected
feature/US-XX   ← all feature work — branched from main, merged via PR, deleted after
hotfix/...      ← emergency fixes only
```

No `develop` branch. This is a solo project.

---

## Starting a New Task

At the start of every new task, update `main` and create a fresh task branch:

```bash
git checkout main
git fetch
git pull
git checkout -b feature/US-04-transaction-list
```

Use the Trello card or task name to choose the branch name. If the task is not tied to a user story, use a short descriptive branch name such as `chore/update-agent-rules`.

---

## Branch Naming

```
feature/US-01-bank-connection
feature/US-04-transaction-list
bugfix/US-03-wrong-net-worth-calculation
hotfix/auth-token-expiry-crash
```

Format: `{type}/{US-XX}-{short-description}` in kebab-case.

---

## Before Every Commit — Run These

```bash
# Backend
cd backend && mvn spotless:apply       # auto-format

# Frontend
cd frontend && ng lint --fix           # auto-fix lint
cd frontend && npx tsc --noEmit        # type-check
```

> Ask before running `mvn clean verify`, `ng build`, or `ng test`.

## After Finishing a Task

Review the worktree, stage only files relevant to the task, and create a local Conventional Commit:

```bash
git status
git add <task-relevant-files>
git commit -m "type(scope): short description"
```

Then report the commit hash and wait for human approval. Do not push.

---

## Commit Messages — Conventional Commits

```
feat(transactions): add keyword search filter
fix(auth): handle token refresh race condition
refactor(goals): extract progress to GoalProgressService
test(dashboard): add unit tests for net worth calculation
chore(deps): update jjwt to 0.12.6
docs(readme): update local setup instructions
```

Format: `{type}({scope}): {short description in present tense}`  
Scope = package or feature name in lowercase.

Never commit with messages like: `fix`, `update`, `wip`, `changes`, or any single word.

---

## What Never Goes in a Commit

- `application-dev.yml` or any file containing real credentials
- `.env` files
- `target/` or `dist/` build artifacts
- Files unrelated to the current task

Run `git status` and review staged files before committing.

---

## After PR Merges

The human handles merging and pushing. Once they confirm the merge:

```bash
git checkout main
git fetch
git pull
git branch -d feature/US-XX-description
```

Do not delete the remote branch — GitHub does that automatically if configured.

---

## Do Not

- Push to GitHub — human pushes
- Commit directly to `main`
- Use vague commit messages (`fix`, `update`, `wip`)
- Commit secrets or credentials

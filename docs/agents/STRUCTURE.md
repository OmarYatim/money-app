# docs/ — Folder Structure

```
docs/
└── agents/                          ← agent context files, read on demand
    ├── backend.md                   ← Spring Boot rules, commands, patterns
    ├── frontend.md                  ← Angular 21 rules, signals, standalone
    ├── typescript.md                ← TypeScript types, naming, inject()
    ├── database.md                  ← Flyway migrations, PostgreSQL conventions
    ├── api-design.md                ← REST URLs, status codes, pagination
    ├── git.md                       ← branching, commits, PR rules
    ├── environments.md              ← env vars, secrets, Powens URLs
    ├── definition-of-done.md        ← pre-submit checklist with commands
    └── skills/
        └── angular21.md             ← Angular 21 skill file (add separately)
```

## How This Works

The root `AGENTS.md` is minimal — it contains only what is relevant to **every single task**.
All detailed rules live in `docs/agents/` and are read **on demand** based on what the task touches.

The agent reads the lookup table in `AGENTS.md` to know which file to open:

| Working on... | Read... |
|---|---|
| Any backend task | `docs/agents/backend.md` |
| Any frontend task | `docs/agents/frontend.md` + `docs/agents/typescript.md` |
| Any database change | `docs/agents/database.md` |
| Any REST endpoint | `docs/agents/api-design.md` |
| Committing or branching | `docs/agents/git.md` |
| Env vars or Powens | `docs/agents/environments.md` |
| Marking a task done | `docs/agents/definition-of-done.md` |

## Adding New Skill Files

When Angular 21 knowledge is added as a skill:
- Place it at `docs/agents/skills/angular21.md`
- The reference is already in `docs/agents/frontend.md` line 5
- No changes to root `AGENTS.md` needed

# TypeScript Rules

Read this file for any task that involves writing or modifying `.ts` files in the frontend.

---

## Check Commands

```bash
cd frontend && npx tsc --noEmit        # type-check without producing output files
cd frontend && ng lint --fix           # lint and auto-fix
cd frontend && ng lint                 # check only
```

> Ask before running `ng build` or `ng test`.

---

## Strict Mode — Always On

`tsconfig.json` has `"strict": true`. Never disable it or any of its sub-flags.  
This enables: `strictNullChecks`, `noImplicitAny`, `strictPropertyInitialization`, and more.

---

## Type Safety

- Never use `any` — use the correct type, a generic, or `unknown`
- Prefer type inference when the type is obvious from the right-hand side
- Always declare return types on public service methods

```typescript
// Type inference is fine here — type is obvious
const count = signal(0);
const name = 'Money App';

// Explicit return type required on service methods
getTransactions(): Observable<Page<Transaction>> { ... }

// unknown over any when type is genuinely unknown
function parse(data: unknown): Transaction {
  if (!isTransaction(data)) throw new Error('Unexpected shape');
  return data;
}
```

---

## Interfaces — Location and Shape

All domain models and API response shapes live in `src/app/shared/models/`.  
One file per domain entity. Never define interfaces inline in a component or service file.

```
shared/models/
├── transaction.model.ts
├── account.model.ts
├── goal.model.ts
├── page.model.ts        ← generic Page<T>
└── auth.model.ts
```

```typescript
// page.model.ts
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
}
```

---

## Union Types Over Enums

Prefer union types for string sets — they serialise cleanly and work better with JSON APIs.

```typescript
// Preferred
export const CATEGORY_TYPES = [
  'GROCERIES', 'DINING', 'TRANSPORT', 'UTILITIES', 'RENT',
  'HEALTH', 'ENTERTAINMENT', 'SHOPPING', 'TRAVEL', 'EDUCATION',
  'INCOME', 'TRANSFER', 'SAVINGS', 'SUBSCRIPTION', 'OTHER'
] as const;

export type CategoryType = typeof CATEGORY_TYPES[number];
```

---

## Signal Typing

```typescript
// Explicit types when the initial value doesn't infer correctly
transactions = signal<Transaction[]>([]);
error = signal<string | null>(null);
selectedId = signal<number | undefined>(undefined);

// computed() types are inferred — no annotation needed
total = computed(() => this.transactions().reduce((s, t) => s + t.value, 0));
```

---

## Null Safety

```typescript
// Optional chaining and nullish coalescing
const name = user?.name ?? 'Unknown';

// Non-null assertion — only when provably non-null from context
const id = this.route.snapshot.paramMap.get('id')!;   // safe: route config guarantees it

// Never — will throw at runtime if null
const name = user!.name;   // ❌ if user could be null
```

---

## Dependency Injection

Use `inject()` — not constructor injection.

```typescript
// Correct
export class TransactionListComponent {
  private service = inject(TransactionService);
  private router = inject(Router);
}

// Avoid
constructor(private service: TransactionService) {}   // verbose, harder to test
```

---

## Naming Conventions

| Thing | Convention | Example |
|---|---|---|
| Interfaces / types | PascalCase | `Transaction`, `Page<T>` |
| Services | PascalCase + Service | `TransactionService` |
| Components | PascalCase + Component | `TransactionListComponent` |
| Pipes | PascalCase + Pipe | `CategoryColorPipe` |
| Guards | PascalCase + Guard | `AuthGuard` |
| Signals | camelCase noun | `transactions`, `loading`, `error` |
| Constants | UPPER_SNAKE_CASE | `MAX_PAGE_SIZE` |
| Files | kebab-case + type | `transaction-list.component.ts` |

---

## Do Not

- Use `any` — use the correct type or `unknown`
- Disable `strict` or `strictNullChecks`
- Define interfaces inline in components or services
- Use `enum` when a union type works — prefer union types
- Push code to GitHub — human reviews and pushes

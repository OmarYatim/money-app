# Frontend Rules — Angular 21

> **Persona:** You are an experienced Angular developer who writes clean, performant, accessible Angular 21 code. You default to signals, standalone components, and modern control flow. You never introduce patterns from older Angular versions unless explicitly asked.

Angular 21 SPA. Displays financial data fetched from the Spring Boot backend. Uses signals for state, Observables only for HTTP, standalone components throughout.

Also read: [`typescript.md`](typescript.md) · [`css.md`](css.md)  
Angular 21 API reference: [`docs/agents/skills/angular21.md`](skills/angular21.md)

**Package manager: npm**

---

## Check & Lint Commands

```bash
cd frontend && ng lint --fix           # auto-fix lint issues (run before every commit)
cd frontend && ng lint                 # check only
cd frontend && npx tsc --noEmit        # type-check without building
```

> Ask before running `ng serve`, `ng build`, `ng test`, or `ng generate`.

---

## Folder Structure — Do Not Deviate

```
frontend/src/app/
├── core/                        ← singleton services, loaded once at startup
│   ├── auth/
│   │   ├── auth.service.ts
│   │   ├── auth.interceptor.ts
│   │   └── auth.guard.ts
│   └── sse/
│       └── sse.service.ts
├── shared/
│   ├── components/              ← loading-spinner, empty-state, currency-display
│   ├── pipes/
│   └── models/                  ← ALL TypeScript interfaces live here
└── features/                    ← one folder per feature
    ├── accounts/
    ├── transactions/
    ├── dashboard/
    ├── goals/
    ├── reports/
    ├── subscriptions/
    └── household/
```

Each feature folder contains `{feature}.service.ts` and component subfolders.  
**No `.module.ts` files.** All components are standalone.

---

## Standalone Components

All components are standalone. Never create an NgModule.  
Do **not** set `standalone: true` in the `@Component` decorator — it is the default in Angular 20+.

```typescript
@Component({
  selector: 'app-transaction-list',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatCardModule, CurrencyPipe, DatePipe],
  templateUrl: './transaction-list.component.html',
  styleUrl: './transaction-list.component.scss',
})
export class TransactionListComponent { }
```

Bootstrap via `bootstrapApplication` in `main.ts`.  
Use paths relative to the component `.ts` file for external templates and styles.

---

## Components

- Keep components small and focused on a single responsibility
- Use `input()` and `output()` functions — not `@Input()` / `@Output()` decorators
- Use `computed()` for all derived state
- Always set `changeDetection: ChangeDetectionStrategy.OnPush`
- Prefer inline templates for components with fewer than ~10 lines of HTML
- Use Reactive forms — not Template-driven forms
- Do **not** use `@HostBinding` or `@HostListener` — use the `host` object in the decorator instead
- Do **not** use `ngClass` — use `class` bindings instead
- Do **not** use `ngStyle` — use `style` bindings instead

```typescript
// host bindings — correct
@Component({
  host: {
    '[class.active]': 'isActive()',
    '(click)': 'handleClick()',
  }
})

// class binding — correct
<div [class.highlighted]="isSelected()">

// ngClass — never use
<div [ngClass]="{ highlighted: isSelected() }">   // ❌
```

---

## Accessibility

- Every component must pass all AXE automated checks
- Follow WCAG AA minimums: focus management, colour contrast ≥ 4.5:1, ARIA attributes where needed
- All interactive elements must be keyboard-navigable
- Images must have `alt` attributes; decorative images use `alt=""`

---

## Images

- Use `NgOptimizedImage` for all static images
- `NgOptimizedImage` does not work for inline base64 images — use a standard `<img>` for those

```typescript
import { NgOptimizedImage } from '@angular/common';

// In template
<img ngSrc="/assets/logo.png" width="120" height="40" alt="Money App logo" />
```

---

## State Management — Signals for State, Observables for HTTP

```typescript
// Local state — always signals
count = signal(0);
doubled = computed(() => this.count() * 2);

// Mutate signals — use set() or update(), never mutate()
this.count.set(5);
this.count.update(n => n + 1);

// HTTP — services return Observable, components use toSignal()
transactions = toSignal(
  this.service.getTransactions({}),
  { initialValue: [] }
);

// Re-fetch on demand
private refresh$ = new Subject<void>();
transactions = toSignal(
  this.refresh$.pipe(startWith(null), switchMap(() => this.service.getTransactions({}))),
  { initialValue: [] }
);
reload() { this.refresh$.next(); }
```

- Do **not** call `.subscribe()` in a component unless you also clean up in `ngOnDestroy`
- Prefer `toSignal()` — it handles unsubscription automatically
- State transformations must be pure — no side effects in `computed()`

---

## Services

```typescript
@Injectable({ providedIn: 'root' })   // always root for singleton services
export class TransactionService {
  private http = inject(HttpClient);  // inject(), not constructor injection

  getTransactions(filter: TransactionFilter): Observable<Page<Transaction>> {
    return this.http.get<Page<Transaction>>('/api/transactions', { params: { ...filter } });
  }
}
```

- Design services around a single responsibility
- Always `providedIn: 'root'` for singleton services
- Always `inject()` — never constructor injection
- Services return `Observable<T>` — never subscribe inside a service

---

## Lazy Loading

Feature routes must use lazy loading.

```typescript
// app.routes.ts
export const routes: Routes = [
  {
    path: 'transactions',
    loadComponent: () =>
      import('./features/transactions/transaction-list.component')
        .then(m => m.TransactionListComponent),
  },
];
```

---

## Template Rules

- Use native control flow — never `*ngIf`, `*ngFor`, `*ngSwitch`
- Keep templates simple — no complex expressions or method calls in templates
- Do not assume globals like `new Date()` are available in templates
- Use the `async` pipe only when `toSignal()` is not possible

```html
@if (loading()) {
  <app-loading-spinner />
} @else if (error()) {
  <app-error-banner [message]="error()!" />
} @else if (transactions().length === 0) {
  <app-empty-state message="No transactions yet" />
} @else {
  @for (t of transactions(); track t.id) {
    <app-transaction-row [transaction]="t" (reviewed)="markReviewed(t.id)" />
  }
}
```

---

## Smart vs Dumb Components

**Smart:** inject services, own signals, pass data down via `input()`  
**Dumb:** receive data via `input()`, emit via `output()`, never inject services

```typescript
// Dumb component
export class TransactionRowComponent {
  transaction = input.required<Transaction>();
  category = input<string>('OTHER');
  reviewed = output<void>();
}
```

Chart components are always dumb.

---

## Loading / Error / Empty — Always All Three

```typescript
loading = signal(true);
error = signal<string | null>(null);
items = signal<Transaction[]>([]);
```

Never show a blank screen. Always show one of: spinner, error banner, empty state, or content.

---

## Routing

- Never hardcode `https://api.moneyapp.me` in services — use `environment.apiBaseUrl`
- Use Angular `Router` for internal navigation
- Use `window.location.href` only for the Powens Webview external redirect

---

## Template Formatting

- Currency: `{{ amount | currency:'EUR':'symbol':'1.2-2' }}`
- Dates: `{{ date | date:'dd MMM yyyy' }}`
- Never format currency or dates in TypeScript

---

## Do Not

- Push code to GitHub — human reviews and pushes
- Use `@HostBinding` or `@HostListener`
- Use `ngClass` or `ngStyle`
- Use `*ngIf`, `*ngFor`, `*ngSwitch`
- Set `standalone: true` in `@Component`
- Call `mutate()` on signals — use `set()` or `update()`
- Subscribe in a service
- Call Powens API directly — backend only (see [`powens.md`](powens.md))
- Create NgModules

---

## Key Files

| File | Purpose |
|---|---|
| `src/app/core/auth/auth.interceptor.ts` | Attaches JWT to every request, handles 401 refresh |
| `src/app/core/auth/auth.guard.ts` | Blocks unauthenticated routes |
| `src/environments/environment.ts` | Dev config (`apiBaseUrl: ''`) |
| `src/environments/environment.production.ts` | Prod config (`apiBaseUrl: 'https://api.moneyapp.me'`) |
| `proxy.conf.json` | Dev proxy — `/api/*` → `localhost:8080` |
| `angular.json` | Build config, proxy reference |

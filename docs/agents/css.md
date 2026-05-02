# CSS / SCSS Rules

Read this file for any task that involves writing or modifying `.scss` or `.css` files.

---

## Check & Lint Commands

```bash
cd frontend && ng lint --fix           # Stylelint runs as part of Angular ESLint config
cd frontend && ng lint                 # check only
```

> Ask before running `ng build` or `ng serve`.

---

## Preprocessor

Use **SCSS** only. Never write plain `.css` files for component styles.  
New components use `.component.scss` with a path relative to the `.ts` file.

---

## Methodology — BEM

Use BEM (Block Element Modifier) naming for all custom classes.

```scss
// Block
.transaction-card { }

// Element
.transaction-card__amount { }
.transaction-card__label { }

// Modifier
.transaction-card--highlighted { }
.transaction-card__amount--negative { }
```

Never use deeply nested selectors more than 2 levels deep.  
Never use `#id` selectors for styling.

---

## Design Tokens — Use Angular Material Theming

Do not hardcode colours, spacing, or typography values.  
Use Angular Material's design tokens and CSS custom properties.

```scss
// Correct — uses the theme
color: var(--mat-sys-primary);
background: var(--mat-sys-surface);
font-size: var(--mat-sys-body-large-size);

// Wrong — hardcoded
color: #1976d2;         // ❌
font-size: 16px;        // ❌
```

Define any custom tokens in `src/styles/_tokens.scss` and import where needed.

---

## Responsive Design

Use Angular Material breakpoints. Never write breakpoints with raw pixel values.

```scss
@use '@angular/material' as mat;

// Use Material breakpoints
@include mat.breakpoint(handset) {
  .dashboard-grid {
    grid-template-columns: 1fr;
  }
}
```

---

## Scoped vs Global Styles

| Where | What goes there |
|---|---|
| `component.scss` | Styles scoped to that component only |
| `src/styles/_tokens.scss` | Custom CSS variables / design tokens |
| `src/styles/_typography.scss` | Global typography rules |
| `src/styles.scss` | Global resets and Material theme import only |

Never add component-specific styles to `styles.scss`.

---

## Accessibility

- Colour contrast must meet WCAG AA minimum (4.5:1 for body text, 3:1 for large text)
- Never use colour as the only means of conveying information — pair with an icon or label
- Focus styles must be visible — never `outline: none` without a replacement

---

## Do Not

- Hardcode colours, spacing, or font sizes — use design tokens
- Use `#id` selectors
- Nest selectors more than 2 levels deep
- Write plain `.css` files — always `.scss`
- Use `!important` — if you feel the need, the specificity is wrong
- Push code to GitHub — human reviews and pushes

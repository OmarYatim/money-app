import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: 'accounts',
    loadComponent: () =>
      import('./features/accounts/account-list/account-list.component').then(
        (component) => component.AccountListComponent,
      ),
  },
  {
    path: 'transactions',
    loadComponent: () =>
      import('./features/transactions/transaction-list/transaction-list.component').then(
        (component) => component.TransactionListComponent,
      ),
  },
  {
    path: 'transactions/:id',
    loadComponent: () =>
      import('./features/transactions/transaction-detail/transaction-detail.component').then(
        (component) => component.TransactionDetailComponent,
      ),
  },
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'accounts',
  },
];

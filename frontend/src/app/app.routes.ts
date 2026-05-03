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
    path: '',
    pathMatch: 'full',
    redirectTo: 'accounts',
  },
];

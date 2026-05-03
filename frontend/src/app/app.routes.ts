import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () =>
      import('./features/auth/login/login.component').then((c) => c.LoginComponent),
  },
  {
    path: 'accounts',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/accounts/account-list/account-list.component').then(
        (component) => component.AccountListComponent,
      ),
  },
  {
    path: 'transactions',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/transactions/transaction-list/transaction-list.component').then(
        (component) => component.TransactionListComponent,
      ),
  },
  {
    path: 'transactions/:id',
    canActivate: [authGuard],
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

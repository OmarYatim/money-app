import { CurrencyPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';
import { catchError, forkJoin, map, of, startWith, Subject, switchMap } from 'rxjs';

import type { Account } from '../../../shared/models/account.model';
import type { SyncStatus } from '../../../shared/models/sync-status.model';
import { PageActionsComponent } from '../../../shared/components/page-actions/page-actions.component';
import { AccountConnectComponent } from '../account-connect/account-connect.component';
import { AccountService } from '../account.service';

interface AccountListState {
  accounts: Account[];
  syncStatus: SyncStatus;
  loading: boolean;
  error: string | null;
}

const EMPTY_SYNC_STATUS: SyncStatus = {
  lastSyncedAt: null,
  connectionsRequiringAction: [],
  hasSyncError: false,
};

@Component({
  selector: 'app-account-list',
  imports: [
    CurrencyPipe,
    MatIconModule,
    MatProgressSpinnerModule,
    RouterLink,
    PageActionsComponent,
    AccountConnectComponent,
  ],
  templateUrl: './account-list.component.html',
  styleUrl: './account-list.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AccountListComponent {
  private readonly accountService = inject(AccountService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly refreshAccounts$ = new Subject<void>();

  protected readonly filterType = signal<string>('all');

  protected readonly accountTypeTabs = [
    { id: 'all', label: 'All accounts', count: (a: Account[]) => a.length },
    {
      id: 'checking',
      label: 'Checking',
      count: (a: Account[]) => a.filter((x) => (x.type ?? '').toLowerCase() === 'checking').length,
    },
    {
      id: 'savings',
      label: 'Savings',
      count: (a: Account[]) => a.filter((x) => (x.type ?? '').toLowerCase() === 'savings').length,
    },
    {
      id: 'credit',
      label: 'Credit',
      count: (a: Account[]) => a.filter((x) => (x.type ?? '').toLowerCase() === 'credit').length,
    },
    {
      id: 'investment',
      label: 'Investments',
      count: (a: Account[]) =>
        a.filter((x) => (x.type ?? '').toLowerCase() === 'investment').length,
    },
  ];

  protected readonly state = toSignal(
    this.refreshAccounts$.pipe(
      startWith(undefined),
      switchMap(() =>
        forkJoin({
          accounts: this.accountService.getAccounts(),
          syncStatus: this.accountService.getSyncStatus(),
        }).pipe(
          map(
            ({ accounts, syncStatus }): AccountListState => ({
              accounts,
              syncStatus,
              loading: false,
              error: null,
            }),
          ),
          catchError(() =>
            of({
              accounts: [],
              syncStatus: EMPTY_SYNC_STATUS,
              loading: false,
              error: 'Unable to load connected accounts.',
            }),
          ),
        ),
      ),
    ),
    {
      initialValue: {
        accounts: [],
        syncStatus: EMPTY_SYNC_STATUS,
        loading: true,
        error: null,
      },
    },
  );

  protected readonly actionConnections = computed(
    () => this.state().syncStatus.connectionsRequiringAction,
  );

  protected readonly filteredAccounts = computed(() => {
    const { accounts } = this.state();
    const type = this.filterType();
    if (type === 'all') return accounts;
    return accounts.filter((a) => (a.type ?? '').toLowerCase() === type);
  });

  protected readonly totals = computed(() => {
    const { accounts } = this.state();
    const totalAssets = accounts.filter((a) => a.balance > 0).reduce((s, a) => s + a.balance, 0);
    const totalLiabilities = accounts
      .filter((a) => a.balance < 0)
      .reduce((s, a) => s + Math.abs(a.balance), 0);
    return { totalAssets, totalLiabilities, netWorth: totalAssets - totalLiabilities };
  });

  protected accountTypeAccent(type: string | null): string {
    switch ((type ?? '').toLowerCase()) {
      case 'checking':
        return 'linear-gradient(135deg, #5b5fef, #7c80f5)';
      case 'savings':
        return 'linear-gradient(135deg, #2cad6a, #5dd49a)';
      case 'credit':
        return 'linear-gradient(135deg, #c8366f, #e04a8c)';
      case 'investment':
        return 'linear-gradient(135deg, #d99838, #f0b85e)';
      default:
        return 'linear-gradient(135deg, #9396a8, #c5c7d4)';
    }
  }

  protected accountTypeIcon(type: string | null): string {
    switch ((type ?? '').toLowerCase()) {
      case 'checking':
        return 'account_balance';
      case 'savings':
        return 'savings';
      case 'credit':
        return 'credit_card';
      case 'investment':
        return 'trending_up';
      default:
        return 'account_balance_wallet';
    }
  }

  protected reloadAccounts(): void {
    this.refreshAccounts$.next();
    this.snackBar.open('Accounts refreshed.', 'Dismiss', {
      duration: 3000,
    });
  }

  protected reconnect(): void {
    this.snackBar.open(
      'Use Connect a bank to re-authenticate the connection.',
      'Dismiss',
      { duration: 5000 },
    );
  }
}

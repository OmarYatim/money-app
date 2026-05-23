import { CurrencyPipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';
import { catchError, finalize, forkJoin, map, of, startWith, Subject, switchMap } from 'rxjs';

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

interface AccountConnectionGroup {
  id: string;
  connectionId: number | null;
  institutionName: string;
  accounts: Account[];
}

interface DisconnectDialogState {
  connectionId: number;
  institutionName: string;
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
  private readonly destroyRef = inject(DestroyRef);
  private readonly snackBar = inject(MatSnackBar);
  private readonly refreshAccounts$ = new Subject<void>();

  protected readonly filterType = signal<string>('all');
  protected readonly disconnectDialog = signal<DisconnectDialogState | null>(null);
  protected readonly deleteDataChoice = signal(false);
  protected readonly disconnectingConnectionId = signal<number | null>(null);

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

  protected readonly accountGroups = computed(() => {
    const groups = new Map<string, AccountConnectionGroup>();
    this.filteredAccounts().forEach((account) => {
      const groupKey =
        account.connectionId === null ? `account-${account.id}` : `${account.connectionId}`;
      const existing = groups.get(groupKey);
      if (existing) {
        existing.accounts.push(account);
        return;
      }

      groups.set(groupKey, {
        id: groupKey,
        connectionId: account.connectionId,
        institutionName: account.institutionName ?? account.name,
        accounts: [account],
      });
    });
    return Array.from(groups.values());
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

  protected openDisconnectDialog(group: AccountConnectionGroup): void {
    if (group.connectionId === null || this.disconnectingConnectionId() !== null) {
      return;
    }

    this.deleteDataChoice.set(false);
    this.disconnectDialog.set({
      connectionId: group.connectionId,
      institutionName: group.institutionName,
    });
  }

  protected closeDisconnectDialog(): void {
    if (this.disconnectingConnectionId() !== null) {
      return;
    }

    this.disconnectDialog.set(null);
  }

  protected confirmDisconnect(): void {
    const dialog = this.disconnectDialog();
    if (!dialog || this.disconnectingConnectionId() !== null) {
      return;
    }

    this.disconnectingConnectionId.set(dialog.connectionId);
    this.accountService
      .disconnectConnection(dialog.connectionId, this.deleteDataChoice())
      .pipe(
        finalize(() => this.disconnectingConnectionId.set(null)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => {
          this.disconnectDialog.set(null);
          this.refreshAccounts$.next();
          this.snackBar.open('Bank connection disconnected.', 'Dismiss', {
            duration: 4000,
          });
        },
        error: () => {
          this.snackBar.open('Unable to disconnect this bank connection.', 'Dismiss', {
            duration: 5000,
          });
        },
      });
  }
}

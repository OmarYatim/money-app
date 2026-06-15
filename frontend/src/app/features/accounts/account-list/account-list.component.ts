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
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { catchError, finalize, forkJoin, map, merge, of, startWith, Subject, switchMap } from 'rxjs';

import type { Account } from '../../../shared/models/account.model';
import type { SyncStatus } from '../../../shared/models/sync-status.model';
import { LanguageService } from '../../../core/i18n/language.service';
import { PageActionsComponent } from '../../../shared/components/page-actions/page-actions.component';
import { AccountConnectComponent } from '../account-connect/account-connect.component';
import { AccountService } from '../account.service';
import {
  ConnectedBanksComponent,
  type ConnectedBankGroup,
} from '../connected-banks/connected-banks.component';

interface DisconnectDialogState {
  connectionId: number;
  institutionName: string;
  accounts: Account[];
}

interface AccountListState {
  accounts: Account[];
  syncStatus: SyncStatus;
  loading: boolean;
  error: string | null;
}

interface AccountTypeTab {
  readonly id: string;
  readonly labelKey: string;
  readonly count: (accounts: Account[]) => number;
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
    TranslatePipe,
    PageActionsComponent,
    AccountConnectComponent,
    ConnectedBanksComponent,
  ],
  templateUrl: './account-list.component.html',
  styleUrl: './account-list.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AccountListComponent {
  private readonly accountService = inject(AccountService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly snackBar = inject(MatSnackBar);
  private readonly languageService = inject(LanguageService);
  private readonly translate = inject(TranslateService);
  private readonly refreshAccounts$ = new Subject<void>();

  protected readonly filterType = signal<string>('all');
  protected readonly disconnectDialog = signal<DisconnectDialogState | null>(null);
  protected readonly deleteDataChoice = signal(false);
  protected readonly disconnectingConnectionId = signal<number | null>(null);
  protected readonly renamingAccountId = signal<number | null>(null);
  protected readonly savingRenameAccountId = signal<number | null>(null);
  protected readonly openAccountMenuId = signal<number | null>(null);
  protected readonly renameDraft = signal('');

  protected readonly accountTypeTabs: readonly AccountTypeTab[] = [
    { id: 'all', labelKey: 'accounts.filters.allAccounts', count: (a: Account[]) => a.length },
    {
      id: 'checking',
      labelKey: 'accountTypes.checking',
      count: (a: Account[]) => a.filter((x) => (x.type ?? '').toLowerCase() === 'checking').length,
    },
    {
      id: 'savings',
      labelKey: 'accountTypes.savings',
      count: (a: Account[]) => a.filter((x) => (x.type ?? '').toLowerCase() === 'savings').length,
    },
    {
      id: 'credit',
      labelKey: 'accountTypes.credit',
      count: (a: Account[]) => a.filter((x) => (x.type ?? '').toLowerCase() === 'credit').length,
    },
    {
      id: 'investment',
      labelKey: 'accountTypes.investment',
      count: (a: Account[]) =>
        a.filter((x) => (x.type ?? '').toLowerCase() === 'investment').length,
    },
  ];

  protected readonly state = toSignal(
    merge(this.refreshAccounts$, this.accountService.accountsUpdated$).pipe(
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
              error: this.t('accounts.errors.loadConnected'),
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

  protected readonly connectedBankGroups = computed(() => {
    const groups = new Map<string, ConnectedBankGroup>();
    this.state().accounts.forEach((account) => {
      const groupKey = this.connectionGroupKey(account);
      const existing = groups.get(groupKey);
      if (existing) {
        existing.accounts.push(account);
        existing.totalBalance += account.balance;
        return;
      }

      const institutionName = account.institutionName ?? account.name;
      groups.set(groupKey, {
        id: groupKey,
        connectionId: account.connectionId,
        institutionName,
        accounts: [account],
        totalBalance: account.balance,
        initial: institutionName.trim().charAt(0).toUpperCase() || 'B',
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
    this.snackBar.open(this.t('accounts.messages.refreshed'), this.t('common.dismiss'), {
      duration: 3000,
    });
  }

  protected displayName(account: Account): string {
    return account.name;
  }

  protected accountLastFour(account: Account): string {
    return account.accountNumberLastFour ?? '----';
  }

  protected accountTypeLabel(type: string | null): string {
    this.languageService.currentLang();
    return this.t(`accountTypes.${(type ?? 'account').toLowerCase()}`);
  }

  protected toggleAccountMenu(accountId: number): void {
    this.openAccountMenuId.update((current) => (current === accountId ? null : accountId));
  }

  protected closeAccountMenu(): void {
    this.openAccountMenuId.set(null);
  }

  protected startRename(account: Account): void {
    this.openAccountMenuId.set(null);
    this.renamingAccountId.set(account.id);
    this.renameDraft.set(this.displayName(account));
  }

  protected updateRenameDraft(event: Event): void {
    this.renameDraft.set((event.target as HTMLInputElement).value);
  }

  protected commitRename(account: Account): void {
    if (this.renamingAccountId() !== account.id || this.savingRenameAccountId() !== null) {
      return;
    }

    const name = this.renameDraft().trim() || account.name;
    this.renamingAccountId.set(null);
    if (name === account.name) {
      return;
    }

    this.savingRenameAccountId.set(account.id);
    this.accountService
      .updateAccount(account.id, name)
      .pipe(
        finalize(() => this.savingRenameAccountId.set(null)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => {
          this.refreshAccounts$.next();
          this.snackBar.open(this.t('accounts.messages.renamed'), this.t('common.dismiss'), {
            duration: 3000,
          });
        },
        error: () => {
          this.snackBar.open(this.t('accounts.errors.rename'), this.t('common.dismiss'), {
            duration: 5000,
          });
        },
      });
  }

  protected cancelRename(account: Account): void {
    this.renameDraft.set(this.displayName(account));
    this.renamingAccountId.set(null);
  }

  protected handleRenameKeydown(event: KeyboardEvent, account: Account): void {
    if (event.key === 'Enter') {
      this.commitRename(account);
    }
    if (event.key === 'Escape') {
      this.cancelRename(account);
    }
  }

  protected reconnect(): void {
    this.snackBar.open(
      this.t('accounts.messages.useConnectToReauth'),
      this.t('common.dismiss'),
      { duration: 5000 },
    );
  }

  protected openDisconnectDialog(group: ConnectedBankGroup): void {
    if (group.connectionId === null || this.disconnectingConnectionId() !== null) {
      return;
    }

    this.openAccountMenuId.set(null);
    this.deleteDataChoice.set(false);
    this.disconnectDialog.set({
      connectionId: group.connectionId,
      institutionName: group.institutionName,
      accounts: group.accounts,
    });
  }

  protected openDisconnectDialogForAccount(account: Account): void {
    const group = this.connectedBankGroups().find((item) => item.id === this.connectionGroupKey(account));
    if (group) {
      this.openDisconnectDialog(group);
    }
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
          this.snackBar.open(this.t('accounts.messages.disconnected'), this.t('common.dismiss'), {
            duration: 4000,
          });
        },
        error: () => {
          this.snackBar.open(this.t('accounts.errors.disconnect'), this.t('common.dismiss'), {
            duration: 5000,
          });
        },
      });
  }

  private connectionGroupKey(account: Account): string {
    return account.connectionId === null
      ? `account-${account.id}`
      : `connection-${account.connectionId}`;
  }

  private t(key: string, params?: Record<string, unknown>): string {
    return this.translate.instant(key, params);
  }
}

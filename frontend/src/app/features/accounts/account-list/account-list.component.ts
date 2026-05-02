import { CurrencyPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { catchError, forkJoin, map, of, startWith, Subject, switchMap } from 'rxjs';

import type { Account } from '../../../shared/models/account.model';
import type { SyncStatus } from '../../../shared/models/sync-status.model';
import { AccountConnectComponent } from '../account-connect/account-connect.component';
import { AccountService } from '../account.service';

interface AccountListState {
  accounts: Account[];
  syncStatus: SyncStatus;
  loading: boolean;
  error: string | null;
}

const EMPTY_SYNC_STATUS: SyncStatus = {
  connectionsRequiringAction: [],
};

@Component({
  selector: 'app-account-list',
  imports: [
    CurrencyPipe,
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatProgressSpinnerModule,
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

  protected reloadAccounts(): void {
    this.refreshAccounts$.next();
    this.snackBar.open('Accounts refreshed.', 'Dismiss', {
      duration: 3000,
    });
  }

  protected reconnect(): void {
    this.snackBar.open('Use Connect a bank to re-authenticate the connection.', 'Dismiss', {
      duration: 5000,
    });
  }
}

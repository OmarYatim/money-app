import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable, Subject } from 'rxjs';

import { environment } from '../../../environments/environment';
import type { Account } from '../../shared/models/account.model';
import type { BankConnectResponse } from '../../shared/models/bank-connection.model';
import type { SyncStatus } from '../../shared/models/sync-status.model';

@Injectable({ providedIn: 'root' })
export class AccountService {
  private readonly http = inject(HttpClient);
  private readonly apiBaseUrl = environment.apiBaseUrl;
  private readonly accountsUpdatedSubject = new Subject<void>();

  readonly accountsUpdated$ = this.accountsUpdatedSubject.asObservable();

  connectBank(): Observable<BankConnectResponse> {
    return this.http.get<BankConnectResponse>(`${this.apiBaseUrl}/api/bank/connect`);
  }

  getAccounts(): Observable<Account[]> {
    return this.http.get<Account[]>(`${this.apiBaseUrl}/api/accounts`);
  }

  getTransactionFilterAccounts(): Observable<Account[]> {
    return this.http.get<Account[]>(`${this.apiBaseUrl}/api/accounts/transaction-filter-options`);
  }

  updateAccount(accountId: number, name: string): Observable<Account> {
    return this.http.patch<Account>(`${this.apiBaseUrl}/api/accounts/${accountId}`, { name });
  }

  disconnectConnection(connectionId: number, deleteData: boolean): Observable<void> {
    return this.http.delete<void>(
      `${this.apiBaseUrl}/api/bank/connections/${connectionId}`,
      { params: { deleteData: String(deleteData) } },
    );
  }

  getSyncStatus(): Observable<SyncStatus> {
    return this.http.get<SyncStatus>(`${this.apiBaseUrl}/api/sync/status`);
  }

  notifyAccountsUpdated(): void {
    this.accountsUpdatedSubject.next();
  }
}

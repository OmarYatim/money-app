import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable, Subject } from 'rxjs';

import { environment } from '../../../environments/environment';
import type { CategoryType } from '../../shared/models/category.model';
import type { Page } from '../../shared/models/page.model';
import type { Transaction } from '../../shared/models/transaction.model';
import type { TransactionSummary } from '../../shared/models/transaction-summary.model';

export interface TransactionQuery {
  page?: number;
  size?: number;
  accountId?: number | null;
  category?: CategoryType | null;
  minDate?: string | null;
  maxDate?: string | null;
  minAmount?: string | null;
  maxAmount?: string | null;
  keyword?: string | null;
  reviewed?: boolean | null;
  internalTransfer?: boolean | null;
  sort?: 'newest' | 'oldest' | 'largest' | 'smallest';
}

@Injectable({ providedIn: 'root' })
export class TransactionService {
  private readonly http = inject(HttpClient);
  private readonly apiBaseUrl = environment.apiBaseUrl;
  private readonly transactionsUpdatedSubject = new Subject<void>();

  readonly transactionsUpdated$ = this.transactionsUpdatedSubject.asObservable();

  getTransactions(query: TransactionQuery = {}): Observable<Page<Transaction>> {
    const params = this.queryParams(query);

    return this.http.get<Page<Transaction>>(`${this.apiBaseUrl}/api/transactions`, {
      params,
    });
  }

  getTransaction(id: number): Observable<Transaction> {
    return this.http.get<Transaction>(`${this.apiBaseUrl}/api/transactions/${id}`);
  }

  getTransactionSummary(query: TransactionQuery = {}): Observable<TransactionSummary> {
    const params = this.queryParams(query, false);

    return this.http.get<TransactionSummary>(`${this.apiBaseUrl}/api/transactions/summary`, {
      params,
    });
  }

  updateCategory(id: number, category: CategoryType): Observable<Transaction> {
    return this.http.patch<Transaction>(`${this.apiBaseUrl}/api/transactions/${id}/category`, {
      category,
    });
  }

  updateInternalTransfer(id: number, internalTransfer: boolean): Observable<Transaction> {
    return this.http.patch<Transaction>(
      `${this.apiBaseUrl}/api/transactions/${id}/internal-transfer`,
      { internalTransfer },
    );
  }

  updateReviewed(id: number, reviewed: boolean): Observable<Transaction> {
    return this.http.patch<Transaction>(`${this.apiBaseUrl}/api/transactions/${id}/reviewed`, {
      reviewed,
    });
  }

  notifyTransactionsUpdated(): void {
    this.transactionsUpdatedSubject.next();
  }

  private queryParams(query: TransactionQuery, includePage = true): HttpParams {
    let params = new HttpParams();
    if (includePage) {
      params = params.set('page', query.page ?? 0).set('size', query.size ?? 20);
    }

    if (query.accountId !== undefined && query.accountId !== null) {
      params = params.set('accountId', query.accountId);
    }

    if (query.category) {
      params = params.set('category', query.category);
    }

    if (query.minDate) {
      params = params.set('minDate', query.minDate);
    }

    if (query.maxDate) {
      params = params.set('maxDate', query.maxDate);
    }

    if (query.minAmount) {
      params = params.set('minAmount', query.minAmount);
    }

    if (query.maxAmount) {
      params = params.set('maxAmount', query.maxAmount);
    }

    if (query.keyword) {
      params = params.set('keyword', query.keyword);
    }

    if (query.reviewed !== undefined && query.reviewed !== null) {
      params = params.set('reviewed', query.reviewed);
    }

    if (query.internalTransfer !== undefined && query.internalTransfer !== null) {
      params = params.set('internalTransfer', query.internalTransfer);
    }

    if (query.sort) {
      params = params.set('sort', query.sort);
    }

    return params;
  }
}

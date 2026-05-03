import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import type { CategoryType } from '../../shared/models/category.model';
import type { Page } from '../../shared/models/page.model';
import type { Transaction } from '../../shared/models/transaction.model';

export interface TransactionQuery {
  page?: number;
  size?: number;
}

@Injectable({ providedIn: 'root' })
export class TransactionService {
  private readonly http = inject(HttpClient);
  private readonly apiBaseUrl = environment.apiBaseUrl;

  getTransactions(query: TransactionQuery = {}): Observable<Page<Transaction>> {
    return this.http.get<Page<Transaction>>(`${this.apiBaseUrl}/api/transactions`, {
      params: {
        page: query.page ?? 0,
        size: query.size ?? 20,
      },
    });
  }

  getTransaction(id: number): Observable<Transaction> {
    return this.http.get<Transaction>(`${this.apiBaseUrl}/api/transactions/${id}`);
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
}

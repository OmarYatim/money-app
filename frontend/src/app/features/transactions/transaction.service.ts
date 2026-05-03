import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import type { CategoryType } from '../../shared/models/category.model';
import type { Transaction } from '../../shared/models/transaction.model';

@Injectable({ providedIn: 'root' })
export class TransactionService {
  private readonly http = inject(HttpClient);
  private readonly apiBaseUrl = environment.apiBaseUrl;

  getTransactions(): Observable<Transaction[]> {
    return this.http.get<Transaction[]>(`${this.apiBaseUrl}/api/transactions`);
  }

  getTransaction(id: number): Observable<Transaction> {
    return this.http.get<Transaction>(`${this.apiBaseUrl}/api/transactions/${id}`);
  }

  updateCategory(id: number, category: CategoryType): Observable<Transaction> {
    return this.http.patch<Transaction>(`${this.apiBaseUrl}/api/transactions/${id}/category`, {
      category,
    });
  }
}

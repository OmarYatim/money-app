import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import type {
  IncomeExpenses,
  NetWorthHistory,
  SpendingByCategory,
} from '../../shared/models/report.model';

export interface SpendingByCategoryQuery {
  startDate: string;
  endDate: string;
  accountId?: number | null;
}

export interface IncomeExpensesQuery {
  months: number;
  accountId?: number | null;
}

@Injectable({ providedIn: 'root' })
export class ReportsService {
  private readonly http = inject(HttpClient);
  private readonly apiBaseUrl = environment.apiBaseUrl;

  getSpendingByCategory(query: SpendingByCategoryQuery): Observable<SpendingByCategory[]> {
    let params = new HttpParams().set('startDate', query.startDate).set('endDate', query.endDate);
    if (query.accountId !== undefined && query.accountId !== null) {
      params = params.set('accountId', query.accountId);
    }

    return this.http.get<SpendingByCategory[]>(`${this.apiBaseUrl}/api/reports/spending-by-category`, {
      params,
    });
  }

  getIncomeVsExpenses(query: IncomeExpensesQuery): Observable<IncomeExpenses[]> {
    let params = new HttpParams().set('months', query.months);
    if (query.accountId !== undefined && query.accountId !== null) {
      params = params.set('accountId', query.accountId);
    }

    return this.http.get<IncomeExpenses[]>(`${this.apiBaseUrl}/api/reports/income-vs-expenses`, {
      params,
    });
  }

  getNetWorthHistory(months: number): Observable<NetWorthHistory[]> {
    const params = new HttpParams().set('months', months);

    return this.http
      .get<NetWorthHistory[] | null>(`${this.apiBaseUrl}/api/reports/net-worth-history`, {
        params,
      })
      .pipe(map((history) => history ?? []));
  }
}

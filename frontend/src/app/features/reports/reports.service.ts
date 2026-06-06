import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import type {
  IncomeExpenses,
  SpendingByCategory,
  TopMerchant,
} from '../../shared/models/report.model';

interface ReportPeriodQuery {
  startDate: string;
  endDate: string;
  accountId?: number | null;
}

export type SpendingByCategoryQuery = ReportPeriodQuery;

export interface TopMerchantsQuery extends ReportPeriodQuery {
  limit?: number;
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

  getTopMerchants(query: TopMerchantsQuery): Observable<TopMerchant[]> {
    let params = new HttpParams()
      .set('startDate', query.startDate)
      .set('endDate', query.endDate)
      .set('limit', query.limit ?? 8);
    if (query.accountId !== undefined && query.accountId !== null) {
      params = params.set('accountId', query.accountId);
    }

    return this.http.get<TopMerchant[]>(`${this.apiBaseUrl}/api/reports/top-merchants`, {
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
}

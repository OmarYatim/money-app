import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable, Subject } from 'rxjs';

import { environment } from '../../../environments/environment';
import type { DashboardSummary } from '../../shared/models/dashboard.model';
import type { SyncStatus } from '../../shared/models/sync-status.model';

@Injectable({ providedIn: 'root' })
export class DashboardService {
  private readonly http = inject(HttpClient);
  private readonly apiBaseUrl = environment.apiBaseUrl;
  private readonly summaryUpdatedSubject = new Subject<boolean>();

  readonly summaryUpdated$ = this.summaryUpdatedSubject.asObservable();

  getSummary(): Observable<DashboardSummary> {
    return this.http.get<DashboardSummary>(`${this.apiBaseUrl}/api/dashboard/summary`);
  }

  syncNow(): Observable<SyncStatus> {
    return this.http.post<SyncStatus>(`${this.apiBaseUrl}/api/sync`, {});
  }

  notifySummaryUpdated(): void {
    this.summaryUpdatedSubject.next(false);
  }
}

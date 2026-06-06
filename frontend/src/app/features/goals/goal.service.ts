import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import type {
  Goal,
  GoalContribution,
  GoalContributionPayload,
  GoalPayload,
} from '../../shared/models/goal.model';

@Injectable({ providedIn: 'root' })
export class GoalService {
  private readonly http = inject(HttpClient);
  private readonly apiBaseUrl = environment.apiBaseUrl;

  getGoals(): Observable<Goal[]> {
    return this.http.get<Goal[]>(`${this.apiBaseUrl}/api/goals`);
  }

  getGoal(id: number): Observable<Goal> {
    return this.http.get<Goal>(`${this.apiBaseUrl}/api/goals/${id}`);
  }

  getContributions(goalId: number): Observable<GoalContribution[]> {
    return this.http.get<GoalContribution[]>(
      `${this.apiBaseUrl}/api/goals/${goalId}/contributions`,
    );
  }

  createGoal(payload: GoalPayload): Observable<Goal> {
    return this.http.post<Goal>(`${this.apiBaseUrl}/api/goals`, payload);
  }

  updateGoal(id: number, payload: GoalPayload): Observable<Goal> {
    return this.http.put<Goal>(`${this.apiBaseUrl}/api/goals/${id}`, payload);
  }

  archiveGoal(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiBaseUrl}/api/goals/${id}`);
  }

  addContribution(goalId: number, payload: GoalContributionPayload): Observable<Goal> {
    return this.http.post<Goal>(
      `${this.apiBaseUrl}/api/goals/${goalId}/contributions`,
      payload,
    );
  }
}

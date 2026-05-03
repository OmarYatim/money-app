import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import { catchError, map, of, startWith, Subject, switchMap } from 'rxjs';

import type { DashboardSummary } from '../../../shared/models/dashboard.model';
import { DashboardService } from '../dashboard.service';
import { SummaryCardComponent, type SummaryCardTone } from '../summary-card/summary-card.component';

interface DashboardState {
  summary: DashboardSummary | null;
  loading: boolean;
  error: string | null;
}

interface MetricTile {
  label: string;
  value: number;
  icon: string;
  tone: SummaryCardTone;
  showSign: boolean;
}

@Component({
  selector: 'app-dashboard',
  imports: [
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatProgressSpinnerModule,
    RouterLink,
    SummaryCardComponent,
  ],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardComponent {
  private readonly dashboardService = inject(DashboardService);
  private readonly refreshSummary$ = new Subject<void>();

  protected readonly state = toSignal(
    this.refreshSummary$.pipe(
      startWith(undefined),
      switchMap(() =>
        this.dashboardService.getSummary().pipe(
          map(
            (summary): DashboardState => ({
              summary,
              loading: false,
              error: null,
            }),
          ),
          catchError(() =>
            of({
              summary: null,
              loading: false,
              error: 'Unable to load dashboard summary.',
            }),
          ),
        ),
      ),
    ),
    {
      initialValue: {
        summary: null,
        loading: true,
        error: null,
      },
    },
  );

  protected readonly metrics = computed<MetricTile[]>(() => {
    const summary = this.state().summary;
    if (summary === null) {
      return [];
    }

    return [
      {
        label: 'Net Worth',
        value: summary.netWorth,
        icon: 'account_balance_wallet',
        tone: this.moneyTone(summary.netWorth),
        showSign: false,
      },
      {
        label: 'Cash Available',
        value: summary.totalAssets,
        icon: 'savings',
        tone: 'positive',
        showSign: false,
      },
      {
        label: 'Future Balance',
        value: summary.futureBalance,
        icon: 'calendar_month',
        tone: this.moneyTone(summary.futureBalance),
        showSign: false,
      },
      {
        label: 'Monthly Income',
        value: summary.monthlyIncome,
        icon: 'trending_up',
        tone: 'positive',
        showSign: true,
      },
      {
        label: 'Monthly Expenses',
        value: summary.monthlyExpenses,
        icon: 'trending_down',
        tone: 'negative',
        showSign: false,
      },
      {
        label: "Today's Spending",
        value: summary.dailySpending,
        icon: 'payments',
        tone: 'negative',
        showSign: false,
      },
    ];
  });

  protected readonly lastSyncedLabel = computed(() => {
    const lastSyncedAt = this.state().summary?.lastSyncedAt;
    if (!lastSyncedAt) {
      return 'Never synced';
    }

    const elapsedMinutes = Math.floor((Date.now() - new Date(lastSyncedAt).getTime()) / 60000);
    if (elapsedMinutes <= 0) {
      return 'just now';
    }

    if (elapsedMinutes < 60) {
      return `${elapsedMinutes} min ago`;
    }

    const elapsedHours = Math.floor(elapsedMinutes / 60);
    return elapsedHours === 1 ? '1 hour ago' : `${elapsedHours} hours ago`;
  });

  protected readonly emptySummary = computed(() => {
    const summary = this.state().summary;
    if (summary === null) {
      return false;
    }

    return (
      summary.totalAssets === 0 &&
      summary.totalLiabilities === 0 &&
      summary.monthlyIncome === 0 &&
      summary.monthlyExpenses === 0
    );
  });

  protected reloadSummary(): void {
    this.refreshSummary$.next();
  }

  private moneyTone(value: number): SummaryCardTone {
    if (value > 0) {
      return 'positive';
    }

    if (value < 0) {
      return 'negative';
    }

    return 'neutral';
  }
}

import { CurrencyPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import { catchError, concat, map, of, startWith, Subject, switchMap } from 'rxjs';
import type { Observable } from 'rxjs';

import type { DashboardSummary } from '../../../shared/models/dashboard.model';
import type { Transaction } from '../../../shared/models/transaction.model';
import { PageActionsComponent } from '../../../shared/components/page-actions/page-actions.component';
import { DashboardService } from '../dashboard.service';
import { TransactionService } from '../../transactions/transaction.service';

interface DashboardState {
  summary: DashboardSummary | null;
  loading: boolean;
  error: string | null;
}

interface NetWorthPoint { month: string; value: number; }
interface SpendingItem { label: string; color: string; amount: number; }
interface GoalItem { label: string; current: number; target: number; color: string; }
interface CashFlowBar { x: number; wB: number; inY: number; inH: number; outY: number; outH: number; hasIn: boolean; }

@Component({
  selector: 'app-dashboard',
  imports: [
    CurrencyPipe,
    DatePipe,
    MatIconModule,
    MatProgressSpinnerModule,
    RouterLink,
    PageActionsComponent,
  ],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardComponent {
  private readonly dashboardService = inject(DashboardService);
  private readonly transactionService = inject(TransactionService);
  private readonly refreshSummary$ = new Subject<boolean>();

  protected readonly selectedChartPeriod = signal('1Y');
  protected readonly chartPeriods = ['1M', '3M', '6M', '1Y', 'All'];

  protected readonly state = toSignal(
    this.refreshSummary$.pipe(
      startWith(false),
      switchMap((syncFirst) =>
        concat(
          of({ summary: null, loading: true, error: null }),
          this.loadSummary(syncFirst).pipe(
            map((summary): DashboardState => ({ summary, loading: false, error: null })),
            catchError(() => of({ summary: null, loading: false, error: 'Unable to refresh dashboard summary.' })),
          ),
        ),
      ),
    ),
    { initialValue: { summary: null, loading: true, error: null } },
  );

  protected readonly recentTransactions = toSignal(
    this.transactionService.getTransactions({ size: 6 }).pipe(
      map((page) => page.content),
      catchError(() => of([] as Transaction[])),
    ),
    { initialValue: [] as Transaction[] },
  );

  protected readonly lastSyncedLabel = computed(() => {
    const lastSyncedAt = this.state().summary?.lastSyncedAt;
    if (!lastSyncedAt) return 'Never synced';
    const elapsedMinutes = Math.floor((Date.now() - new Date(lastSyncedAt).getTime()) / 60000);
    if (elapsedMinutes <= 0) return 'just now';
    if (elapsedMinutes < 60) return `${elapsedMinutes} min ago`;
    const elapsedHours = Math.floor(elapsedMinutes / 60);
    return elapsedHours === 1 ? '1 hour ago' : `${elapsedHours} hours ago`;
  });

  protected readonly emptySummary = computed(() => {
    const summary = this.state().summary;
    if (summary === null) return false;
    return summary.totalAssets === 0 && summary.totalLiabilities === 0 &&
      summary.monthlyIncome === 0 && summary.monthlyExpenses === 0;
  });

  protected readonly netWorthInt = computed(() => Math.trunc(this.state().summary?.netWorth ?? 0));
  protected readonly netWorthDec = computed(() => {
    const nw = this.state().summary?.netWorth ?? 0;
    return Math.abs(Math.round((nw - Math.trunc(nw)) * 100)).toString().padStart(2, '0');
  });

  // Static chart data
  protected readonly netWorthData: NetWorthPoint[] = [
    { month: 'Jun', value: 14200 }, { month: 'Jul', value: 14850 },
    { month: 'Aug', value: 15420 }, { month: 'Sep', value: 15100 },
    { month: 'Oct', value: 15980 }, { month: 'Nov', value: 16420 },
    { month: 'Dec', value: 16890 }, { month: 'Jan', value: 17240 },
    { month: 'Feb', value: 17680 }, { month: 'Mar', value: 18120 },
    { month: 'Apr', value: 18540 }, { month: 'May', value: 19134 },
  ];

  protected readonly spendingData: SpendingItem[] = [
    { label: 'Housing', color: '#7c80f5', amount: 890 },
    { label: 'Groceries', color: '#5b5fef', amount: 320 },
    { label: 'Dining', color: '#d99838', amount: 184 },
    { label: 'Shopping', color: '#c8366f', amount: 152 },
    { label: 'Transport', color: '#3aa8c4', amount: 96 },
    { label: 'Utilities', color: '#9396a8', amount: 87 },
    { label: 'Subscription', color: '#7c80f5', amount: 31 },
    { label: 'Entertainment', color: '#e04a62', amount: 14 },
  ];
  protected readonly spendingTotal = this.spendingData.reduce((s, d) => s + d.amount, 0);

  protected readonly goals: GoalItem[] = [
    { label: 'Emergency fund', current: 5640, target: 9000, color: '#5b5fef' },
    { label: 'Trip to Japan', current: 1840, target: 4500, color: '#d99838' },
    { label: 'New laptop', current: 620, target: 2400, color: '#2cad6a' },
  ];

  // SVG chart calculations
  protected readonly netWorthChart = this.buildNetWorthChart();
  protected readonly cashFlowChart = this.buildCashFlowChart();

  protected reloadSummary(): void {
    this.refreshSummary$.next(true);
  }

  protected categoryIcon(category: string): string {
    const map: Record<string, string> = {
      GROCERIES: 'local_grocery_store', INCOME: 'payments', SAVINGS: 'savings',
      DINING: 'restaurant', SHOPPING: 'shopping_bag', SUBSCRIPTION: 'subscriptions',
      TRANSPORT: 'directions_bus', TRAVEL: 'flight', TRANSFER: 'sync_alt',
      UTILITIES: 'bolt', RENT: 'home', HEALTH: 'local_hospital',
      ENTERTAINMENT: 'movie', EDUCATION: 'school', OTHER: 'more_horiz',
    };
    return map[category.toUpperCase()] ?? 'receipt';
  }

  protected categoryIconBg(category: string): string {
    const cat = category.toUpperCase();
    if (['GROCERIES', 'INCOME', 'SAVINGS'].includes(cat)) return 'rgba(44,173,106,0.14)';
    if (['DINING', 'SHOPPING', 'SUBSCRIPTION'].includes(cat)) return 'rgba(124,58,237,0.12)';
    if (['TRANSPORT', 'TRAVEL', 'TRANSFER'].includes(cat)) return 'rgba(91,95,239,0.12)';
    if (['UTILITIES', 'RENT', 'HEALTH', 'ENTERTAINMENT', 'EDUCATION'].includes(cat)) return 'rgba(217,152,56,0.14)';
    return 'rgba(147,150,168,0.14)';
  }

  protected categoryIconColor(category: string): string {
    const cat = category.toUpperCase();
    if (['GROCERIES', 'INCOME', 'SAVINGS'].includes(cat)) return '#2cad6a';
    if (['DINING', 'SHOPPING', 'SUBSCRIPTION'].includes(cat)) return '#7c3aed';
    if (['TRANSPORT', 'TRAVEL', 'TRANSFER'].includes(cat)) return '#5b5fef';
    if (['UTILITIES', 'RENT', 'HEALTH', 'ENTERTAINMENT', 'EDUCATION'].includes(cat)) return '#d99838';
    return '#9396a8';
  }

  protected categoryLabel(category: string): string {
    return category.toLowerCase().split('_')
      .map((w) => w.charAt(0).toUpperCase() + w.slice(1)).join(' ');
  }

  protected goalPct(goal: GoalItem): number {
    return Math.round((goal.current / goal.target) * 100);
  }

  private loadSummary(syncFirst: boolean): Observable<DashboardSummary> {
    if (!syncFirst) return this.dashboardService.getSummary();
    return this.dashboardService.syncNow().pipe(switchMap(() => this.dashboardService.getSummary()));
  }

  private buildNetWorthChart() {
    const data = this.netWorthData;
    const w = 520, h = 140, pad = 8;
    const min = Math.min(...data.map((d) => d.value));
    const max = Math.max(...data.map((d) => d.value));
    const xStep = (w - pad * 2) / (data.length - 1);
    const yScale = (v: number) => h - pad - ((v - min) / (max - min)) * (h - pad * 2);
    const points = data.map((d, i): [number, number] => [pad + i * xStep, yScale(d.value)]);
    const path = points.map((p, i) => (i === 0 ? `M${p[0].toFixed(1)},${p[1].toFixed(1)}` : `L${p[0].toFixed(1)},${p[1].toFixed(1)}`)).join(' ');
    const last = points[points.length - 1];
    const area = `${path} L${last[0].toFixed(1)},${h} L${pad},${h} Z`;
    const gridLines = [0, 1, 2, 3].map((i) => pad + i * (h - pad * 2) / 3);
    return { path, area, points, data, gridLines, w, h, pad };
  }

  private buildCashFlowChart() {
    const data = Array.from({ length: 30 }, (_, i) => {
      const seed = (i * 9301 + 49297) % 233280;
      const r = seed / 233280;
      const inflow = i === 4 || i === 22 ? 1400 + r * 800 : r > 0.85 ? r * 200 : 0;
      const outflow = 30 + r * 180 + (i % 7 === 0 ? 200 : 0);
      return { inflow, outflow };
    });
    const w = 600, h = 180, padL = 30, padR = 8, padT = 8, padB = 18;
    const max = Math.max(...data.map((d) => Math.max(d.inflow, d.outflow)));
    const cx = (w - padL - padR) / data.length;
    const mid = (h - padT - padB) / 2 + padT;
    const bars: CashFlowBar[] = data.map((d, i) => {
      const x = padL + i * cx + 1;
      const wB = cx - 2;
      const inH = (d.inflow / max) * (h - padT - padB) / 2;
      const outH = (d.outflow / max) * (h - padT - padB) / 2;
      return { x, wB, inY: mid - inH, inH, outY: mid, outH, hasIn: d.inflow > 0 };
    });
    const gridRatios = [1, 0.5, 0, -0.5, -1];
    const gridLines = gridRatios.map((r) => ({
      y: mid - r * (h - padT - padB) / 2,
      label: r === 0 ? '0' : `${r > 0 ? '+' : '−'}${Math.round(Math.abs(r) * max / 100) * 100}`,
      dashed: r !== 0,
    }));
    return { bars, gridLines, mid, w, h, padL, padR };
  }
}

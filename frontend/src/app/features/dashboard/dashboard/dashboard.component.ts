import { CurrencyPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import { catchError, concat, map, of, startWith, Subject, switchMap } from 'rxjs';
import type { Observable } from 'rxjs';

import type { Account } from '../../../shared/models/account.model';
import type { DashboardSummary } from '../../../shared/models/dashboard.model';
import type { Transaction } from '../../../shared/models/transaction.model';
import { PageActionsComponent } from '../../../shared/components/page-actions/page-actions.component';
import { AccountService } from '../../accounts/account.service';
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
interface HeroAccount { label: string; balance: number; color: string; }

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
  private readonly accountService = inject(AccountService);
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
  protected readonly accounts = toSignal(
    this.accountService.getAccounts().pipe(catchError(() => of([] as Account[]))),
    { initialValue: [] as Account[] },
  );
  protected readonly chartTransactions = toSignal(
    this.loadChartTransactions().pipe(catchError(() => of([] as Transaction[]))),
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
  protected readonly savingsRate = computed(() => {
    const summary = this.state().summary;
    if (!summary || summary.monthlyIncome <= 0) return 0;

    return Math.round(((summary.monthlyIncome - summary.monthlyExpenses) / summary.monthlyIncome) * 100);
  });
  protected readonly cashAvailable = computed(() =>
    this.accounts()
      .filter((account) => ['checking', 'savings'].includes((account.type ?? '').toLowerCase()))
      .reduce((sum, account) => sum + account.balance, 0),
  );

  protected readonly heroAccounts = computed<HeroAccount[]>(() =>
    this.accounts().map((account, index) => ({
      label: account.institutionName ?? account.name,
      balance: account.balance,
      color: this.accountColor(index),
    })),
  );

  protected readonly netWorthData = computed(() =>
    this.buildNetWorthData(this.accounts(), this.chartTransactions()),
  );

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
  protected readonly netWorthChart = computed(() => this.buildNetWorthChart(this.netWorthData()));
  protected readonly cashFlowChart = computed(() => this.buildCashFlowChart(this.chartTransactions()));

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

  private loadChartTransactions(): Observable<Transaction[]> {
    return this.transactionService.getTransactions({ size: 1000 }).pipe(map((page) => page.content));
  }

  private buildNetWorthData(accounts: Account[], transactions: Transaction[]): NetWorthPoint[] {
    const now = new Date();
    const months = this.chartMonthCount();
    const currentNetWorth = accounts.reduce((sum, account) => sum + account.balance, 0);

    return Array.from({ length: months }, (_, index) => {
      const pointDate = new Date(now.getFullYear(), now.getMonth() - (months - index - 1), 1);
      const nextMonth = new Date(pointDate.getFullYear(), pointDate.getMonth() + 1, 1);
      const laterTransactionValue = transactions
        .filter((transaction) => !transaction.internalTransfer)
        .filter((transaction) => new Date(transaction.date) >= nextMonth)
        .reduce((sum, transaction) => sum + transaction.value, 0);
      return {
        month: pointDate.toLocaleDateString('en-GB', { month: 'short' }),
        value: currentNetWorth - laterTransactionValue,
      };
    });
  }

  private chartMonthCount(): number {
    if (this.selectedChartPeriod() === '1M') return 2;
    if (this.selectedChartPeriod() === '3M') return 4;
    if (this.selectedChartPeriod() === '6M') return 7;
    return 12;
  }

  private buildNetWorthChart(data: NetWorthPoint[]) {
    const w = 520, h = 140, pad = 8;
    if (data.length === 0) {
      return { path: '', area: '', points: [] as [number, number][], data, gridLines: [], w, h, pad };
    }
    const min = Math.min(...data.map((d) => d.value));
    const max = Math.max(...data.map((d) => d.value));
    const range = max - min || 1;
    const xStep = data.length === 1 ? 0 : (w - pad * 2) / (data.length - 1);
    const yScale = (v: number) => h - pad - ((v - min) / range) * (h - pad * 2);
    const points = data.map((d, i): [number, number] => [pad + i * xStep, yScale(d.value)]);
    const path = points.map((p, i) => (i === 0 ? `M${p[0].toFixed(1)},${p[1].toFixed(1)}` : `L${p[0].toFixed(1)},${p[1].toFixed(1)}`)).join(' ');
    const last = points[points.length - 1];
    const area = `${path} L${last[0].toFixed(1)},${h} L${pad},${h} Z`;
    const gridLines = [0, 1, 2, 3].map((i) => pad + i * (h - pad * 2) / 3);
    return { path, area, points, data, gridLines, w, h, pad };
  }

  private accountColor(index: number): string {
    const colors = ['#5b5fef', '#2cad6a', '#d99838', '#7c3aed', '#3aa8c4', '#c8366f'];
    return colors[index % colors.length];
  }

  private buildCashFlowChart(transactions: Transaction[]) {
    const now = new Date();
    const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
    const start = new Date(today);
    start.setDate(start.getDate() - 29);
    const data = Array.from({ length: 30 }, (_, index) => {
      const date = new Date(start);
      date.setDate(start.getDate() + index);
      return { date: this.isoDate(date), inflow: 0, outflow: 0 };
    });
    const byDate = new Map(data.map((day) => [day.date, day]));

    transactions
      .filter((transaction) => !transaction.internalTransfer)
      .forEach((transaction) => {
        const transactionDate = new Date(transaction.date);
        if (transactionDate < start || transactionDate > today) return;

        const day = byDate.get(this.isoDate(transactionDate));
        if (!day) return;

        if (transaction.value > 0) {
          day.inflow += transaction.value;
        } else {
          day.outflow += Math.abs(transaction.value);
        }
      });

    const w = 600, h = 180, padL = 30, padR = 8, padT = 8, padB = 18;
    const max = Math.max(...data.map((d) => Math.max(d.inflow, d.outflow)), 1);
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

  private isoDate(date: Date): string {
    return [
      date.getFullYear(),
      String(date.getMonth() + 1).padStart(2, '0'),
      String(date.getDate()).padStart(2, '0'),
    ].join('-');
  }
}

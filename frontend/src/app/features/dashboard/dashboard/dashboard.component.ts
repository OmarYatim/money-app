import { CurrencyPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { catchError, concat, exhaustMap, firstValueFrom, map, merge, of, startWith, Subject, switchMap, timer } from 'rxjs';
import type { Observable } from 'rxjs';

import type { Account } from '../../../shared/models/account.model';
import type { DashboardSummary } from '../../../shared/models/dashboard.model';
import type { SyncStatus } from '../../../shared/models/sync-status.model';
import type { Transaction } from '../../../shared/models/transaction.model';
import { PageActionsComponent } from '../../../shared/components/page-actions/page-actions.component';
import { LanguageService } from '../../../core/i18n/language.service';
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
interface CashFlowBar { x: number; wB: number; inY: number; inH: number; outY: number; outH: number; hasIn: boolean; }
interface HeroAccount { label: string; balance: number; color: string; }

const EMPTY_SYNC_STATUS: SyncStatus = {
  lastSyncedAt: null,
  connectionsRequiringAction: [],
  hasSyncError: false,
};

@Component({
  selector: 'app-dashboard',
  imports: [
    CurrencyPipe,
    DatePipe,
    MatIconModule,
    MatProgressSpinnerModule,
    RouterLink,
    TranslatePipe,
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
  private readonly languageService = inject(LanguageService);
  private readonly translate = inject(TranslateService);
  private readonly refreshSummary$ = new Subject<boolean>();

  protected readonly selectedChartPeriod = signal('1Y');
  protected readonly chartPeriods = ['1M', '3M', '6M', '1Y', 'All'];
  protected readonly syncActionError = signal<string | null>(null);

  protected readonly state = toSignal(
    merge(this.refreshSummary$, this.dashboardService.summaryUpdated$).pipe(
      startWith(false),
      exhaustMap((syncFirst) =>
        concat(
          of({ summary: null, loading: true, error: null }),
          this.loadSummary(syncFirst).pipe(
            map((summary): DashboardState => ({ summary, loading: false, error: null })),
            catchError(() => of({ summary: null, loading: false, error: this.t('dashboard.errors.refreshSummary') })),
          ),
        ),
      ),
    ),
    { initialValue: { summary: null, loading: true, error: null } },
  );

  protected readonly recentTransactions = toSignal(
    this.transactionService.transactionsUpdated$.pipe(
      startWith(undefined),
      switchMap(() =>
        this.transactionService.getTransactions({ size: 6 }).pipe(
          map((page) => page.content),
          catchError(() => of([] as Transaction[])),
        ),
      ),
    ),
    { initialValue: [] as Transaction[] },
  );
  protected readonly accounts = toSignal(
    this.accountService.accountsUpdated$.pipe(
      startWith(undefined),
      switchMap(() => this.accountService.getAccounts().pipe(catchError(() => of([] as Account[])))),
    ),
    { initialValue: [] as Account[] },
  );
  protected readonly syncStatus = toSignal(
    merge(timer(0, 60000), this.accountService.accountsUpdated$, this.transactionService.transactionsUpdated$).pipe(
      switchMap(() => this.accountService.getSyncStatus()),
      catchError(() => of(EMPTY_SYNC_STATUS)),
    ),
    { initialValue: EMPTY_SYNC_STATUS },
  );
  protected readonly chartTransactions = toSignal(
    this.transactionService.transactionsUpdated$.pipe(
      startWith(undefined),
      switchMap(() => this.loadChartTransactions().pipe(catchError(() => of([] as Transaction[])))),
    ),
    { initialValue: [] as Transaction[] },
  );

  protected readonly lastSyncedLabel = computed(() => {
    const lastSyncedAt = this.syncStatus().lastSyncedAt ?? this.state().summary?.lastSyncedAt;
    this.languageService.currentLang();
    if (!lastSyncedAt) return this.t('dashboard.sync.never');
    const elapsedMinutes = Math.floor((Date.now() - new Date(lastSyncedAt).getTime()) / 60000);
    if (elapsedMinutes <= 0) return this.t('dashboard.sync.justNow');
    if (elapsedMinutes < 60) {
      return this.t('dashboard.sync.minutesAgo', { count: elapsedMinutes });
    }
    const elapsedHours = Math.floor(elapsedMinutes / 60);
    return elapsedHours === 1
      ? this.t('dashboard.sync.oneHourAgo')
      : this.t('dashboard.sync.hoursAgo', { count: elapsedHours });
  });
  protected readonly hasSyncAlert = computed(
    () => this.syncStatus().hasSyncError || this.syncStatus().connectionsRequiringAction.length > 0,
  );
  protected readonly syncBannerText = computed(() => {
    const actionCount = this.syncStatus().connectionsRequiringAction.length;
    this.languageService.currentLang();
    if (actionCount > 0) {
      return actionCount === 1
        ? this.t('dashboard.sync.oneConnectionNeedsAction')
        : this.t('dashboard.sync.connectionsNeedAction', { count: actionCount });
    }

    return this.t('dashboard.sync.lastSyncFailed');
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

  protected readonly spendingData = computed<SpendingItem[]>(() => {
    const now = new Date();
    const monthStart = new Date(now.getFullYear(), now.getMonth(), 1);
    const nextMonth = new Date(now.getFullYear(), now.getMonth() + 1, 1);
    const totals = new Map<string, number>();

    this.chartTransactions()
      .filter((tx) => !tx.internalTransfer && tx.value < 0)
      .filter((tx) => {
        const date = new Date(tx.date);
        return date >= monthStart && date < nextMonth;
      })
      .forEach((tx) => {
        const cat = tx.category.toUpperCase();
        totals.set(cat, (totals.get(cat) ?? 0) + Math.abs(tx.value));
      });

    return Array.from(totals.entries())
      .map(([category, amount]) => ({
        label: this.categoryLabel(category),
        color: this.categoryIconColor(category),
        amount: Math.round(amount),
      }))
      .sort((a, b) => b.amount - a.amount);
  });

  protected readonly spendingTotal = computed(() =>
    this.spendingData().reduce((s, d) => s + d.amount, 0),
  );

  // SVG chart calculations
  protected readonly netWorthChart = computed(() => this.buildNetWorthChart(this.netWorthData()));
  protected readonly cashFlowChart = computed(() => this.buildCashFlowChart(this.chartTransactions()));

  protected reloadSummary(): void {
    this.refreshSummary$.next(true);
  }

  protected async resolveSyncIssue(): Promise<void> {
    this.syncActionError.set(null);
    try {
      const response = await firstValueFrom(this.accountService.connectBank());
      window.location.href = response.webviewUrl;
    } catch {
      this.syncActionError.set(this.t('dashboard.errors.openReauth'));
    }
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
    this.languageService.currentLang();
    return this.t(`categories.${category.toLowerCase()}`);
  }

  private loadSummary(syncFirst: boolean): Observable<DashboardSummary> {
    if (!syncFirst) return this.dashboardService.getSummary();
    return this.dashboardService.syncNow().pipe(switchMap(() => this.dashboardService.getSummary()));
  }

  private loadChartTransactions(): Observable<Transaction[]> {
    const start = new Date();
    start.setFullYear(start.getFullYear() - 1);
    const minDate = this.isoDate(start);
    return this.transactionService.getTransactions({ minDate, size: 300 }).pipe(map((page) => page.content));
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
        month: pointDate.toLocaleDateString(this.dateLocale(), { month: 'short' }),
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

  private dateLocale(): string {
    return this.languageService.currentLang() === 'en' ? 'en-GB' : this.languageService.currentLang();
  }

  private t(key: string, params?: Record<string, unknown>): string {
    return this.translate.instant(key, params);
  }
}

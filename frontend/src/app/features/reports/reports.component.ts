import { CurrencyPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { PageActionsComponent } from '../../shared/components/page-actions/page-actions.component';
import type { Account } from '../../shared/models/account.model';
import type {
  IncomeExpenses,
  NetWorthHistory,
  SpendingByCategory,
} from '../../shared/models/report.model';
import { AccountService } from '../accounts/account.service';
import { IncomeExpensesChartComponent } from './income-expenses-chart/income-expenses-chart.component';
import { NetWorthChartComponent } from './net-worth-chart/net-worth-chart.component';
import { ReportsService } from './reports.service';
import { SpendingChartComponent } from './spending-chart/spending-chart.component';

type ReportRange = 'month' | 'quarter' | 'sixMonths' | 'ytd' | 'year';

interface ReportsState {
  spending: SpendingByCategory[];
  incomeExpenses: IncomeExpenses[];
  netWorthHistory: NetWorthHistory[];
  loading: boolean;
  error: string | null;
}

interface ReportRangeOption {
  value: ReportRange;
  label: string;
  months: number;
}

const RANGE_OPTIONS: ReportRangeOption[] = [
  { value: 'month', label: 'This Month', months: 1 },
  { value: 'quarter', label: 'Last 3 Months', months: 3 },
  { value: 'sixMonths', label: 'Last 6 Months', months: 6 },
  { value: 'ytd', label: 'YTD', months: new Date().getMonth() + 1 },
  { value: 'year', label: 'Year', months: 12 },
];

const EMPTY_STATE: ReportsState = {
  spending: [],
  incomeExpenses: [],
  netWorthHistory: [],
  loading: true,
  error: null,
};

@Component({
  selector: 'app-reports',
  imports: [
    CurrencyPipe,
    MatButtonToggleModule,
    MatIconModule,
    MatProgressSpinnerModule,
    PageActionsComponent,
    IncomeExpensesChartComponent,
    NetWorthChartComponent,
    SpendingChartComponent,
  ],
  templateUrl: './reports.component.html',
  styleUrl: './reports.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReportsComponent {
  private readonly accountService = inject(AccountService);
  private readonly reportsService = inject(ReportsService);
  private readonly router = inject(Router);

  protected readonly rangeOptions = RANGE_OPTIONS;
  protected readonly selectedRange = signal<ReportRange>('month');
  protected readonly selectedAccountId = signal<number | null>(null);
  protected readonly accounts = signal<Account[]>([]);
  protected readonly state = signal<ReportsState>(EMPTY_STATE);

  protected readonly currentRange = computed(
    () =>
      this.rangeOptions.find((option) => option.value === this.selectedRange()) ??
      this.rangeOptions[0],
  );

  protected readonly dateRange = computed(() => this.buildDateRange(this.currentRange().months));

  protected readonly subtitle = computed(
    () => `${this.periodLabel()} · ${this.selectedAccountLabel()}`,
  );

  protected readonly totalIncome = computed(() =>
    this.state().incomeExpenses.reduce((sum, item) => sum + item.totalIncome, 0),
  );

  protected readonly totalSpending = computed(() =>
    this.state().incomeExpenses.reduce((sum, item) => sum + item.totalExpenses, 0),
  );

  protected readonly netCashFlow = computed(() => this.totalIncome() - this.totalSpending());

  protected readonly savingsRate = computed(() => {
    if (this.totalIncome() <= 0) {
      return 0;
    }

    return Math.round((this.netCashFlow() / this.totalIncome()) * 100);
  });

  protected readonly spendingTotal = computed(() =>
    this.state().spending.reduce((sum, item) => sum + item.totalAmount, 0),
  );

  protected readonly showNetCashFlowLine = computed(() => this.state().incomeExpenses.length > 1);

  protected readonly forecastMonthlyDelta = computed(() => {
    const months = Math.max(this.state().incomeExpenses.length, 1);
    return this.netCashFlow() / months;
  });

  protected readonly savingsRateGaugeBackground = computed(() => {
    const progressDegrees = Math.min(Math.max(this.savingsRate(), 0), 100) * 1.8;
    return `conic-gradient(from 270deg, #5b5fef 0deg ${progressDegrees}deg, var(--ink-100) ${progressDegrees}deg 180deg, transparent 180deg 360deg)`;
  });

  constructor() {
    void this.loadAccounts();
    void this.reloadReports();
  }

  protected async selectRange(range: ReportRange): Promise<void> {
    this.selectedRange.set(range);
    await this.reloadReports();
  }

  protected async selectAccount(event: Event): Promise<void> {
    const value = (event.target as HTMLSelectElement).value;
    this.selectedAccountId.set(value ? Number(value) : null);
    await this.reloadReports();
  }

  protected async reloadReports(): Promise<void> {
    const dateRange = this.dateRange();
    const accountId = this.selectedAccountId();
    const months = this.currentRange().months;
    this.state.set({ ...this.state(), loading: true, error: null });

    try {
      const [spending, incomeExpenses, netWorthHistory] = await Promise.all([
        firstValueFrom(
          this.reportsService.getSpendingByCategory({
            startDate: dateRange.startDate,
            endDate: dateRange.endDate,
            accountId,
          }),
        ),
        firstValueFrom(this.reportsService.getIncomeVsExpenses({ months, accountId })),
        firstValueFrom(this.reportsService.getNetWorthHistory(months)),
      ]);

      this.state.set({
        spending,
        incomeExpenses,
        netWorthHistory,
        loading: false,
        error: null,
      });
    } catch {
      this.state.set({
        ...this.state(),
        loading: false,
        error: 'Unable to load reports.',
      });
    }
  }

  protected drillDownToCategory(category: string): void {
    const range = this.dateRange();
    void this.router.navigate(['/transactions'], {
      queryParams: {
        category,
        minDate: range.startDate,
        maxDate: range.endDate,
      },
    });
  }

  protected selectedAccountLabel(): string {
    const accountId = this.selectedAccountId();
    if (accountId === null) {
      return 'All accounts';
    }

    const account = this.accounts().find((item) => item.id === accountId);
    return account ? this.accountLabel(account) : 'Selected account';
  }

  protected accountLabel(account: Account): string {
    const lastFour = account.accountNumberLastFour ? ` · ${account.accountNumberLastFour}` : '';
    return `${account.name}${lastFour}`;
  }

  protected periodLabel(): string {
    const range = this.dateRange();
    return `${this.formatShortDate(range.startDate)} – ${this.formatShortDate(range.endDate)}`;
  }

  private async loadAccounts(): Promise<void> {
    try {
      this.accounts.set(await firstValueFrom(this.accountService.getTransactionFilterAccounts()));
    } catch {
      this.accounts.set([]);
    }
  }

  private buildDateRange(months: number): { startDate: string; endDate: string } {
    const today = new Date();
    const endDate = this.isoDate(today);
    const start = new Date(today.getFullYear(), today.getMonth() - months + 1, 1);
    return { startDate: this.isoDate(start), endDate };
  }

  private isoDate(date: Date): string {
    return [
      date.getFullYear(),
      String(date.getMonth() + 1).padStart(2, '0'),
      String(date.getDate()).padStart(2, '0'),
    ].join('-');
  }

  private formatShortDate(date: string): string {
    return new Intl.DateTimeFormat('en', { month: 'short', day: 'numeric' }).format(
      new Date(`${date}T00:00:00`),
    );
  }
}

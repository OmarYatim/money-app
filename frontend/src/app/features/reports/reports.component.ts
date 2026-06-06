import { CurrencyPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { PageActionsComponent } from '../../shared/components/page-actions/page-actions.component';
import type { Account } from '../../shared/models/account.model';
import type { IncomeExpenses, SpendingByCategory, TopMerchant } from '../../shared/models/report.model';
import { AccountService } from '../accounts/account.service';
import { IncomeExpensesChartComponent } from './income-expenses-chart/income-expenses-chart.component';
import { ReportsService } from './reports.service';
import { SpendingChartComponent } from './spending-chart/spending-chart.component';

type ReportRange = 'month' | 'quarter' | 'sixMonths' | 'ytd' | 'year';

interface ReportsState {
  spending: SpendingByCategory[];
  incomeExpenses: IncomeExpenses[];
  topMerchants: TopMerchant[];
  loading: boolean;
  error: string | null;
}

interface ReportRangeOption {
  value: ReportRange;
  label: string;
  months: number;
}

interface FixedFlexibleGroup {
  key: string;
  label: string;
  icon: string;
  totalAmount: number;
  percentage: number;
  color: string;
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
  topMerchants: [],
  loading: true,
  error: null,
};

const CATEGORY_COLORS: Record<string, string> = {
  GROCERIES: '#5b5fef',
  DINING: '#d99838',
  TRANSPORT: '#3aa8c4',
  UTILITIES: '#9396a8',
  RENT: '#7c80f5',
  HEALTH: '#2cad6a',
  ENTERTAINMENT: '#e04a62',
  SHOPPING: '#c8366f',
  TRAVEL: '#3aa8c4',
  EDUCATION: '#7c80f5',
  INCOME: '#1f8a52',
  TRANSFER: '#6b6e85',
  SAVINGS: '#2cad6a',
  SUBSCRIPTION: '#7c80f5',
  OTHER: '#9396a8',
};

const FIXED_FLEXIBLE_CATEGORY_GROUPS: readonly {
  key: string;
  label: string;
  icon: string;
  color: string;
  categories: readonly string[];
}[] = [
  {
    key: 'fixed',
    label: 'Fixed costs',
    icon: 'home',
    color: '#5b5fef',
    categories: ['RENT', 'UTILITIES', 'TRANSPORT'],
  },
  {
    key: 'subscriptions',
    label: 'Recurring subs',
    icon: 'sync',
    color: '#7c80f5',
    categories: ['SUBSCRIPTION'],
  },
  {
    key: 'flexible',
    label: 'Flexible spend',
    icon: 'tune',
    color: '#d99838',
    categories: [
      'GROCERIES',
      'DINING',
      'HEALTH',
      'ENTERTAINMENT',
      'SHOPPING',
      'TRAVEL',
      'EDUCATION',
      'OTHER',
    ],
  },
];

@Component({
  selector: 'app-reports',
  imports: [
    CurrencyPipe,
    MatButtonToggleModule,
    MatIconModule,
    MatProgressSpinnerModule,
    PageActionsComponent,
    IncomeExpensesChartComponent,
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

  protected readonly fixedFlexibleGroups = computed(() => this.buildFixedFlexibleGroups());

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
      const [spending, incomeExpenses, topMerchants] = await Promise.all([
        firstValueFrom(
          this.reportsService.getSpendingByCategory({
            startDate: dateRange.startDate,
            endDate: dateRange.endDate,
            accountId,
          }),
        ),
        firstValueFrom(this.reportsService.getIncomeVsExpenses({ months, accountId })),
        firstValueFrom(
          this.reportsService.getTopMerchants({
            startDate: dateRange.startDate,
            endDate: dateRange.endDate,
            accountId,
            limit: 6,
          }),
        ),
      ]);

      this.state.set({
        spending,
        incomeExpenses,
        topMerchants,
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

  protected categoryColor(category: string): string {
    return CATEGORY_COLORS[category.toUpperCase()] ?? CATEGORY_COLORS['OTHER'];
  }

  protected categoryLabel(category: string): string {
    return category
      .toLowerCase()
      .split('_')
      .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
      .join(' ');
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

  private buildFixedFlexibleGroups(): FixedFlexibleGroup[] {
    const spending = this.state().spending;
    const spendingByCategory = new Map(
      spending.map((item) => [item.category.toUpperCase(), item.totalAmount]),
    );
    const assignedCategories = new Set(
      FIXED_FLEXIBLE_CATEGORY_GROUPS.flatMap((group) => group.categories),
    );
    const total = this.spendingTotal();

    return FIXED_FLEXIBLE_CATEGORY_GROUPS.map((group) => {
      const groupedTotal = group.categories.reduce(
        (sum, category) => sum + (spendingByCategory.get(category) ?? 0),
        0,
      );
      const unassignedTotal =
        group.key === 'flexible'
          ? spending
              .filter((item) => !assignedCategories.has(item.category.toUpperCase()))
              .reduce((sum, item) => sum + item.totalAmount, 0)
          : 0;
      const totalAmount = groupedTotal + unassignedTotal;

      return {
        key: group.key,
        label: group.label,
        icon: group.icon,
        totalAmount,
        percentage: total > 0 ? Math.round((totalAmount / total) * 100) : 0,
        color: group.color,
      };
    });
  }
}

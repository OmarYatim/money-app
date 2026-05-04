import { CurrencyPipe, DatePipe, isPlatformBrowser } from '@angular/common';
import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  ElementRef,
  inject,
  PLATFORM_ID,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged, firstValueFrom } from 'rxjs';

import type { Account } from '../../../shared/models/account.model';
import { CATEGORY_TYPES, type CategoryType } from '../../../shared/models/category.model';
import type { Transaction } from '../../../shared/models/transaction.model';
import { CategoryColorPipe } from '../../../shared/pipes/category-color.pipe';
import { AccountService } from '../../accounts/account.service';
import { TransactionService, type TransactionQuery } from '../transaction.service';

interface TransactionListState {
  transactions: Transaction[];
  loadingInitial: boolean;
  loadingMore: boolean;
  error: string | null;
  page: number;
  totalPages: number;
  totalElements: number;
}

const PAGE_SIZE = 20;

interface TransactionFilterForm {
  keyword: FormControl<string>;
  accountId: FormControl<number | null>;
  category: FormControl<CategoryType | ''>;
  minDate: FormControl<string>;
  maxDate: FormControl<string>;
  minAmount: FormControl<string>;
  maxAmount: FormControl<string>;
}

@Component({
  selector: 'app-transaction-list',
  imports: [
    CurrencyPipe,
    DatePipe,
    ReactiveFormsModule,
    MatIconModule,
    MatProgressSpinnerModule,
    RouterLink,
    CategoryColorPipe,
  ],
  templateUrl: './transaction-list.component.html',
  styleUrl: './transaction-list.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TransactionListComponent {
  private readonly accountService = inject(AccountService);
  private readonly transactionService = inject(TransactionService);
  private readonly route = inject(ActivatedRoute);
  private readonly platformId = inject(PLATFORM_ID);
  private readonly destroyRef = inject(DestroyRef);
  private readonly sentinel = viewChild<ElementRef<HTMLElement>>('loadMoreSentinel');
  private observer: IntersectionObserver | null = null;

  protected readonly categories = CATEGORY_TYPES;
  protected readonly accounts = signal<Account[]>([]);
  protected readonly selectedPeriod = signal<string>('all');
  protected readonly periodTabs = [
    { id: '7d', label: '7D' },
    { id: '30d', label: '30D' },
    { id: 'month', label: 'This month' },
    { id: 'year', label: 'This year' },
    { id: 'all', label: 'All' },
  ];
  protected readonly activeFilterCount = signal(0);
  protected readonly filterForm = new FormGroup<TransactionFilterForm>({
    keyword: new FormControl('', { nonNullable: true }),
    accountId: new FormControl<number | null>(null),
    category: new FormControl<CategoryType | ''>('', { nonNullable: true }),
    minDate: new FormControl('', { nonNullable: true }),
    maxDate: new FormControl('', { nonNullable: true }),
    minAmount: new FormControl('', { nonNullable: true }),
    maxAmount: new FormControl('', { nonNullable: true }),
  });

  protected readonly state = signal<TransactionListState>({
    transactions: [],
    loadingInitial: true,
    loadingMore: false,
    error: null,
    page: -1,
    totalPages: 0,
    totalElements: 0,
  });

  protected readonly groupedTransactions = computed(() => {
    const txns = this.state().transactions;
    const groups = new Map<string, Transaction[]>();
    for (const tx of txns) {
      const key = tx.date;
      if (!groups.has(key)) groups.set(key, []);
      groups.get(key)!.push(tx);
    }
    return Array.from(groups.entries()).map(([date, items]) => ({ date, items }));
  });

  protected readonly totals = computed(() => {
    const txns = this.state().transactions;
    const totalIn = txns.filter((t) => t.value > 0).reduce((s, t) => s + t.value, 0);
    const totalOut = txns.filter((t) => t.value < 0).reduce((s, t) => s + Math.abs(t.value), 0);
    return { totalIn, totalOut, net: totalIn - totalOut };
  });

  protected readonly unreviewedCount = computed(
    () => this.state().transactions.filter((t) => !t.reviewed).length,
  );

  constructor() {
    this.filterForm.controls.keyword.valueChanges
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe(() => {
        void this.reloadTransactions();
      });

    const accountIdParam = this.route.snapshot.queryParamMap.get('accountId');
    if (accountIdParam) {
      this.filterForm.patchValue({ accountId: Number(accountIdParam) }, { emitEvent: false });
    }

    void this.loadAccounts();
    void this.reloadTransactions();

    afterNextRender(() => {
      this.observeSentinel();
    });

    this.destroyRef.onDestroy(() => {
      this.observer?.disconnect();
    });
  }

  protected async reloadTransactions(): Promise<void> {
    this.activeFilterCount.set(this.countActiveFilters());
    this.state.set({
      transactions: [],
      loadingInitial: true,
      loadingMore: false,
      error: null,
      page: -1,
      totalPages: 0,
      totalElements: 0,
    });

    await this.loadPage(0);
  }

  protected async loadNextPage(): Promise<void> {
    const state = this.state();
    if (state.loadingInitial || state.loadingMore || state.page + 1 >= state.totalPages) {
      return;
    }

    await this.loadPage(state.page + 1);
  }

  protected categoryLabel(category: string): string {
    return category
      .toLowerCase()
      .split('_')
      .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
      .join(' ');
  }

  protected categoryIcon(category: string): string {
    const map: Record<string, string> = {
      GROCERIES: 'local_grocery_store',
      INCOME: 'payments',
      SAVINGS: 'savings',
      DINING: 'restaurant',
      SHOPPING: 'shopping_bag',
      SUBSCRIPTION: 'subscriptions',
      TRANSPORT: 'directions_bus',
      TRAVEL: 'flight',
      TRANSFER: 'sync_alt',
      UTILITIES: 'bolt',
      RENT: 'home',
      HEALTH: 'local_hospital',
      ENTERTAINMENT: 'movie',
      EDUCATION: 'school',
      OTHER: 'more_horiz',
    };
    return map[category.toUpperCase()] ?? 'receipt';
  }

  protected categoryIconBg(category: string): string {
    const cat = category.toUpperCase();
    if (['GROCERIES', 'INCOME', 'SAVINGS'].includes(cat)) return 'rgba(44,173,106,0.14)';
    if (['DINING', 'SHOPPING', 'SUBSCRIPTION'].includes(cat)) return 'rgba(124,58,237,0.12)';
    if (['TRANSPORT', 'TRAVEL', 'TRANSFER'].includes(cat)) return 'rgba(91,95,239,0.12)';
    if (['UTILITIES', 'RENT', 'HEALTH', 'ENTERTAINMENT', 'EDUCATION'].includes(cat))
      return 'rgba(217,152,56,0.14)';
    return 'rgba(147,150,168,0.14)';
  }

  protected categoryIconColor(category: string): string {
    const cat = category.toUpperCase();
    if (['GROCERIES', 'INCOME', 'SAVINGS'].includes(cat)) return '#2cad6a';
    if (['DINING', 'SHOPPING', 'SUBSCRIPTION'].includes(cat)) return '#7c3aed';
    if (['TRANSPORT', 'TRAVEL', 'TRANSFER'].includes(cat)) return '#5b5fef';
    if (['UTILITIES', 'RENT', 'HEALTH', 'ENTERTAINMENT', 'EDUCATION'].includes(cat))
      return '#d99838';
    return '#9396a8';
  }

  protected prettyDate(dateStr: string): string {
    const now = new Date();
    now.setHours(0, 0, 0, 0);
    const d = new Date(dateStr);
    d.setHours(0, 0, 0, 0);
    const diffDays = Math.round((now.getTime() - d.getTime()) / 86400000);
    if (diffDays === 0) return 'Today';
    if (diffDays === 1) return 'Yesterday';
    return d.toLocaleDateString('en-GB', { day: 'numeric', month: 'short' });
  }

  protected setPeriod(period: string): void {
    this.selectedPeriod.set(period);
    const now = new Date();
    const today = now.toISOString().split('T')[0];
    let minDate = '';
    let maxDate = today;

    if (period === '7d') {
      const d = new Date(now);
      d.setDate(d.getDate() - 7);
      minDate = d.toISOString().split('T')[0];
    } else if (period === '30d') {
      const d = new Date(now);
      d.setDate(d.getDate() - 30);
      minDate = d.toISOString().split('T')[0];
    } else if (period === 'month') {
      minDate = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-01`;
    } else if (period === 'year') {
      minDate = `${now.getFullYear()}-01-01`;
    } else {
      maxDate = '';
    }

    this.filterForm.patchValue({ minDate, maxDate }, { emitEvent: false });
    void this.reloadTransactions();
  }

  protected async applyFilters(): Promise<void> {
    await this.reloadTransactions();
  }

  protected async clearFilters(): Promise<void> {
    this.selectedPeriod.set('all');
    this.filterForm.reset(
      {
        keyword: '',
        accountId: null,
        category: '',
        minDate: '',
        maxDate: '',
        minAmount: '',
        maxAmount: '',
      },
      { emitEvent: false },
    );
    await this.reloadTransactions();
  }

  private async loadPage(page: number): Promise<void> {
    this.state.update((current) => ({
      ...current,
      loadingInitial: page === 0 && current.transactions.length === 0,
      loadingMore: page > 0,
      error: null,
    }));

    try {
      const result = await firstValueFrom(
        this.transactionService.getTransactions({
          ...this.currentQuery(),
          page,
          size: PAGE_SIZE,
        }),
      );
      this.state.update((current) => ({
        transactions: page === 0 ? result.content : [...current.transactions, ...result.content],
        loadingInitial: false,
        loadingMore: false,
        error: null,
        page: result.number,
        totalPages: result.totalPages,
        totalElements: result.totalElements,
      }));
    } catch {
      this.state.update((current) => ({
        ...current,
        loadingInitial: false,
        loadingMore: false,
        error: 'Unable to load transactions.',
      }));
    }
  }

  private observeSentinel(): void {
    if (!isPlatformBrowser(this.platformId)) {
      return;
    }

    const sentinel = this.sentinel()?.nativeElement;
    if (!sentinel) {
      return;
    }

    this.observer = new IntersectionObserver((entries) => {
      if (entries.some((entry) => entry.isIntersecting)) {
        void this.loadNextPage();
      }
    });
    this.observer.observe(sentinel);
  }

  private async loadAccounts(): Promise<void> {
    try {
      this.accounts.set(await firstValueFrom(this.accountService.getAccounts()));
    } catch {
      this.accounts.set([]);
    }
  }

  private currentQuery(): TransactionQuery {
    const value = this.filterForm.getRawValue();

    return {
      accountId: value.accountId,
      category: value.category || null,
      minDate: value.minDate || null,
      maxDate: value.maxDate || null,
      minAmount: value.minAmount || null,
      maxAmount: value.maxAmount || null,
      keyword: value.keyword.trim() || null,
    };
  }

  private countActiveFilters(): number {
    const value = this.filterForm.getRawValue();
    return [
      value.accountId,
      value.category,
      value.minDate,
      value.maxDate,
      value.minAmount,
      value.maxAmount,
      value.keyword.trim(),
    ].filter(Boolean).length;
  }
}

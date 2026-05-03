import { CurrencyPipe, DatePipe, isPlatformBrowser } from '@angular/common';
import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  inject,
  PLATFORM_ID,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged, firstValueFrom } from 'rxjs';

import type { Account } from '../../../shared/models/account.model';
import { CATEGORY_TYPES, type CategoryType } from '../../../shared/models/category.model';
import { CategoryColorPipe } from '../../../shared/pipes/category-color.pipe';
import type { Transaction } from '../../../shared/models/transaction.model';
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
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
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
  private readonly platformId = inject(PLATFORM_ID);
  private readonly destroyRef = inject(DestroyRef);
  private readonly sentinel = viewChild<ElementRef<HTMLElement>>('loadMoreSentinel');
  private observer: IntersectionObserver | null = null;

  protected readonly categories = CATEGORY_TYPES;
  protected readonly accounts = signal<Account[]>([]);
  protected readonly filtersOpen = signal(false);
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

  constructor() {
    this.filterForm.controls.keyword.valueChanges
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe(() => {
        void this.reloadTransactions();
      });

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

  protected toggleFilters(): void {
    this.filtersOpen.update((open) => !open);
  }

  protected async applyFilters(): Promise<void> {
    await this.reloadTransactions();
  }

  protected async clearFilters(): Promise<void> {
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

import { CurrencyPipe, DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ActivatedRoute } from '@angular/router';
import { debounceTime, distinctUntilChanged, firstValueFrom } from 'rxjs';

import type { Account } from '../../../shared/models/account.model';
import { CATEGORY_TYPES, type CategoryType } from '../../../shared/models/category.model';
import type { Transaction } from '../../../shared/models/transaction.model';
import { PageActionsComponent } from '../../../shared/components/page-actions/page-actions.component';
import { CategoryColorPipe } from '../../../shared/pipes/category-color.pipe';
import { AccountService } from '../../accounts/account.service';
import { TransactionService, type TransactionQuery } from '../transaction.service';
import { UnreviewedTransactionCountService } from '../unreviewed-transaction-count.service';

interface TransactionListState {
  transactions: Transaction[];
  loadingInitial: boolean;
  error: string | null;
  page: number;
  totalPages: number;
  totalElements: number;
}

interface TransactionDetailModalState {
  transaction: Transaction | null;
  loading: boolean;
  saving: boolean;
  error: string | null;
}

const PAGE_SIZE = 20;
const REVIEW_ALL_PAGE_SIZE = 100;
const PAGE_SIZE_OPTIONS = [20, 50, 100] as const;

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
    PageActionsComponent,
    CategoryColorPipe,
  ],
  templateUrl: './transaction-list.component.html',
  styleUrl: './transaction-list.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TransactionListComponent {
  private readonly accountService = inject(AccountService);
  private readonly transactionService = inject(TransactionService);
  private readonly unreviewedTransactionCountService = inject(UnreviewedTransactionCountService);
  private readonly route = inject(ActivatedRoute);

  protected readonly categories = CATEGORY_TYPES;
  protected readonly accounts = signal<Account[]>([]);
  protected readonly pageSizeOptions = PAGE_SIZE_OPTIONS;
  protected readonly pageSize = signal<number>(PAGE_SIZE);
  protected readonly selectedPeriod = signal<string>('all');
  protected readonly periodTabs = [
    { id: '7d', label: '7D' },
    { id: '30d', label: '30D' },
    { id: 'month', label: 'This month' },
    { id: 'year', label: 'This year' },
    { id: 'all', label: 'All' },
  ];
  protected readonly activeFilterCount = signal(0);
  protected readonly reviewingAll = signal(false);
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
    error: null,
    page: -1,
    totalPages: 0,
    totalElements: 0,
  });
  protected readonly detailState = signal<TransactionDetailModalState>({
    transaction: null,
    loading: false,
    saving: false,
    error: null,
  });
  protected readonly categoryMenuOpen = signal(false);

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
  protected readonly visiblePages = computed(() => {
    const { page, totalPages } = this.state();
    if (totalPages <= 1) return [];

    const start = Math.max(0, Math.min(page - 2, totalPages - 5));
    const end = Math.min(totalPages, start + 5);
    return Array.from({ length: end - start }, (_, index) => start + index);
  });

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
  }

  protected async reloadTransactions(): Promise<void> {
    this.activeFilterCount.set(this.countActiveFilters());
    this.state.set({
      transactions: [],
      loadingInitial: true,
      error: null,
      page: -1,
      totalPages: 0,
      totalElements: 0,
    });

    await this.loadPage(0);
  }

  protected async goToPage(page: number): Promise<void> {
    const state = this.state();
    if (state.loadingInitial || page < 0 || page >= state.totalPages || page === state.page) {
      return;
    }

    await this.loadPage(page);
  }

  protected async goToPreviousPage(): Promise<void> {
    await this.goToPage(this.state().page - 1);
  }

  protected async goToNextPage(): Promise<void> {
    await this.goToPage(this.state().page + 1);
  }

  protected async changePageSize(event: Event): Promise<void> {
    const size = Number((event.target as HTMLSelectElement).value);
    if (size === this.pageSize()) return;

    this.pageSize.set(size);
    await this.reloadTransactions();
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

  protected async openTransaction(transaction: Transaction): Promise<void> {
    this.categoryMenuOpen.set(false);
    this.detailState.set({
      transaction,
      loading: true,
      saving: false,
      error: null,
    });

    try {
      const detail = await firstValueFrom(this.transactionService.getTransaction(transaction.id));
      this.detailState.set({
        transaction: detail,
        loading: false,
        saving: false,
        error: null,
      });
    } catch {
      this.detailState.set({
        transaction,
        loading: false,
        saving: false,
        error: 'Unable to load full transaction details.',
      });
    }
  }

  protected openTransactionFromKeyboard(event: Event, transaction: Transaction): void {
    event.preventDefault();
    void this.openTransaction(transaction);
  }

  protected closeTransaction(): void {
    this.categoryMenuOpen.set(false);
    this.detailState.set({
      transaction: null,
      loading: false,
      saving: false,
      error: null,
    });
  }

  protected async toggleRowReviewed(event: MouseEvent, transaction: Transaction): Promise<void> {
    event.stopPropagation();
    this.categoryMenuOpen.set(false);

    try {
      const updated = await firstValueFrom(
        this.transactionService.updateReviewed(transaction.id, !transaction.reviewed),
      );
      this.replaceTransaction(updated);
      this.unreviewedTransactionCountService.refresh();

      const currentDetail = this.detailState().transaction;
      if (currentDetail?.id === updated.id) {
        this.detailState.set({
          transaction: updated,
          loading: false,
          saving: false,
          error: null,
        });
      }
    } catch {
      this.detailState.update((current) => ({
        ...current,
        error: 'Unable to update reviewed state.',
      }));
    }
  }

  protected async markAllMatchingTransactionsReviewed(): Promise<void> {
    if (this.unreviewedCount() === 0 || this.reviewingAll()) return;

    this.reviewingAll.set(true);
    this.state.update((current) => ({ ...current, error: null }));

    try {
      const unreviewedTransactions = await this.loadAllMatchingUnreviewedTransactions();
      const updatedTransactions = await Promise.all(
        unreviewedTransactions.map((transaction) =>
          firstValueFrom(this.transactionService.updateReviewed(transaction.id, true)),
        ),
      );
      const updatedById = new Map(
        updatedTransactions.map((transaction) => [transaction.id, transaction]),
      );
      this.state.update((current) => ({
        ...current,
        transactions: current.transactions.map(
          (transaction) => updatedById.get(transaction.id) ?? transaction,
        ),
      }));

      const currentDetail = this.detailState().transaction;
      if (currentDetail) {
        const updatedDetail = updatedById.get(currentDetail.id);
        if (updatedDetail) {
          this.detailState.set({
            transaction: updatedDetail,
            loading: false,
            saving: false,
            error: null,
          });
        }
      }

      this.unreviewedTransactionCountService.refresh();
    } catch {
      this.state.update((current) => ({
        ...current,
        error: 'Unable to mark all loaded transactions as reviewed.',
      }));
    } finally {
      this.reviewingAll.set(false);
    }
  }

  private async loadAllMatchingUnreviewedTransactions(): Promise<Transaction[]> {
    const query = this.currentQuery();
    const firstPage = await firstValueFrom(
      this.transactionService.getTransactions({
        ...query,
        page: 0,
        size: REVIEW_ALL_PAGE_SIZE,
      }),
    );
    const additionalPages = await Promise.all(
      Array.from({ length: Math.max(firstPage.totalPages - 1, 0) }, (_, index) =>
        firstValueFrom(
          this.transactionService.getTransactions({
            ...query,
            page: index + 1,
            size: REVIEW_ALL_PAGE_SIZE,
          }),
        ),
      ),
    );
    return [firstPage, ...additionalPages]
      .flatMap((page) => page.content)
      .filter((transaction) => !transaction.reviewed);
  }

  protected toggleCategoryMenu(): void {
    if (this.detailState().saving) return;
    this.categoryMenuOpen.update((open) => !open);
  }

  protected async toggleDetailReviewed(): Promise<void> {
    const transaction = this.detailState().transaction;
    if (!transaction) return;

    this.detailState.update((current) => ({ ...current, saving: true, error: null }));

    try {
      const updated = await firstValueFrom(
        this.transactionService.updateReviewed(transaction.id, !transaction.reviewed),
      );
      this.replaceTransaction(updated);
      this.unreviewedTransactionCountService.refresh();
      this.detailState.set({
        transaction: updated,
        loading: false,
        saving: false,
        error: null,
      });
    } catch {
      this.detailState.update((current) => ({
        ...current,
        saving: false,
        error: 'Unable to update reviewed state.',
      }));
    }
  }

  protected async updateDetailCategory(selectedCategory: CategoryType): Promise<void> {
    const transaction = this.detailState().transaction;
    if (!transaction || selectedCategory === transaction.category) {
      this.categoryMenuOpen.set(false);
      return;
    }

    this.categoryMenuOpen.set(false);
    this.detailState.update((current) => ({ ...current, saving: true, error: null }));

    try {
      const updated = await firstValueFrom(
        this.transactionService.updateCategory(transaction.id, selectedCategory),
      );
      this.replaceTransaction(updated);
      this.detailState.set({
        transaction: updated,
        loading: false,
        saving: false,
        error: null,
      });
    } catch {
      this.detailState.update((current) => ({
        ...current,
        saving: false,
        error: 'Unable to update category.',
      }));
    }
  }

  protected async toggleDetailInternalTransfer(): Promise<void> {
    const transaction = this.detailState().transaction;
    if (!transaction) return;

    this.detailState.update((current) => ({ ...current, saving: true, error: null }));

    try {
      const updated = await firstValueFrom(
        this.transactionService.updateInternalTransfer(
          transaction.id,
          !transaction.internalTransfer,
        ),
      );
      this.replaceTransaction(updated);
      this.detailState.set({
        transaction: updated,
        loading: false,
        saving: false,
        error: null,
      });
    } catch {
      this.detailState.update((current) => ({
        ...current,
        saving: false,
        error: 'Unable to update internal transfer state.',
      }));
    }
  }

  protected transactionReference(transaction: Transaction): string {
    const year = new Date(transaction.date).getFullYear();
    return `NX-${String(transaction.id).padStart(6, '0')}-${year}`;
  }

  protected transactionMethod(transaction: Transaction): string {
    const type = transaction.type?.toLowerCase() ?? '';
    if (type.includes('card')) return 'Card';
    if (type.includes('transfer')) return 'Bank transfer';
    if (type.includes('debit')) return 'SEPA Direct Debit';
    return transaction.type ? this.categoryLabel(transaction.type) : 'Bank transaction';
  }

  protected transactionDirection(transaction: Transaction): string {
    return transaction.value >= 0 ? 'Credit (incoming)' : 'Debit (outgoing)';
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
      error: null,
    }));

    try {
      const result = await firstValueFrom(
        this.transactionService.getTransactions({
          ...this.currentQuery(),
          page,
          size: this.pageSize(),
        }),
      );
      this.state.update((current) => ({
        ...current,
        transactions: result.content,
        loadingInitial: false,
        error: null,
        page: result.number,
        totalPages: result.totalPages,
        totalElements: result.totalElements,
      }));
    } catch {
      this.state.update((current) => ({
        ...current,
        loadingInitial: false,
        error: 'Unable to load transactions.',
      }));
    }
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

  private replaceTransaction(updated: Transaction): void {
    this.state.update((current) => ({
      ...current,
      transactions: current.transactions.map((transaction) =>
        transaction.id === updated.id ? updated : transaction,
      ),
    }));
  }
}

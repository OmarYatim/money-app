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
  sourceTransactions: Transaction[];
  filteredTransactions: Transaction[];
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
const CLIENT_FETCH_PAGE_SIZE = 100;
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

type FilterMenu = 'account' | 'category' | 'status' | 'transfer' | 'group' | 'sort' | 'rows';
type StatusFilter = 'all' | 'reviewed' | 'unreviewed';
type TransferFilter = 'all' | 'internal' | 'regular';
type GroupByFilter = 'date' | 'bank' | 'category';
type SortFilter = 'newest' | 'oldest' | 'largest' | 'smallest';

interface Option<T extends string> {
  value: T;
  label: string;
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
  protected readonly statusFilter = signal<StatusFilter>('all');
  protected readonly transferFilter = signal<TransferFilter>('all');
  protected readonly groupBy = signal<GroupByFilter>('date');
  protected readonly sortBy = signal<SortFilter>('newest');
  protected readonly statusOptions: Option<StatusFilter>[] = [
    { value: 'all', label: 'All' },
    { value: 'reviewed', label: 'Reviewed' },
    { value: 'unreviewed', label: 'Unreviewed' },
  ];
  protected readonly groupOptions: Option<GroupByFilter>[] = [
    { value: 'date', label: 'Date' },
    { value: 'bank', label: 'Bank' },
    { value: 'category', label: 'Category' },
  ];
  protected readonly sortOptions: Option<SortFilter>[] = [
    { value: 'newest', label: 'Newest' },
    { value: 'oldest', label: 'Oldest' },
    { value: 'largest', label: 'Largest amount' },
    { value: 'smallest', label: 'Smallest amount' },
  ];
  protected readonly transferOptions: Option<TransferFilter>[] = [
    { value: 'all', label: 'All' },
    { value: 'internal', label: 'Internal only' },
    { value: 'regular', label: 'Exclude internal' },
  ];
  protected readonly selectedPeriod = signal<string>('all');
  protected readonly periodTabs = [
    { id: '7d', label: '7D' },
    { id: '30d', label: '30D' },
    { id: 'month', label: 'This month' },
    { id: 'year', label: 'This year' },
    { id: 'future', label: 'Future' },
    { id: 'all', label: 'All' },
    { id: 'custom', label: 'Custom' },
  ];
  protected readonly customDateOpen = signal(false);
  protected readonly customMinDate = signal('');
  protected readonly customMaxDate = signal('');
  protected readonly customDateLabel = computed(() => {
    const min = this.customMinDate();
    const max = this.customMaxDate();
    if (!min && !max) return null;
    const fmt = (d: string) =>
      new Date(d).toLocaleDateString('en-GB', { day: 'numeric', month: 'short' });
    if (min && max) return `${fmt(min)} – ${fmt(max)}`;
    if (min) return `From ${fmt(min)}`;
    return `To ${fmt(max)}`;
  });
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
    sourceTransactions: [],
    filteredTransactions: [],
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
  protected readonly filterMenuOpen = signal<FilterMenu | null>(null);
  protected readonly categoryMenuOpen = signal(false);

  protected readonly groupedTransactions = computed(() => {
    const txns = this.state().transactions;
    const groups = new Map<string, Transaction[]>();
    for (const tx of txns) {
      const key = this.groupKey(tx);
      if (!groups.has(key)) groups.set(key, []);
      groups.get(key)!.push(tx);
    }
    return Array.from(groups.entries()).map(([key, items]) => ({
      key,
      label: this.groupLabel(key),
      items,
    }));
  });

  protected readonly totals = computed(() => {
    const txns = this.state().filteredTransactions.filter((transaction) => !transaction.internalTransfer);
    const totalIn = txns.filter((t) => t.value > 0).reduce((s, t) => s + t.value, 0);
    const totalOut = txns.filter((t) => t.value < 0).reduce((s, t) => s + Math.abs(t.value), 0);
    return { totalIn, totalOut, net: totalIn - totalOut };
  });

  protected readonly unreviewedCount = computed(
    () => this.state().filteredTransactions.filter((t) => !t.reviewed).length,
  );
  protected readonly visiblePages = computed(() => {
    const { page, totalPages } = this.state();
    if (totalPages <= 1) return [];

    const start = Math.max(0, Math.min(page - 2, totalPages - 5));
    const end = Math.min(totalPages, start + 5);
    return Array.from({ length: end - start }, (_, index) => start + index);
  });
  protected selectedAccountLabel(): string {
    const selectedAccountId = this.filterForm.controls.accountId.value;
    if (selectedAccountId === null) return 'All accounts';

    const account = this.accounts().find((item) => item.id === selectedAccountId);
    return account ? this.accountLabel(account) : 'All accounts';
  }

  protected selectedCategoryLabel(): string {
    const category = this.filterForm.controls.category.value;
    return category ? this.categoryLabel(category) : 'All categories';
  }

  protected selectedStatusLabel(): string {
    return this.optionLabel(this.statusOptions, this.statusFilter());
  }

  protected selectedTransferLabel(): string {
    return this.optionLabel(this.transferOptions, this.transferFilter());
  }

  protected selectedGroupLabel(): string {
    return this.optionLabel(this.groupOptions, this.groupBy());
  }

  protected selectedSortLabel(): string {
    return this.optionLabel(this.sortOptions, this.sortBy());
  }

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
      sourceTransactions: [],
      filteredTransactions: [],
      transactions: [],
      loadingInitial: true,
      error: null,
      page: -1,
      totalPages: 0,
      totalElements: 0,
    });

    await this.loadTransactions();
  }

  protected async goToPage(page: number): Promise<void> {
    const state = this.state();
    if (state.loadingInitial || page < 0 || page >= state.totalPages || page === state.page) {
      return;
    }

    this.applyClientView(page);
  }

  protected async goToPreviousPage(): Promise<void> {
    await this.goToPage(this.state().page - 1);
  }

  protected async goToNextPage(): Promise<void> {
    await this.goToPage(this.state().page + 1);
  }

  protected toggleFilterMenu(menu: FilterMenu): void {
    this.customDateOpen.set(false);
    this.filterMenuOpen.update((openMenu) => (openMenu === menu ? null : menu));
  }

  protected async selectAccount(accountId: number | null): Promise<void> {
    this.filterMenuOpen.set(null);
    this.filterForm.patchValue({ accountId }, { emitEvent: false });
    await this.applyFilters();
  }

  protected async selectCategory(category: CategoryType | ''): Promise<void> {
    this.filterMenuOpen.set(null);
    this.filterForm.patchValue({ category }, { emitEvent: false });
    await this.applyFilters();
  }

  protected async selectPageSize(size: number): Promise<void> {
    this.filterMenuOpen.set(null);
    if (size === this.pageSize()) return;

    this.pageSize.set(size);
    this.applyClientView(0);
  }

  protected async selectStatus(status: StatusFilter): Promise<void> {
    this.filterMenuOpen.set(null);
    this.statusFilter.set(status);
    this.activeFilterCount.set(this.countActiveFilters());
    this.applyClientView(0);
  }

  protected async selectTransfer(transfer: TransferFilter): Promise<void> {
    this.filterMenuOpen.set(null);
    this.transferFilter.set(transfer);
    this.activeFilterCount.set(this.countActiveFilters());
    this.applyClientView(0);
  }

  protected selectGroupBy(group: GroupByFilter): void {
    this.filterMenuOpen.set(null);
    this.groupBy.set(group);
  }

  protected selectSort(sort: SortFilter): void {
    this.filterMenuOpen.set(null);
    this.sortBy.set(sort);
    this.applyClientView(0);
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
    if (period === 'custom') {
      this.selectedPeriod.set('custom');
      this.customMinDate.set(this.filterForm.controls.minDate.value);
      this.customMaxDate.set(this.filterForm.controls.maxDate.value);
      this.customDateOpen.set(true);
      return;
    }

    this.customDateOpen.set(false);
    this.selectedPeriod.set(period);
    const now = new Date();
    const today = this.isoDate(now);
    let minDate = '';
    let maxDate = today;

    if (period === '7d') {
      const d = new Date(now);
      d.setDate(d.getDate() - 7);
      minDate = this.isoDate(d);
    } else if (period === '30d') {
      const d = new Date(now);
      d.setDate(d.getDate() - 30);
      minDate = this.isoDate(d);
    } else if (period === 'month') {
      minDate = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-01`;
    } else if (period === 'year') {
      minDate = `${now.getFullYear()}-01-01`;
    } else if (period === 'future') {
      minDate = today;
      maxDate = '';
    } else {
      maxDate = '';
    }

    this.filterForm.patchValue({ minDate, maxDate }, { emitEvent: false });
    void this.reloadTransactions();
  }

  protected applyCustomDate(): void {
    this.filterForm.patchValue(
      { minDate: this.customMinDate(), maxDate: this.customMaxDate() },
      { emitEvent: false },
    );
    this.customDateOpen.set(false);
    void this.reloadTransactions();
  }

  protected cancelCustomDate(): void {
    this.customDateOpen.set(false);
    if (!this.filterForm.controls.minDate.value && !this.filterForm.controls.maxDate.value) {
      this.selectedPeriod.set('all');
    }
  }

  protected setCustomMinDate(event: Event): void {
    this.customMinDate.set((event.target as HTMLInputElement).value);
  }

  protected setCustomMaxDate(event: Event): void {
    this.customMaxDate.set((event.target as HTMLInputElement).value);
  }

  protected async applyFilters(): Promise<void> {
    this.filterMenuOpen.set(null);
    await this.reloadTransactions();
  }

  protected async openTransaction(transaction: Transaction): Promise<void> {
    this.filterMenuOpen.set(null);
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
    this.filterMenuOpen.set(null);
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
        sourceTransactions: current.sourceTransactions.map(
          (transaction) => updatedById.get(transaction.id) ?? transaction,
        ),
      }));
      this.applyClientView(this.state().page);

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
    this.filterMenuOpen.set(null);
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
    this.filterMenuOpen.set(null);
    this.customDateOpen.set(false);
    this.customMinDate.set('');
    this.customMaxDate.set('');
    this.selectedPeriod.set('all');
    this.statusFilter.set('all');
    this.transferFilter.set('all');
    this.groupBy.set('date');
    this.sortBy.set('newest');
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

  private async loadTransactions(): Promise<void> {
    this.state.update((current) => ({
      ...current,
      loadingInitial: true,
      error: null,
    }));

    try {
      const query = this.currentQuery();
      const firstPage = await firstValueFrom(
        this.transactionService.getTransactions({
          ...query,
          page: 0,
          size: CLIENT_FETCH_PAGE_SIZE,
        }),
      );
      const additionalPages = await Promise.all(
        Array.from({ length: Math.max(firstPage.totalPages - 1, 0) }, (_, index) =>
          firstValueFrom(
            this.transactionService.getTransactions({
              ...query,
              page: index + 1,
              size: CLIENT_FETCH_PAGE_SIZE,
            }),
          ),
        ),
      );
      const sourceTransactions = [firstPage, ...additionalPages].flatMap((page) => page.content);
      this.state.update((current) => ({
        ...current,
        sourceTransactions,
        loadingInitial: false,
        error: null,
      }));
      this.applyClientView(0);
    } catch {
      this.state.update((current) => ({
        ...current,
        loadingInitial: false,
        error: 'Unable to load transactions.',
      }));
    }
  }

  private applyClientView(page: number): void {
    const filteredTransactions = this.sortTransactions(
      this.filterByTransfer(this.filterByStatus(this.state().sourceTransactions)),
    );
    const totalPages = Math.ceil(filteredTransactions.length / this.pageSize());
    const safePage = totalPages === 0 ? 0 : Math.min(Math.max(page, 0), totalPages - 1);
    const start = safePage * this.pageSize();

    this.state.update((current) => ({
      ...current,
      filteredTransactions,
      transactions: filteredTransactions.slice(start, start + this.pageSize()),
      page: safePage,
      totalPages,
      totalElements: filteredTransactions.length,
    }));
  }

  private filterByStatus(transactions: Transaction[]): Transaction[] {
    const status = this.statusFilter();
    if (status === 'reviewed') return transactions.filter((transaction) => transaction.reviewed);
    if (status === 'unreviewed') return transactions.filter((transaction) => !transaction.reviewed);
    return transactions;
  }

  private filterByTransfer(transactions: Transaction[]): Transaction[] {
    const transfer = this.transferFilter();
    if (transfer === 'internal') {
      return transactions.filter((transaction) => transaction.internalTransfer);
    }
    if (transfer === 'regular') {
      return transactions.filter((transaction) => !transaction.internalTransfer);
    }
    return transactions;
  }

  private sortTransactions(transactions: Transaction[]): Transaction[] {
    return [...transactions].sort((left, right) => {
      if (this.sortBy() === 'oldest') return this.transactionTime(left) - this.transactionTime(right);
      if (this.sortBy() === 'largest') return Math.abs(right.value) - Math.abs(left.value);
      if (this.sortBy() === 'smallest') return Math.abs(left.value) - Math.abs(right.value);
      return this.transactionTime(right) - this.transactionTime(left);
    });
  }

  private async loadAccounts(): Promise<void> {
    try {
      this.accounts.set(await firstValueFrom(this.accountService.getAccounts()));
    } catch {
      this.accounts.set([]);
    }
  }

  private accountLabel(account: Account): string {
    return account.institutionName ?? account.name;
  }

  private groupKey(transaction: Transaction): string {
    if (this.groupBy() === 'bank') return transaction.accountName ?? 'Connected account';
    if (this.groupBy() === 'category') return transaction.category;
    return transaction.date;
  }

  private groupLabel(key: string): string {
    if (this.groupBy() === 'category') return this.categoryLabel(key);
    if (this.groupBy() === 'date') return this.prettyDate(key);
    return key;
  }

  private optionLabel<T extends string>(options: Option<T>[], value: T): string {
    return options.find((option) => option.value === value)?.label ?? value;
  }

  private transactionTime(transaction: Transaction): number {
    return new Date(transaction.date).getTime();
  }

  private isoDate(date: Date): string {
    return [
      date.getFullYear(),
      String(date.getMonth() + 1).padStart(2, '0'),
      String(date.getDate()).padStart(2, '0'),
    ].join('-');
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
      this.statusFilter() === 'all' ? '' : this.statusFilter(),
      this.transferFilter() === 'all' ? '' : this.transferFilter(),
    ].filter(Boolean).length;
  }

  private replaceTransaction(updated: Transaction): void {
    this.state.update((current) => ({
      ...current,
      sourceTransactions: current.sourceTransactions.map((transaction) =>
        transaction.id === updated.id ? updated : transaction,
      ),
    }));
    this.applyClientView(this.state().page);
  }
}

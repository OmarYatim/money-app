import { CurrencyPipe } from '@angular/common';
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
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { debounceTime, distinctUntilChanged, firstValueFrom } from 'rxjs';

import type { Account } from '../../../shared/models/account.model';
import { CATEGORY_TYPES, type CategoryType } from '../../../shared/models/category.model';
import type { Transaction } from '../../../shared/models/transaction.model';
import type { TransactionSummary } from '../../../shared/models/transaction-summary.model';
import { PageActionsComponent } from '../../../shared/components/page-actions/page-actions.component';
import { LanguageService } from '../../../core/i18n/language.service';
import { AccountService } from '../../accounts/account.service';
import { TransactionService, type TransactionQuery } from '../transaction.service';
import { UnreviewedTransactionCountService } from '../unreviewed-transaction-count.service';
import { TransactionModalComponent } from '../transaction-modal/transaction-modal.component';
import { TransactionRowComponent } from '../transaction-row/transaction-row.component';

interface TransactionListState {
  transactions: Transaction[];
  summary: TransactionSummary;
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
const EMPTY_TRANSACTION_SUMMARY: TransactionSummary = {
  totalElements: 0,
  unreviewedCount: 0,
  totalIn: 0,
  totalOut: 0,
  net: 0,
};

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
  labelKey: string;
}

@Component({
  selector: 'app-transaction-list',
  imports: [
    CurrencyPipe,
    ReactiveFormsModule,
    MatIconModule,
    MatProgressSpinnerModule,
    TranslatePipe,
    PageActionsComponent,
    TransactionModalComponent,
    TransactionRowComponent,
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
  private readonly languageService = inject(LanguageService);
  private readonly translate = inject(TranslateService);

  protected readonly categories = CATEGORY_TYPES;
  protected readonly accounts = signal<Account[]>([]);
  protected readonly pageSizeOptions = PAGE_SIZE_OPTIONS;
  protected readonly pageSize = signal<number>(PAGE_SIZE);
  protected readonly statusFilter = signal<StatusFilter>('all');
  protected readonly transferFilter = signal<TransferFilter>('all');
  protected readonly groupBy = signal<GroupByFilter>('date');
  protected readonly sortBy = signal<SortFilter>('newest');
  protected readonly statusOptions: Option<StatusFilter>[] = [
    { value: 'all', labelKey: 'transactions.filters.all' },
    { value: 'reviewed', labelKey: 'transactions.review.reviewed' },
    { value: 'unreviewed', labelKey: 'transactions.review.unreviewed' },
  ];
  protected readonly groupOptions: Option<GroupByFilter>[] = [
    { value: 'date', labelKey: 'transactions.filters.date' },
    { value: 'bank', labelKey: 'transactions.filters.bank' },
    { value: 'category', labelKey: 'transactions.filters.category' },
  ];
  protected readonly sortOptions: Option<SortFilter>[] = [
    { value: 'newest', labelKey: 'transactions.sort.newest' },
    { value: 'oldest', labelKey: 'transactions.sort.oldest' },
    { value: 'largest', labelKey: 'transactions.sort.largest' },
    { value: 'smallest', labelKey: 'transactions.sort.smallest' },
  ];
  protected readonly transferOptions: Option<TransferFilter>[] = [
    { value: 'all', labelKey: 'transactions.filters.all' },
    { value: 'internal', labelKey: 'transactions.transfer.internalOnly' },
    { value: 'regular', labelKey: 'transactions.transfer.excludeInternal' },
  ];
  protected readonly selectedPeriod = signal<string>('all');
  protected readonly periodTabs = [
    { id: '7d', labelKey: 'transactions.period.7d' },
    { id: '30d', labelKey: 'transactions.period.30d' },
    { id: 'month', labelKey: 'transactions.period.month' },
    { id: 'year', labelKey: 'transactions.period.year' },
    { id: 'future', labelKey: 'transactions.period.future' },
    { id: 'all', labelKey: 'transactions.filters.all' },
    { id: 'custom', labelKey: 'transactions.period.custom' },
  ];
  protected readonly customDateOpen = signal(false);
  protected readonly customMinDate = signal('');
  protected readonly customMaxDate = signal('');
  protected readonly customDateLabel = computed(() => {
    const min = this.customMinDate();
    const max = this.customMaxDate();
    if (!min && !max) return null;
    const fmt = (d: string) =>
      new Date(d).toLocaleDateString(this.dateLocale(), { day: 'numeric', month: 'short' });
    if (min && max) return `${fmt(min)} – ${fmt(max)}`;
    if (min) return this.t('transactions.period.fromDate', { date: fmt(min) });
    return this.t('transactions.period.toDate', { date: fmt(max) });
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
    transactions: [],
    summary: EMPTY_TRANSACTION_SUMMARY,
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
    const summary = this.state().summary;
    return { totalIn: summary.totalIn, totalOut: summary.totalOut, net: summary.net };
  });

  protected readonly unreviewedCount = computed(() => this.state().summary.unreviewedCount);
  protected readonly visiblePages = computed(() => {
    const { page, totalPages } = this.state();
    if (totalPages <= 1) return [];

    const start = Math.max(0, Math.min(page - 2, totalPages - 5));
    const end = Math.min(totalPages, start + 5);
    return Array.from({ length: end - start }, (_, index) => start + index);
  });
  protected selectedAccountLabel(): string {
    const selectedAccountId = this.filterForm.controls.accountId.value;
    if (selectedAccountId === null) return this.t('accounts.filters.allAccounts');

    const account = this.accounts().find((item) => item.id === selectedAccountId);
    return account ? this.accountLabel(account) : this.t('accounts.filters.allAccounts');
  }

  protected selectedCategoryLabel(): string {
    const category = this.filterForm.controls.category.value;
    return category ? this.categoryLabel(category) : this.t('transactions.filters.allCategories');
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

    const categoryParam = this.route.snapshot.queryParamMap.get('category');
    if (categoryParam && this.isCategoryType(categoryParam)) {
      this.filterForm.patchValue({ category: categoryParam }, { emitEvent: false });
    }

    const minDateParam = this.route.snapshot.queryParamMap.get('minDate');
    const maxDateParam = this.route.snapshot.queryParamMap.get('maxDate');
    if (minDateParam || maxDateParam) {
      this.selectedPeriod.set('custom');
      this.customMinDate.set(minDateParam ?? '');
      this.customMaxDate.set(maxDateParam ?? '');
      this.filterForm.patchValue(
        { minDate: minDateParam ?? '', maxDate: maxDateParam ?? '' },
        { emitEvent: false },
      );
    }

    const transactionIdParam = this.route.snapshot.queryParamMap.get('transactionId');
    if (transactionIdParam) {
      void this.openTransactionById(Number(transactionIdParam));
    }

    void this.loadAccounts();
    void this.reloadTransactions();

    this.transactionService.transactionsUpdated$
      .pipe(takeUntilDestroyed())
      .subscribe(() => {
        void this.reloadTransactions();
        this.unreviewedTransactionCountService.refresh();
      });
  }

  protected async reloadTransactions(): Promise<void> {
    this.activeFilterCount.set(this.countActiveFilters());
    this.state.set({
      transactions: [],
      summary: EMPTY_TRANSACTION_SUMMARY,
      loadingInitial: true,
      error: null,
      page: -1,
      totalPages: 0,
      totalElements: 0,
    });

    await this.loadTransactions(0);
  }

  protected async goToPage(page: number): Promise<void> {
    const state = this.state();
    if (state.loadingInitial || page < 0 || page >= state.totalPages || page === state.page) {
      return;
    }

    await this.loadTransactions(page);
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
    await this.reloadTransactions();
  }

  protected async selectStatus(status: StatusFilter): Promise<void> {
    this.filterMenuOpen.set(null);
    this.statusFilter.set(status);
    this.activeFilterCount.set(this.countActiveFilters());
    await this.reloadTransactions();
  }

  protected async selectTransfer(transfer: TransferFilter): Promise<void> {
    this.filterMenuOpen.set(null);
    this.transferFilter.set(transfer);
    this.activeFilterCount.set(this.countActiveFilters());
    await this.reloadTransactions();
  }

  protected selectGroupBy(group: GroupByFilter): void {
    this.filterMenuOpen.set(null);
    this.groupBy.set(group);
  }

  protected async selectSort(sort: SortFilter): Promise<void> {
    this.filterMenuOpen.set(null);
    this.sortBy.set(sort);
    await this.reloadTransactions();
  }

  protected categoryLabel(category: string): string {
    this.languageService.currentLang();
    return this.t(`categories.${category.toLowerCase()}`);
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
    this.languageService.currentLang();
    if (diffDays === 0) return this.t('time.today');
    if (diffDays === 1) return this.t('time.yesterday');
    return d.toLocaleDateString(this.dateLocale(), { day: 'numeric', month: 'short' });
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
        error: this.t('transactions.errors.loadFullDetails'),
      });
    }
  }

  protected closeTransaction(): void {
    this.filterMenuOpen.set(null);
    this.detailState.set({
      transaction: null,
      loading: false,
      saving: false,
      error: null,
    });
  }

  protected async toggleRowReviewed(transaction: Transaction): Promise<void> {
    try {
      const updated = await firstValueFrom(
        this.transactionService.updateReviewed(transaction.id, !transaction.reviewed),
      );
      await this.replaceTransaction(updated);
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
        error: this.t('transactions.errors.updateReviewed'),
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
      await this.loadTransactions(this.state().page);

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
        error: this.t('transactions.errors.markAllReviewed'),
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
        reviewed: false,
        page: 0,
        size: REVIEW_ALL_PAGE_SIZE,
      }),
    );
    const additionalPages = await Promise.all(
      Array.from({ length: Math.max(firstPage.totalPages - 1, 0) }, (_, index) =>
        firstValueFrom(
          this.transactionService.getTransactions({
            ...query,
            reviewed: false,
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

  protected async toggleDetailReviewed(): Promise<void> {
    const transaction = this.detailState().transaction;
    if (!transaction) return;

    this.detailState.update((current) => ({ ...current, saving: true, error: null }));

    try {
      const updated = await firstValueFrom(
        this.transactionService.updateReviewed(transaction.id, !transaction.reviewed),
      );
      await this.replaceTransaction(updated);
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
        error: this.t('transactions.errors.updateReviewed'),
      }));
    }
  }

  protected async updateDetailCategory(selectedCategory: CategoryType): Promise<void> {
    const transaction = this.detailState().transaction;
    if (!transaction || selectedCategory === transaction.category) return;

    this.detailState.update((current) => ({ ...current, saving: true, error: null }));

    try {
      const updated = await firstValueFrom(
        this.transactionService.updateCategory(transaction.id, selectedCategory),
      );
      await this.replaceTransaction(updated);
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
        error: this.t('transactions.errors.updateCategory'),
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
      await this.replaceTransaction(updated);
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
        error: this.t('transactions.errors.updateInternalTransfer'),
      }));
    }
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

  private async loadTransactions(page: number): Promise<void> {
    this.state.update((current) => ({
      ...current,
      loadingInitial: true,
      error: null,
    }));

    try {
      const query = this.currentQuery();
      const [transactionPage, summary] = await Promise.all([
        firstValueFrom(
          this.transactionService.getTransactions({
            ...query,
            page,
            size: this.pageSize(),
          }),
        ),
        firstValueFrom(this.transactionService.getTransactionSummary(query)),
      ]);
      this.mergeTransactionAccounts(transactionPage.content);
      this.state.update((current) => ({
        ...current,
        transactions: transactionPage.content,
        summary,
        loadingInitial: false,
        error: null,
        page: transactionPage.number,
        totalPages: transactionPage.totalPages,
        totalElements: transactionPage.totalElements,
      }));
    } catch {
      this.state.update((current) => ({
        ...current,
        loadingInitial: false,
        error: this.t('transactions.errors.loadTransactions'),
      }));
    }
  }

  private async openTransactionById(id: number): Promise<void> {
    this.detailState.set({ transaction: null, loading: true, saving: false, error: null });
    try {
      const transaction = await firstValueFrom(this.transactionService.getTransaction(id));
      this.detailState.set({ transaction, loading: false, saving: false, error: null });
    } catch {
      this.detailState.set({
        transaction: null,
        loading: false,
        saving: false,
        error: this.t('transactions.errors.loadTransaction'),
      });
    }
  }

  private async loadAccounts(): Promise<void> {
    try {
      this.accounts.set(await firstValueFrom(this.accountService.getAccounts()));
    } catch {
      this.accounts.set([]);
    }
  }

  protected accountLabel(account: Account): string {
    return account.name;
  }

  private mergeTransactionAccounts(transactions: Transaction[]): void {
    const accountsById = new Map(this.accounts().map((account) => [account.id, account]));
    transactions.forEach((transaction) => {
      if (transaction.accountId === null || accountsById.has(transaction.accountId)) {
        return;
      }

      accountsById.set(transaction.accountId, {
        id: transaction.accountId,
        connectionId: null,
        institutionName: null,
        name: transaction.accountName ?? this.t('transactions.archivedAccount'),
        type: null,
        accountNumberLastFour: null,
        balance: 0,
        coming: 0,
        currency: 'EUR',
        lastUpdate: null,
        disabled: true,
      });
    });
    this.accounts.set(Array.from(accountsById.values()));
  }

  private groupKey(transaction: Transaction): string {
    if (this.groupBy() === 'bank') return transaction.accountName ?? this.t('transactions.connectedAccount');
    if (this.groupBy() === 'category') return transaction.category;
    return transaction.date;
  }

  private groupLabel(key: string): string {
    if (this.groupBy() === 'category') return this.categoryLabel(key);
    if (this.groupBy() === 'date') return this.prettyDate(key);
    return key;
  }

  private optionLabel<T extends string>(options: Option<T>[], value: T): string {
    const option = options.find((item) => item.value === value);
    return option ? this.t(option.labelKey) : value;
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

  private isCategoryType(value: string): value is CategoryType {
    return CATEGORY_TYPES.includes(value as CategoryType);
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
      reviewed: this.reviewedFilterValue(),
      internalTransfer: this.internalTransferFilterValue(),
      sort: this.sortBy(),
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

  private async replaceTransaction(updated: Transaction): Promise<void> {
    this.state.update((current) => ({
      ...current,
      transactions: current.transactions.map((transaction) =>
        transaction.id === updated.id ? updated : transaction,
      ),
    }));
    await this.loadTransactions(this.state().page);
  }

  private reviewedFilterValue(): boolean | null {
    if (this.statusFilter() === 'reviewed') return true;
    if (this.statusFilter() === 'unreviewed') return false;
    return null;
  }

  private internalTransferFilterValue(): boolean | null {
    if (this.transferFilter() === 'internal') return true;
    if (this.transferFilter() === 'regular') return false;
    return null;
  }
}

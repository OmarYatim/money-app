import { CurrencyPipe, DatePipe } from '@angular/common';
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
import { isPlatformBrowser } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { CategoryColorPipe } from '../../../shared/pipes/category-color.pipe';
import type { Transaction } from '../../../shared/models/transaction.model';
import { TransactionService } from '../transaction.service';

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

@Component({
  selector: 'app-transaction-list',
  imports: [
    CurrencyPipe,
    DatePipe,
    MatButtonModule,
    MatCardModule,
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
  private readonly transactionService = inject(TransactionService);
  private readonly platformId = inject(PLATFORM_ID);
  private readonly destroyRef = inject(DestroyRef);
  private readonly sentinel = viewChild<ElementRef<HTMLElement>>('loadMoreSentinel');
  private observer: IntersectionObserver | null = null;

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
    void this.reloadTransactions();

    afterNextRender(() => {
      this.observeSentinel();
    });

    this.destroyRef.onDestroy(() => {
      this.observer?.disconnect();
    });
  }

  protected async reloadTransactions(): Promise<void> {
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

  private async loadPage(page: number): Promise<void> {
    this.state.update((current) => ({
      ...current,
      loadingInitial: page === 0 && current.transactions.length === 0,
      loadingMore: page > 0,
      error: null,
    }));

    try {
      const result = await firstValueFrom(
        this.transactionService.getTransactions({ page, size: PAGE_SIZE }),
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
}

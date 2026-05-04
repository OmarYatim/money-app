import { CurrencyPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { CATEGORY_TYPES, type CategoryType } from '../../../shared/models/category.model';
import type { Transaction } from '../../../shared/models/transaction.model';
import { CategoryColorPipe } from '../../../shared/pipes/category-color.pipe';
import { TransactionService } from '../transaction.service';

interface TransactionDetailState {
  transaction: Transaction | null;
  loading: boolean;
  saving: boolean;
  error: string | null;
}

@Component({
  selector: 'app-transaction-detail',
  imports: [
    CurrencyPipe,
    DatePipe,
    MatIconModule,
    MatProgressSpinnerModule,
    MatSlideToggleModule,
    RouterLink,
    CategoryColorPipe,
  ],
  templateUrl: './transaction-detail.component.html',
  styleUrl: './transaction-detail.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TransactionDetailComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly transactionService = inject(TransactionService);
  private readonly transactionId = Number(this.route.snapshot.paramMap.get('id'));

  protected readonly categories = CATEGORY_TYPES;

  protected readonly state = signal<TransactionDetailState>({
    transaction: null,
    loading: true,
    saving: false,
    error: null,
  });

  constructor() {
    void this.loadTransaction();
  }

  protected async toggleInternalTransfer(): Promise<void> {
    const transaction = this.state().transaction;
    if (!transaction) {
      return;
    }

    this.state.update((current) => ({ ...current, saving: true, error: null }));

    try {
      const updatedTransaction = await firstValueFrom(
        this.transactionService.updateInternalTransfer(
          this.transactionId,
          !transaction.internalTransfer,
        ),
      );
      this.state.set({
        transaction: updatedTransaction,
        loading: false,
        saving: false,
        error: null,
      });
    } catch {
      this.state.update((current) => ({
        ...current,
        saving: false,
        error: 'Unable to update internal transfer flag.',
      }));
    }
  }

  protected async toggleReviewed(): Promise<void> {
    const transaction = this.state().transaction;
    if (!transaction) {
      return;
    }

    this.state.update((current) => ({ ...current, saving: true, error: null }));

    try {
      const updatedTransaction = await firstValueFrom(
        this.transactionService.updateReviewed(this.transactionId, !transaction.reviewed),
      );
      this.state.set({
        transaction: updatedTransaction,
        loading: false,
        saving: false,
        error: null,
      });
    } catch {
      this.state.update((current) => ({
        ...current,
        saving: false,
        error: 'Unable to update reviewed state.',
      }));
    }
  }

  protected async onCategoryChange(event: Event): Promise<void> {
    const select = event.target as HTMLSelectElement;
    const selectedCategory = select.value as CategoryType;
    const transaction = this.state().transaction;
    if (!transaction || selectedCategory === transaction.category) {
      return;
    }

    this.state.update((current) => ({ ...current, saving: true, error: null }));

    try {
      const updatedTransaction = await firstValueFrom(
        this.transactionService.updateCategory(this.transactionId, selectedCategory),
      );
      this.state.set({
        transaction: updatedTransaction,
        loading: false,
        saving: false,
        error: null,
      });
    } catch {
      this.state.update((current) => ({
        ...current,
        saving: false,
        error: 'Unable to update category.',
      }));
    }
  }

  protected categoryLabel(category: string): string {
    return category
      .toLowerCase()
      .split('_')
      .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
      .join(' ');
  }

  private async loadTransaction(): Promise<void> {
    this.state.set({
      transaction: null,
      loading: true,
      saving: false,
      error: null,
    });

    try {
      const transaction = await firstValueFrom(
        this.transactionService.getTransaction(this.transactionId),
      );
      this.state.set({
        transaction,
        loading: false,
        saving: false,
        error: null,
      });
    } catch {
      this.state.set({
        transaction: null,
        loading: false,
        saving: false,
        error: 'Unable to load transaction.',
      });
    }
  }
}

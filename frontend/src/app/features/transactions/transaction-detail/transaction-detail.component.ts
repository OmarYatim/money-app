import { CurrencyPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MatBottomSheet, MatBottomSheetModule } from '@angular/material/bottom-sheet';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import type { CategoryType } from '../../../shared/models/category.model';
import type { Transaction } from '../../../shared/models/transaction.model';
import { CategoryColorPipe } from '../../../shared/pipes/category-color.pipe';
import { CategoryPickerComponent } from '../category-picker/category-picker.component';
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
    MatBottomSheetModule,
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatProgressSpinnerModule,
    RouterLink,
    CategoryColorPipe,
  ],
  templateUrl: './transaction-detail.component.html',
  styleUrl: './transaction-detail.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TransactionDetailComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly bottomSheet = inject(MatBottomSheet);
  private readonly transactionService = inject(TransactionService);
  private readonly transactionId = Number(this.route.snapshot.paramMap.get('id'));

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

  protected async editCategory(): Promise<void> {
    const transaction = this.state().transaction;
    if (!transaction) {
      return;
    }

    const bottomSheetRef = this.bottomSheet.open(CategoryPickerComponent, {
      data: { selectedCategory: transaction.category },
    });
    const selectedCategory = (await firstValueFrom(
      bottomSheetRef.afterDismissed(),
    )) as CategoryType | undefined;

    if (!selectedCategory || selectedCategory === transaction.category) {
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
      const transaction = await firstValueFrom(this.transactionService.getTransaction(this.transactionId));
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

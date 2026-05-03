import { CurrencyPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import { catchError, map, of, startWith, Subject, switchMap } from 'rxjs';

import { CategoryColorPipe } from '../../../shared/pipes/category-color.pipe';
import type { Transaction } from '../../../shared/models/transaction.model';
import { TransactionService } from '../transaction.service';

interface TransactionListState {
  transactions: Transaction[];
  loading: boolean;
  error: string | null;
}

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
  private readonly refreshTransactions$ = new Subject<void>();

  protected readonly state = toSignal(
    this.refreshTransactions$.pipe(
      startWith(undefined),
      switchMap(() =>
        this.transactionService.getTransactions().pipe(
          map(
            (transactions): TransactionListState => ({
              transactions,
              loading: false,
              error: null,
            }),
          ),
          catchError(() =>
            of({
              transactions: [],
              loading: false,
              error: 'Unable to load transactions.',
            }),
          ),
        ),
      ),
    ),
    {
      initialValue: {
        transactions: [],
        loading: true,
        error: null,
      },
    },
  );

  protected reloadTransactions(): void {
    this.refreshTransactions$.next();
  }

  protected categoryLabel(category: string): string {
    return category
      .toLowerCase()
      .split('_')
      .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
      .join(' ');
  }
}

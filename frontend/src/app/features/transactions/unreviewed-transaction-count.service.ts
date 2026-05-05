import { computed, inject, Injectable } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { catchError, map, of, startWith, Subject, switchMap } from 'rxjs';

import { TransactionService } from './transaction.service';

const BADGE_COUNT_LIMIT = 99;
const BADGE_FETCH_SIZE = 1000;

@Injectable({ providedIn: 'root' })
export class UnreviewedTransactionCountService {
  private readonly transactionService = inject(TransactionService);
  private readonly refresh$ = new Subject<void>();

  readonly count = toSignal(
    this.refresh$.pipe(
      startWith(undefined),
      switchMap(() =>
        this.transactionService.getTransactions({ size: BADGE_FETCH_SIZE }).pipe(
          map((page) => page.content.filter((transaction) => !transaction.reviewed).length),
          catchError(() => of(0)),
        ),
      ),
    ),
    { initialValue: 0 },
  );

  readonly label = computed(() => {
    const count = this.count();
    return count > BADGE_COUNT_LIMIT ? `${BADGE_COUNT_LIMIT}+` : `${count}`;
  });

  refresh(): void {
    this.refresh$.next();
  }
}

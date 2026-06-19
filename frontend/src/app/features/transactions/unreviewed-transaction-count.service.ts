import { computed, inject, Injectable } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { catchError, map, of, startWith, Subject, switchMap } from 'rxjs';

import { TransactionService } from './transaction.service';

const BADGE_COUNT_LIMIT = 99;

@Injectable({ providedIn: 'root' })
export class UnreviewedTransactionCountService {
  private readonly transactionService = inject(TransactionService);
  private readonly refresh$ = new Subject<void>();

  readonly count = toSignal(
    this.refresh$.pipe(
      startWith(undefined),
      switchMap(() =>
        this.transactionService.getTransactionSummary({ reviewed: false }).pipe(
          map((summary) => summary.totalElements),
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

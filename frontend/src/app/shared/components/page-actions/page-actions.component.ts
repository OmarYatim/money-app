import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';

import { UnreviewedTransactionCountService } from '../../../features/transactions/unreviewed-transaction-count.service';

@Component({
  selector: 'app-page-actions',
  imports: [MatIconModule, RouterLink],
  templateUrl: './page-actions.component.html',
  styleUrl: './page-actions.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PageActionsComponent {
  private readonly unreviewedTransactionCountService = inject(UnreviewedTransactionCountService);

  protected readonly todayLabel = new Intl.DateTimeFormat('en-GB', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
  }).format(new Date());
  protected readonly unreviewedCount = this.unreviewedTransactionCountService.count;
  protected readonly unreviewedCountLabel = this.unreviewedTransactionCountService.label;
}

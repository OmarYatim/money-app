import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';

import { LanguageService } from '../../../core/i18n/language.service';
import { UnreviewedTransactionCountService } from '../../../features/transactions/unreviewed-transaction-count.service';

@Component({
  selector: 'app-page-actions',
  imports: [MatIconModule, RouterLink, TranslatePipe],
  templateUrl: './page-actions.component.html',
  styleUrl: './page-actions.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PageActionsComponent {
  private readonly unreviewedTransactionCountService = inject(UnreviewedTransactionCountService);
  private readonly languageService = inject(LanguageService);

  protected readonly todayLabel = computed(() =>
    new Intl.DateTimeFormat(this.dateLocale(), {
      weekday: 'long',
      day: 'numeric',
      month: 'long',
    }).format(new Date()),
  );
  protected readonly unreviewedCount = this.unreviewedTransactionCountService.count;
  protected readonly unreviewedCountLabel = this.unreviewedTransactionCountService.label;

  private dateLocale(): string {
    return this.languageService.currentLang() === 'en' ? 'en-GB' : this.languageService.currentLang();
  }
}

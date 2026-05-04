import { CurrencyPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

export type SummaryCardTone = 'neutral' | 'positive' | 'negative';

@Component({
  selector: 'app-summary-card',
  imports: [CurrencyPipe],
  templateUrl: './summary-card.component.html',
  styleUrl: './summary-card.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SummaryCardComponent {
  readonly label = input.required<string>();
  readonly value = input.required<number>();
  readonly icon = input.required<string>();
  readonly tone = input<SummaryCardTone>('neutral');
  readonly showSign = input(false);

  protected readonly displaySign = computed(() => {
    if (!this.showSign()) {
      return '';
    }

    return this.value() > 0 ? '+' : '';
  });
}

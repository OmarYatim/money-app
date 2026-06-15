import { CurrencyPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import type { ChartData, ChartEvent, ChartOptions } from 'chart.js';
import { BaseChartDirective } from 'ng2-charts';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';

import { LanguageService } from '../../../core/i18n/language.service';
import type { SpendingByCategory } from '../../../shared/models/report.model';

const CATEGORY_COLORS: Record<string, string> = {
  GROCERIES: '#5b5fef',
  DINING: '#d99838',
  TRANSPORT: '#3aa8c4',
  UTILITIES: '#9396a8',
  RENT: '#7c80f5',
  HEALTH: '#2cad6a',
  ENTERTAINMENT: '#e04a62',
  SHOPPING: '#c8366f',
  TRAVEL: '#3aa8c4',
  EDUCATION: '#7c80f5',
  INCOME: '#1f8a52',
  TRANSFER: '#6b6e85',
  SAVINGS: '#2cad6a',
  SUBSCRIPTION: '#7c80f5',
  OTHER: '#9396a8',
};

@Component({
  selector: 'app-spending-chart',
  imports: [BaseChartDirective, CurrencyPipe, TranslatePipe],
  templateUrl: './spending-chart.component.html',
  styleUrl: './spending-chart.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SpendingChartComponent {
  private readonly languageService = inject(LanguageService);
  private readonly translate = inject(TranslateService);

  readonly data = input.required<SpendingByCategory[]>();
  readonly categorySelected = output<string>();

  protected readonly total = computed(() =>
    this.data().reduce((sum, item) => sum + item.totalAmount, 0),
  );

  protected readonly chartData = computed<ChartData<'doughnut'>>(() => ({
    labels: this.data().map((item) => this.categoryLabel(item.category)),
    datasets: [
      {
        data: this.data().map((item) => item.totalAmount),
        backgroundColor: this.data().map((item) => this.categoryColor(item.category)),
        borderColor: '#ffffff',
        borderWidth: 2,
        hoverOffset: 6,
      },
    ],
  }));

  protected readonly chartOptions: ChartOptions<'doughnut'> = {
    responsive: true,
    maintainAspectRatio: false,
    cutout: '58%',
    plugins: {
      legend: { display: false },
      tooltip: {
        callbacks: {
          label: (context) => {
            const label = context.label ?? '';
            const value = Number(context.parsed ?? 0);
            return `${label}: ${new Intl.NumberFormat(this.dateLocale(), {
              style: 'currency',
              currency: 'EUR',
            }).format(value)}`;
          },
        },
      },
    },
  };

  protected selectSegment(event: { event?: ChartEvent; active?: object[] }): void {
    const activeElement = event.active?.[0];
    if (!this.hasChartElementIndex(activeElement)) {
      return;
    }

    const item = this.data()[activeElement.index];
    if (item) {
      this.categorySelected.emit(item.category);
    }
  }

  protected categoryColor(category: string): string {
    return CATEGORY_COLORS[category.toUpperCase()] ?? CATEGORY_COLORS['OTHER'];
  }

  protected categoryLabel(category: string): string {
    this.languageService.currentLang();
    return this.translate.instant(`categories.${category.toLowerCase()}`);
  }

  private dateLocale(): string {
    return this.languageService.currentLang() === 'en' ? 'en-GB' : this.languageService.currentLang();
  }

  private hasChartElementIndex(value: object | undefined): value is { index: number } {
    return typeof (value as { index?: unknown } | undefined)?.index === 'number';
  }
}

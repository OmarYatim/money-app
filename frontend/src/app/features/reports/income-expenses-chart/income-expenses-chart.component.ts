import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import type { ChartData, ChartOptions } from 'chart.js';
import { BaseChartDirective } from 'ng2-charts';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';

import { LanguageService } from '../../../core/i18n/language.service';
import type { IncomeExpenses } from '../../../shared/models/report.model';

@Component({
  selector: 'app-income-expenses-chart',
  imports: [BaseChartDirective, TranslatePipe],
  templateUrl: './income-expenses-chart.component.html',
  styleUrl: './income-expenses-chart.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class IncomeExpensesChartComponent {
  private readonly languageService = inject(LanguageService);
  private readonly translate = inject(TranslateService);

  readonly data = input.required<IncomeExpenses[]>();
  readonly showNetLine = input(true);

  protected readonly chartData = computed<ChartData>(() => {
    const datasets: ChartData['datasets'] = [
      {
        type: 'bar',
        label: this.t('categories.income'),
        data: this.data().map((item) => item.totalIncome),
        backgroundColor: '#2cad6a',
        borderRadius: 4,
      },
      {
        type: 'bar',
        label: this.t('reports.spending'),
        data: this.data().map((item) => item.totalExpenses),
        backgroundColor: '#c5c7d4',
        borderRadius: 4,
      },
    ];

    if (this.showNetLine()) {
      datasets.push({
        type: 'line',
        label: this.t('transactions.summary.net'),
        data: this.data().map((item) => item.netCashFlow),
        borderColor: '#5b5fef',
        backgroundColor: '#5b5fef',
        tension: 0.35,
        pointRadius: 3,
        pointHoverRadius: 5,
        yAxisID: 'y',
      });
    }

    return {
      labels: this.data().map((item) => this.monthLabel(item.month)),
      datasets,
    };
  });

  protected readonly chartOptions: ChartOptions = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {
        labels: {
          boxHeight: 8,
          boxWidth: 8,
          color: '#6b6e85',
          font: { size: 11 },
        },
      },
      tooltip: {
        callbacks: {
          label: (context) =>
            `${context.dataset.label}: ${new Intl.NumberFormat(this.dateLocale(), {
              style: 'currency',
              currency: 'EUR',
            }).format(Number(context.parsed.y ?? 0))}`,
        },
      },
    },
    scales: {
      x: { grid: { display: false }, ticks: { color: '#9396a8', font: { size: 10 } } },
      y: {
        beginAtZero: true,
        grid: { color: '#e8e8ed', tickBorderDash: [2, 4] },
        ticks: {
          color: '#9396a8',
          font: { size: 10 },
          callback: (value) => `€${Number(value) / 1000}k`,
        },
      },
    },
  };

  private monthLabel(month: string): string {
    const [year, monthNumber] = month.split('-').map(Number);
    return new Intl.DateTimeFormat(this.dateLocale(), { month: 'short' }).format(
      new Date(year, monthNumber - 1, 1),
    );
  }

  private dateLocale(): string {
    return this.languageService.currentLang() === 'en' ? 'en-GB' : this.languageService.currentLang();
  }

  private t(key: string): string {
    this.languageService.currentLang();
    return this.translate.instant(key);
  }
}

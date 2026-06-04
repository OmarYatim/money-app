import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import type { ChartData, ChartOptions } from 'chart.js';
import { BaseChartDirective } from 'ng2-charts';

import type { IncomeExpenses } from '../../../shared/models/report.model';

@Component({
  selector: 'app-income-expenses-chart',
  imports: [BaseChartDirective],
  templateUrl: './income-expenses-chart.component.html',
  styleUrl: './income-expenses-chart.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class IncomeExpensesChartComponent {
  readonly data = input.required<IncomeExpenses[]>();

  protected readonly chartData = computed<ChartData<'bar'>>(() => ({
    labels: this.data().map((item) => this.monthLabel(item.month)),
    datasets: [
      {
        type: 'bar',
        label: 'Income',
        data: this.data().map((item) => item.totalIncome),
        backgroundColor: '#2cad6a',
        borderRadius: 4,
      },
      {
        type: 'bar',
        label: 'Spending',
        data: this.data().map((item) => item.totalExpenses),
        backgroundColor: '#c5c7d4',
        borderRadius: 4,
      },
      {
        type: 'line',
        label: 'Net',
        data: this.data().map((item) => item.netCashFlow),
        borderColor: '#5b5fef',
        backgroundColor: '#5b5fef',
        tension: 0.35,
        pointRadius: 3,
        pointHoverRadius: 5,
        yAxisID: 'y',
      },
    ],
  }));

  protected readonly chartOptions: ChartOptions<'bar'> = {
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
            `${context.dataset.label}: ${new Intl.NumberFormat('fr-FR', {
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
    return new Intl.DateTimeFormat('en', { month: 'short' }).format(
      new Date(year, monthNumber - 1, 1),
    );
  }
}

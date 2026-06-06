import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import type { ChartData, ChartOptions } from 'chart.js';
import { BaseChartDirective } from 'ng2-charts';

import type { NetWorthHistory } from '../../../shared/models/report.model';

@Component({
  selector: 'app-net-worth-chart',
  imports: [BaseChartDirective],
  templateUrl: './net-worth-chart.component.html',
  styleUrl: './net-worth-chart.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NetWorthChartComponent {
  readonly data = input.required<NetWorthHistory[]>();
  readonly forecastMonthlyDelta = input(0);
  readonly forecastMonths = input(4);

  protected readonly chartData = computed<ChartData<'line'>>(() => ({
    labels: this.chartLabels(),
    datasets: [
      {
        label: 'Net worth',
        data: [
          ...this.data().map((item) => item.netWorth),
          ...Array(this.forecastLabels().length).fill(null),
        ],
        borderColor: '#5b5fef',
        backgroundColor: 'rgba(91, 95, 239, 0.16)',
        fill: true,
        tension: 0.32,
        pointRadius: 3,
        pointHoverRadius: 5,
      },
      {
        label: 'Forecast',
        data: [...Array(Math.max(this.data().length - 1, 0)).fill(null), ...this.forecastPoints()],
        borderColor: '#5b5fef',
        backgroundColor: '#5b5fef',
        borderDash: [5, 5],
        fill: false,
        tension: 0.32,
        pointRadius: 2,
        pointHoverRadius: 4,
      },
    ],
  }));

  protected readonly chartOptions: ChartOptions<'line'> = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {
        labels: {
          boxHeight: 8,
          boxWidth: 14,
          color: '#6b6e85',
          font: { size: 11 },
        },
      },
      tooltip: {
        callbacks: {
          label: (context) =>
            new Intl.NumberFormat('fr-FR', {
              style: 'currency',
              currency: 'EUR',
            }).format(Number(context.parsed.y ?? 0)),
        },
      },
    },
    scales: {
      x: { grid: { display: false }, ticks: { color: '#9396a8', font: { size: 10 } } },
      y: {
        grid: { color: '#e8e8ed', tickBorderDash: [2, 4] },
        ticks: {
          color: '#9396a8',
          font: { size: 10 },
          callback: (value) => `€${Math.round(Number(value) / 1000)}k`,
        },
      },
    },
  };

  private dateLabel(date: string): string {
    return new Intl.DateTimeFormat('en', { month: 'short', day: 'numeric' }).format(
      new Date(`${date}T00:00:00`),
    );
  }

  private chartLabels(): string[] {
    return [...this.data().map((item) => this.dateLabel(item.date)), ...this.forecastLabels()];
  }

  private forecastLabels(): string[] {
    const lastPoint = this.data().at(-1);
    if (!lastPoint) {
      return [];
    }

    const lastDate = new Date(`${lastPoint.date}T00:00:00`);
    return Array.from({ length: this.forecastMonths() }, (_, index) => {
      const date = new Date(lastDate.getFullYear(), lastDate.getMonth() + index + 1, 1);
      return new Intl.DateTimeFormat('en', { month: 'short' }).format(date);
    });
  }

  private forecastPoints(): number[] {
    const lastPoint = this.data().at(-1);
    if (!lastPoint) {
      return [];
    }

    return [
      lastPoint.netWorth,
      ...Array.from({ length: this.forecastMonths() }, (_, index) =>
        Math.round(lastPoint.netWorth + this.forecastMonthlyDelta() * (index + 1)),
      ),
    ];
  }
}

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

  protected readonly chartData = computed<ChartData<'line'>>(() => ({
    labels: this.data().map((item) => this.dateLabel(item.date)),
    datasets: [
      {
        label: 'Net worth',
        data: this.data().map((item) => item.netWorth),
        borderColor: '#5b5fef',
        backgroundColor: 'rgba(91, 95, 239, 0.16)',
        fill: true,
        tension: 0.32,
        pointRadius: 3,
        pointHoverRadius: 5,
      },
    ],
  }));

  protected readonly chartOptions: ChartOptions<'line'> = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: { display: false },
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
}

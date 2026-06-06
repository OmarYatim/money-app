import { CurrencyPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

import type { Goal } from '../../../shared/models/goal.model';

@Component({
  selector: 'app-goal-list',
  imports: [CurrencyPipe, DatePipe],
  templateUrl: './goal-list.component.html',
  styleUrl: './goal-list.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GoalListComponent {
  goals = input.required<Goal[]>();
  selectedGoalId = input<number | null>(null);
  selected = output<Goal>();
  archived = output<Goal>();

  protected goalColor(goal: Goal): string {
    return normalizeGoalColor(goal.color);
  }
}

function normalizeGoalColor(color: string): string {
  const colors: Record<string, string> = {
    indigo: '#5b5fef',
    green: '#2cad6a',
    amber: '#d99838',
    red: '#e04a62',
    slate: '#9396a8',
  };

  return colors[color] ?? color;
}

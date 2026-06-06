import { CurrencyPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

import type { Goal, GoalContribution } from '../../../shared/models/goal.model';

@Component({
  selector: 'app-goal-detail',
  imports: [CurrencyPipe, DatePipe],
  templateUrl: './goal-detail.component.html',
  styleUrl: './goal-detail.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GoalDetailComponent {
  goal = input<Goal | null>(null);
  contributions = input<GoalContribution[]>([]);
  addContribution = output<void>();
  edit = output<void>();

  protected readonly remainingAmount = computed(() => {
    const goal = this.goal();
    return goal ? Math.max(goal.targetAmount - goal.currentAmount, 0) : 0;
  });
}

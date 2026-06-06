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
}

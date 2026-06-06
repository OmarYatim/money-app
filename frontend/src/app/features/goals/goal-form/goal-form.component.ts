import { ChangeDetectionStrategy, Component, computed, effect, input, output } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';

import type { Account } from '../../../shared/models/account.model';
import type { Goal, GoalPayload } from '../../../shared/models/goal.model';

interface GoalFormValue {
  name: FormControl<string>;
  targetAmount: FormControl<number | null>;
  targetDate: FormControl<string>;
  linkedAccountId: FormControl<number | null>;
}

@Component({
  selector: 'app-goal-form',
  imports: [ReactiveFormsModule],
  templateUrl: './goal-form.component.html',
  styleUrl: './goal-form.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GoalFormComponent {
  goal = input<Goal | null>(null);
  accounts = input<Account[]>([]);
  saving = input(false);
  goalSubmitted = output<GoalPayload>();
  formClosed = output<void>();

  protected readonly heading = computed(() => (this.goal() ? 'Edit goal' : 'New goal'));
  protected readonly submitLabel = computed(() => (this.goal() ? 'Save goal' : 'Create goal'));

  protected readonly form = new FormGroup<GoalFormValue>({
    name: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    targetAmount: new FormControl<number | null>(null, [Validators.required, Validators.min(0.01)]),
    targetDate: new FormControl('', { nonNullable: true }),
    linkedAccountId: new FormControl<number | null>(null),
  });

  constructor() {
    effect(() => {
      this.goal();
      this.fillFromGoal();
    });
  }

  protected fillFromGoal(): void {
    const goal = this.goal();
    this.form.setValue({
      name: goal?.name ?? '',
      targetAmount: goal?.targetAmount ?? null,
      targetDate: goal?.targetDate ?? '',
      linkedAccountId: goal?.linkedAccountId ?? null,
    });
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const value = this.form.getRawValue();
    this.goalSubmitted.emit({
      name: value.name.trim(),
      targetAmount: value.targetAmount ?? 0,
      targetDate: value.targetDate || null,
      linkedAccountId: value.linkedAccountId,
    });
  }
}

import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslatePipe } from '@ngx-translate/core';

import type { GoalContributionPayload } from '../../../shared/models/goal.model';

interface ContributionFormValue {
  amount: FormControl<number | null>;
  note: FormControl<string>;
  contributedAt: FormControl<string>;
}

@Component({
  selector: 'app-contribution-dialog',
  imports: [ReactiveFormsModule, TranslatePipe],
  templateUrl: './contribution-dialog.component.html',
  styleUrl: './contribution-dialog.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ContributionDialogComponent {
  saving = input(false);
  contributionSubmitted = output<GoalContributionPayload>();
  formClosed = output<void>();

  protected readonly form = new FormGroup<ContributionFormValue>({
    amount: new FormControl<number | null>(null, [Validators.required, Validators.min(0.01)]),
    note: new FormControl('', { nonNullable: true }),
    contributedAt: new FormControl('', { nonNullable: true }),
  });

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const value = this.form.getRawValue();
    this.contributionSubmitted.emit({
      amount: value.amount ?? 0,
      note: value.note.trim() || null,
      contributedAt: value.contributedAt || null,
    });
  }
}

import { CurrencyPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, effect, input, output, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { startWith } from 'rxjs';

import type { Account } from '../../../shared/models/account.model';
import type { Goal, GoalPayload } from '../../../shared/models/goal.model';

interface GoalFormValue {
  name: FormControl<string>;
  icon: FormControl<string>;
  color: FormControl<string>;
  category: FormControl<string>;
  priority: FormControl<string>;
  note: FormControl<string>;
  targetAmount: FormControl<number | null>;
  linkedAccountId: FormControl<number | null>;
  autoSaveEnabled: FormControl<boolean>;
  plannedMonthlyContribution: FormControl<number | null>;
  targetMonth: FormControl<string>;
}

type GoalFormTab = 'details' | 'funding';

interface Choice {
  value: string;
  label: string;
}

interface ColorChoice extends Choice {
  swatch: string;
}

const ICON_CHOICES: Choice[] = [
  { value: 'shield', label: 'Shield' },
  { value: 'flight', label: 'Travel' },
  { value: 'computer', label: 'Tech' },
  { value: 'home', label: 'Home' },
  { value: 'directions_car', label: 'Car' },
  { value: 'celebration', label: 'Event' },
  { value: 'savings', label: 'Savings' },
  { value: 'flag', label: 'Flag' },
];

const COLOR_CHOICES: ColorChoice[] = [
  { value: 'indigo', label: 'Indigo', swatch: 'var(--indigo-500)' },
  { value: 'green', label: 'Green', swatch: 'var(--green-500)' },
  { value: 'amber', label: 'Amber', swatch: 'var(--amber-500)' },
  { value: 'red', label: 'Red', swatch: 'var(--red-500)' },
  { value: 'slate', label: 'Slate', swatch: 'var(--ink-400)' },
];

const CATEGORY_CHOICES = ['Safety net', 'Travel', 'Tech', 'Family', 'Long-term', 'Transport', 'Health', 'Education', 'Other'];
const PRIORITY_CHOICES = ['Essential', 'High', 'Medium', 'Low'];

@Component({
  selector: 'app-goal-form',
  imports: [CurrencyPipe, ReactiveFormsModule],
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

  protected readonly activeTab = signal<GoalFormTab>('details');
  protected readonly iconChoices = ICON_CHOICES;
  protected readonly colorChoices = COLOR_CHOICES;
  protected readonly categoryChoices = CATEGORY_CHOICES;
  protected readonly priorityChoices = PRIORITY_CHOICES;
  protected readonly heading = computed(() => (this.goal() ? 'Edit goal' : 'New goal'));
  protected readonly submitLabel = computed(() => (this.goal() ? 'Save goal' : 'Create goal'));
  protected readonly form = new FormGroup<GoalFormValue>({
    name: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    icon: new FormControl('flag', { nonNullable: true }),
    color: new FormControl('indigo', { nonNullable: true }),
    category: new FormControl('Other', { nonNullable: true }),
    priority: new FormControl('Medium', { nonNullable: true }),
    note: new FormControl('', { nonNullable: true }),
    targetAmount: new FormControl<number | null>(null, [Validators.required, Validators.min(0.01)]),
    linkedAccountId: new FormControl<number | null>(null),
    autoSaveEnabled: new FormControl(false, { nonNullable: true }),
    plannedMonthlyContribution: new FormControl<number | null>(0, [Validators.min(0)]),
    targetMonth: new FormControl('', { nonNullable: true }),
  });
  private readonly formValue = toSignal(
    this.form.valueChanges.pipe(startWith(this.form.getRawValue())),
    { initialValue: this.form.getRawValue() },
  );
  protected readonly targetAmount = computed(() => this.formValue().targetAmount ?? 0);
  protected readonly monthlyAmount = computed(() => this.formValue().plannedMonthlyContribution ?? 0);
  protected readonly remainingAmount = computed(() => {
    const goal = this.goal();
    return Math.max(this.targetAmount() - (goal?.currentAmount ?? 0), 0);
  });
  protected readonly forecastMonths = computed(() => {
    const monthly = this.monthlyAmount();
    return monthly > 0 ? Math.ceil(this.remainingAmount() / monthly) : null;
  });
  protected readonly forecastLabel = computed(() => {
    const months = this.forecastMonths();
    if (months === null) {
      return 'No projection';
    }

    const date = new Date();
    date.setMonth(date.getMonth() + months);
    return date.toLocaleDateString('en-GB', { month: 'short', year: 'numeric' });
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
      icon: goal?.icon ?? 'flag',
      color: goal?.color ?? 'indigo',
      category: goal?.category ?? 'Other',
      priority: goal?.priority ?? 'Medium',
      note: goal?.note ?? '',
      targetAmount: goal?.targetAmount ?? null,
      linkedAccountId: goal?.linkedAccountId ?? null,
      autoSaveEnabled: goal?.autoSaveEnabled ?? false,
      plannedMonthlyContribution: goal?.plannedMonthlyContribution ?? 0,
      targetMonth: goal?.targetDate ? goal.targetDate.slice(0, 7) : '',
    });
  }

  protected selectTab(tab: GoalFormTab): void {
    this.activeTab.set(tab);
  }

  protected chooseIcon(icon: string): void {
    this.form.controls.icon.setValue(icon);
  }

  protected chooseColor(color: string): void {
    this.form.controls.color.setValue(color);
  }

  protected choosePriority(priority: string): void {
    this.form.controls.priority.setValue(priority);
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
      targetDate: value.targetMonth ? `${value.targetMonth}-01` : null,
      linkedAccountId: value.linkedAccountId,
      icon: value.icon,
      color: value.color,
      category: value.category,
      priority: value.priority,
      note: value.note.trim() || null,
      autoSaveEnabled: value.autoSaveEnabled,
      plannedMonthlyContribution: value.plannedMonthlyContribution ?? 0,
    });
  }
}

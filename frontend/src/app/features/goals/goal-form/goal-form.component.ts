import { CurrencyPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { startWith } from 'rxjs';

import { LanguageService } from '../../../core/i18n/language.service';
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
  labelKey: string;
}

interface ColorChoice extends Choice {
  swatch: string;
}

const ICON_CHOICES: Choice[] = [
  { value: 'shield', labelKey: 'goals.icons.shield' },
  { value: 'flight', labelKey: 'goals.icons.travel' },
  { value: 'computer', labelKey: 'goals.icons.tech' },
  { value: 'home', labelKey: 'goals.icons.home' },
  { value: 'directions_car', labelKey: 'goals.icons.car' },
  { value: 'celebration', labelKey: 'goals.icons.event' },
  { value: 'savings', labelKey: 'goals.icons.savings' },
  { value: 'flag', labelKey: 'goals.icons.flag' },
];

const COLOR_CHOICES: ColorChoice[] = [
  { value: '#5b5fef', labelKey: 'goals.colors.indigo', swatch: '#5b5fef' },
  { value: '#4f52e0', labelKey: 'goals.colors.deepIndigo', swatch: '#4f52e0' },
  { value: '#2cad6a', labelKey: 'goals.colors.green', swatch: '#2cad6a' },
  { value: '#d99838', labelKey: 'goals.colors.amber', swatch: '#d99838' },
  { value: '#e04a62', labelKey: 'goals.colors.rose', swatch: '#e04a62' },
  { value: '#9396a8', labelKey: 'goals.colors.slate', swatch: '#9396a8' },
  { value: '#14163a', labelKey: 'goals.colors.ink', swatch: '#14163a' },
];

const CATEGORY_CHOICES = ['Safety net', 'Travel', 'Tech', 'Family', 'Long-term', 'Transport', 'Health', 'Education', 'Other'];
const PRIORITY_CHOICES = ['Essential', 'High', 'Medium', 'Low'];

@Component({
  selector: 'app-goal-form',
  imports: [CurrencyPipe, ReactiveFormsModule, TranslatePipe],
  templateUrl: './goal-form.component.html',
  styleUrl: './goal-form.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GoalFormComponent {
  private readonly languageService = inject(LanguageService);
  private readonly translate = inject(TranslateService);

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
  protected readonly heading = computed(() => (this.goal() ? this.t('goals.form.editGoal') : this.t('goals.form.newGoal')));
  protected readonly submitLabel = computed(() => (this.goal() ? this.t('goals.form.saveGoal') : this.t('goals.form.createGoal')));
  protected readonly form = new FormGroup<GoalFormValue>({
    name: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    icon: new FormControl('flag', { nonNullable: true }),
    color: new FormControl('#5b5fef', { nonNullable: true }),
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
  protected readonly selectedColor = computed(() => this.formValue().color ?? '#5b5fef');
  protected readonly selectedColorSoft = computed(() => this.softColor(this.selectedColor(), 14));
  protected readonly targetAmount = computed(() => this.formValue().targetAmount ?? 0);
  protected readonly monthlyAmount = computed(() => this.formValue().plannedMonthlyContribution ?? 0);
  protected readonly remainingAmount = computed(() => {
    const goal = this.goal();
    return Math.max(this.targetAmount() - (goal?.currentAmount ?? 0), 0);
  });
  protected readonly previewProgress = computed(() => {
    const target = this.targetAmount();
    const current = this.goal()?.currentAmount ?? 0;
    return target > 0 ? Math.min(Math.round((current / target) * 100), 100) : 0;
  });
  protected readonly forecastMonths = computed(() => {
    const monthly = this.monthlyAmount();
    return monthly > 0 ? Math.ceil(this.remainingAmount() / monthly) : null;
  });
  protected readonly forecastLabel = computed(() => {
    const months = this.forecastMonths();
    if (months === null) {
      return this.t('goals.noProjection');
    }

    const date = new Date();
    date.setMonth(date.getMonth() + months);
    return date.toLocaleDateString(this.dateLocale(), { month: 'short', year: 'numeric' });
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
      color: this.normalizedColor(goal?.color),
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

  protected goalCategoryLabel(category: string): string {
    return this.t(`goals.category.${this.key(category)}`);
  }

  protected priorityLabel(priority: string): string {
    return this.t(`goals.priority.${priority.toLowerCase()}`);
  }

  protected updateTargetAmount(value: string): void {
    this.form.controls.targetAmount.setValue(Math.max(0, Number(value) || 0));
  }

  protected updateMonthlyAmount(value: string): void {
    this.form.controls.plannedMonthlyContribution.setValue(Math.max(0, Number(value) || 0));
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
      autoSaveEnabled: (value.plannedMonthlyContribution ?? 0) > 0,
      plannedMonthlyContribution: value.plannedMonthlyContribution ?? 0,
    });
  }

  private normalizedColor(color: string | null | undefined): string {
    if (!color || color === 'indigo') {
      return '#5b5fef';
    }

    if (color === 'green') {
      return '#2cad6a';
    }

    if (color === 'amber') {
      return '#d99838';
    }

    if (color === 'red') {
      return '#e04a62';
    }

    if (color === 'slate') {
      return '#9396a8';
    }

    return color;
  }

  private softColor(color: string, opacity: number): string {
    const normalized = color.startsWith('#') ? color.slice(1) : color;

    if (normalized.length !== 6) {
      return 'var(--lavender-50)';
    }

    const alpha = Math.round((opacity / 100) * 255)
      .toString(16)
      .padStart(2, '0');

    return `#${normalized}${alpha}`;
  }

  private dateLocale(): string {
    return this.languageService.currentLang() === 'en' ? 'en-GB' : this.languageService.currentLang();
  }

  private key(value: string): string {
    return value.toLowerCase().replace(/[^a-z0-9]+/g, '');
  }

  private t(key: string, params?: Record<string, unknown>): string {
    return this.translate.instant(key, params);
  }
}

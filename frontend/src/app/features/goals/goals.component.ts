import { CurrencyPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { PageActionsComponent } from '../../shared/components/page-actions/page-actions.component';
import type { Account } from '../../shared/models/account.model';
import type {
  Goal,
  GoalContribution,
  GoalContributionPayload,
  GoalPayload,
} from '../../shared/models/goal.model';
import { AccountService } from '../accounts/account.service';
import { ContributionDialogComponent } from './contribution-dialog/contribution-dialog.component';
import { GoalFormComponent } from './goal-form/goal-form.component';
import { GoalService } from './goal.service';

interface GoalsState {
  goals: Goal[];
  contributions: GoalContribution[];
  loading: boolean;
  saving: boolean;
  error: string | null;
}

type GoalsPanel = 'none' | 'create' | 'edit' | 'contribution';
type GoalsView = 'overview' | 'timeline';
type PriorityFilter = 'All' | 'Essential' | 'High' | 'Medium' | 'Low';

interface TimelineGoal {
  goal: Goal;
  progressPercent: number;
  targetPosition: number | null;
}

interface GoalKpi {
  icon: string;
  label: string;
  value: string;
  sub: string;
  tone: 'default' | 'positive';
}

interface ContributionFeedItem {
  goal: Goal | null;
  amount: number;
  contributedAt: string;
  mode: string;
}

const INITIAL_STATE: GoalsState = {
  goals: [],
  contributions: [],
  loading: true,
  saving: false,
  error: null,
};

@Component({
  selector: 'app-goals',
  imports: [
    CurrencyPipe,
    DatePipe,
    PageActionsComponent,
    ContributionDialogComponent,
    GoalFormComponent,
  ],
  templateUrl: './goals.component.html',
  styleUrl: './goals.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GoalsComponent {
  private readonly accountService = inject(AccountService);
  private readonly goalService = inject(GoalService);

  protected readonly state = signal<GoalsState>(INITIAL_STATE);
  protected readonly accounts = signal<Account[]>([]);
  protected readonly selectedGoalId = signal<number | null>(null);
  protected readonly activePanel = signal<GoalsPanel>('none');
  protected readonly view = signal<GoalsView>('overview');
  protected readonly priorityFilter = signal<PriorityFilter>('All');
  protected readonly priorityFilters: PriorityFilter[] = ['All', 'Essential', 'High', 'Medium', 'Low'];

  protected readonly selectedGoal = computed(() => {
    const selectedGoalId = this.selectedGoalId();
    return this.state().goals.find((goal) => goal.id === selectedGoalId) ?? this.state().goals[0] ?? null;
  });

  protected readonly activeGoals = computed(() => this.state().goals.filter((goal) => !goal.archived));
  protected readonly filteredGoals = computed(() => {
    const filter = this.priorityFilter();
    const goals = this.activeGoals();

    return filter === 'All' ? goals : goals.filter((goal) => goal.priority === filter);
  });
  protected readonly onTrackGoals = computed(() =>
    this.activeGoals().filter((goal) => goal.projectedCompletionDate && goal.targetDate
      ? goal.projectedCompletionDate <= goal.targetDate
      : goal.progressPercent >= 40),
  );
  protected readonly totalSaved = computed(() =>
    this.activeGoals().reduce((sum, goal) => sum + goal.currentAmount, 0),
  );
  protected readonly totalTarget = computed(() =>
    this.activeGoals().reduce((sum, goal) => sum + goal.targetAmount, 0),
  );
  protected readonly monthlyPace = computed(() =>
    this.activeGoals().reduce(
      (sum, goal) => sum + (goal.autoSaveEnabled ? goal.plannedMonthlyContribution : goal.monthlyRate),
      0,
    ),
  );
  protected readonly overallProgress = computed(() => {
    const target = this.totalTarget();
    return target > 0 ? Math.round((this.totalSaved() / target) * 100) : 0;
  });
  protected readonly projectedYearEnd = computed(() => this.totalSaved() + this.monthlyPace() * 6);
  protected readonly projectedReach = computed(() => this.totalSaved() + this.monthlyPace() * 12);
  protected readonly averageProgress = computed(() => {
    const goals = this.activeGoals();
    if (goals.length === 0) {
      return 0;
    }

    return Math.round(goals.reduce((sum, goal) => sum + goal.progressPercent, 0) / goals.length);
  });
  protected readonly averageStreak = computed(() => {
    const activeGoals = this.activeGoals().filter((goal) => this.monthlyAmount(goal) > 0);
    return activeGoals.length > 0 ? Math.round(activeGoals.reduce((sum, goal) => sum + this.goalStreak(goal), 0) / activeGoals.length) : 0;
  });
  protected readonly longestStreakGoal = computed(() => {
    const goals = this.activeGoals();
    return goals.reduce<Goal | null>((longest, goal) => {
      if (!longest || this.goalStreak(goal) > this.goalStreak(longest)) {
        return goal;
      }

      return longest;
    }, null);
  });
  protected readonly kpis = computed<GoalKpi[]>(() => {
    const goals = this.activeGoals();
    const longest = this.longestStreakGoal();

    return [
      {
        icon: 'savings',
        label: 'Total saved',
        value: formatEuro(this.totalSaved()),
        sub: `of ${formatEuro(this.totalTarget())} across ${goals.length} goals`,
        tone: 'positive',
      },
      {
        icon: 'track_changes',
        label: 'Overall progress',
        value: `${this.overallProgress()}%`,
        sub: 'On pace for 2027',
        tone: 'default',
      },
      {
        icon: 'repeat',
        label: 'Monthly auto-save',
        value: formatEuro(this.monthlyPace()),
        sub: `${goals.filter((goal) => this.monthlyAmount(goal) > 0).length} of ${goals.length} goals active`,
        tone: 'default',
      },
      {
        icon: 'check_circle',
        label: 'On track',
        value: `${this.onTrackGoals().length}/${goals.length}`,
        sub: `${Math.max(this.onTrackGoals().length - 1, 0)} ahead · ${Math.max(goals.length - this.onTrackGoals().length, 0)} lagging`,
        tone: 'positive',
      },
      {
        icon: 'flag',
        label: 'Avg streak',
        value: `${this.averageStreak()} mo`,
        sub: longest ? `Longest: ${longest.name} · ${this.goalStreak(longest)}m` : 'No streak yet',
        tone: 'default',
      },
      {
        icon: 'trending_up',
        label: 'Projected reach',
        value: formatEuro(this.projectedReach()),
        sub: 'By end of 2026 at current pace',
        tone: 'positive',
      },
    ];
  });
  protected readonly contributionFeed = computed<ContributionFeedItem[]>(() => {
    const goalsById = new Map(this.activeGoals().map((goal) => [goal.id, goal]));

    return this.state().contributions.map((contribution) => ({
      goal: goalsById.get(contribution.goalId) ?? null,
      amount: contribution.amount,
      contributedAt: contribution.contributedAt,
      mode: contribution.note?.trim() || (goalsById.get(contribution.goalId)?.autoSaveEnabled ? 'Auto-save' : 'Manual'),
    }));
  });
  protected readonly timelineGoals = computed<TimelineGoal[]>(() => {
    const goals = this.activeGoals();
    const targetDates = goals
      .map((goal) => goal.targetDate)
      .filter((targetDate): targetDate is string => targetDate !== null)
      .sort();
    const start = targetDates[0] ?? this.todayIsoDate();
    const end = targetDates[targetDates.length - 1] ?? this.todayIsoDate();
    const range = Math.max(1, new Date(end).getTime() - new Date(start).getTime());

    return goals.map((goal) => ({
      goal,
      progressPercent: Math.min(Math.max(goal.progressPercent, 0), 100),
      targetPosition: goal.targetDate
        ? ((new Date(goal.targetDate).getTime() - new Date(start).getTime()) / range) * 100
        : null,
    }));
  });

  constructor() {
    void this.loadInitialData();
  }

  protected async loadInitialData(): Promise<void> {
    this.state.set({ ...this.state(), loading: true, error: null });

    try {
      const [goals, accounts] = await Promise.all([
        firstValueFrom(this.goalService.getGoals()),
        firstValueFrom(this.accountService.getAccounts()),
      ]);

      this.accounts.set(accounts);
      this.state.set({ ...this.state(), goals, loading: false, error: null });
      this.selectedGoalId.set(goals[0]?.id ?? null);
      await this.loadAllContributions(goals);
    } catch {
      this.state.set({ ...this.state(), loading: false, error: 'Unable to load goals.' });
    }
  }

  protected async selectGoal(goal: Goal): Promise<void> {
    this.selectedGoalId.set(goal.id);
    this.activePanel.set('none');
  }

  protected openCreateForm(): void {
    this.selectedGoalId.set(null);
    this.activePanel.set('create');
  }

  protected selectView(view: GoalsView): void {
    this.view.set(view);
  }

  protected selectPriorityFilter(filter: PriorityFilter): void {
    this.priorityFilter.set(filter);
  }

  protected goalColor(goal: Goal): string {
    return normalizeGoalColor(goal.color);
  }

  protected goalSoftColor(goal: Goal, opacity = 14): string {
    return softColor(this.goalColor(goal), opacity);
  }

  protected monthlyAmount(goal: Goal): number {
    return goal.autoSaveEnabled ? goal.plannedMonthlyContribution : goal.monthlyRate;
  }

  protected remainingAmount(goal: Goal): number {
    return Math.max(goal.targetAmount - goal.currentAmount, 0);
  }

  protected monthsLeft(goal: Goal): number | null {
    const monthly = this.monthlyAmount(goal);
    return monthly > 0 ? Math.ceil(this.remainingAmount(goal) / monthly) : null;
  }

  protected monthsToTarget(goal: Goal): number | null {
    if (!goal.targetDate) {
      return null;
    }

    const now = new Date();
    const target = new Date(goal.targetDate);
    return Math.max((target.getFullYear() - now.getFullYear()) * 12 + target.getMonth() - now.getMonth(), 0);
  }

  protected forecastDate(goal: Goal): Date | null {
    const monthsLeft = this.monthsLeft(goal);
    if (monthsLeft === null) {
      return null;
    }

    const forecast = new Date();
    forecast.setMonth(forecast.getMonth() + monthsLeft);
    return forecast;
  }

  protected goalStreak(goal: Goal): number {
    const monthly = this.monthlyAmount(goal);
    if (monthly <= 0) {
      return 0;
    }

    return Math.max(1, Math.min(24, Math.round(goal.currentAmount / monthly)));
  }

  protected priorityClass(priority: string): string {
    return `goals__priority goals__priority--${priority.toLowerCase()}`;
  }

  protected progressRingStyle(goal: Goal): string {
    const progress = Math.min(Math.max(goal.progressPercent, 0), 100);
    return `conic-gradient(${this.goalColor(goal)} 0% ${progress}%, var(--ink-100) ${progress}% 100%)`;
  }

  protected openEditForm(): void {
    this.activePanel.set('edit');
  }

  protected openContributionForm(): void {
    this.activePanel.set('contribution');
  }

  protected closePanel(): void {
    this.activePanel.set('none');
  }

  protected async saveGoal(payload: GoalPayload): Promise<void> {
    this.state.set({ ...this.state(), saving: true, error: null });
    const selectedGoal = this.selectedGoal();

    try {
      const savedGoal =
        this.activePanel() === 'edit' && selectedGoal
          ? await firstValueFrom(this.goalService.updateGoal(selectedGoal.id, payload))
          : await firstValueFrom(this.goalService.createGoal(payload));
      this.upsertGoal(savedGoal);
      this.selectedGoalId.set(savedGoal.id);
      this.activePanel.set('none');
      await this.loadAllContributions();
      this.state.set({ ...this.state(), saving: false, error: null });
    } catch {
      this.state.set({ ...this.state(), saving: false, error: 'Unable to save goal.' });
    }
  }

  protected async addContribution(payload: GoalContributionPayload): Promise<void> {
    const selectedGoal = this.selectedGoal();
    if (!selectedGoal) {
      return;
    }

    this.state.set({ ...this.state(), saving: true, error: null });
    try {
      const updatedGoal = await firstValueFrom(
        this.goalService.addContribution(selectedGoal.id, payload),
      );
      this.upsertGoal(updatedGoal);
      this.activePanel.set('none');
      await this.loadAllContributions();
      this.state.set({ ...this.state(), saving: false, error: null });
    } catch {
      this.state.set({ ...this.state(), saving: false, error: 'Unable to add contribution.' });
    }
  }

  protected async archiveGoal(goal: Goal): Promise<void> {
    this.state.set({ ...this.state(), saving: true, error: null });
    try {
      await firstValueFrom(this.goalService.archiveGoal(goal.id));
      const goals = this.state().goals.filter((item) => item.id !== goal.id);
      this.state.set({ ...this.state(), goals, saving: false, contributions: [] });
      this.selectedGoalId.set(goals[0]?.id ?? null);
      await this.loadAllContributions(goals);
    } catch {
      this.state.set({ ...this.state(), saving: false, error: 'Unable to archive goal.' });
    }
  }

  private async loadAllContributions(goals = this.activeGoals()): Promise<void> {
    if (goals.length === 0) {
      this.state.set({ ...this.state(), contributions: [] });
      return;
    }

    const contributions = await Promise.all(
      goals.map((goal) => firstValueFrom(this.goalService.getContributions(goal.id))),
    );
    const sortedContributions = contributions
      .flat()
      .sort((left, right) => new Date(right.contributedAt).getTime() - new Date(left.contributedAt).getTime());

    this.state.set({ ...this.state(), contributions: sortedContributions });
  }

  private upsertGoal(goal: Goal): void {
    const goals = this.state().goals.filter((item) => item.id !== goal.id);
    goals.push(goal);
    goals.sort((left, right) => this.compareGoals(left, right));
    this.state.set({ ...this.state(), goals });
  }

  private compareGoals(left: Goal, right: Goal): number {
    if (left.targetDate && right.targetDate) {
      return left.targetDate.localeCompare(right.targetDate);
    }

    if (left.targetDate) {
      return -1;
    }

    if (right.targetDate) {
      return 1;
    }

    return left.id - right.id;
  }

  private todayIsoDate(): string {
    return new Date().toISOString().slice(0, 10);
  }
}

function formatEuro(amount: number): string {
  return `€${Math.round(amount).toLocaleString('fr-FR')}`;
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

function softColor(color: string, opacity: number): string {
  const normalized = color.startsWith('#') ? color.slice(1) : color;

  if (normalized.length !== 6) {
    return 'var(--lavender-50)';
  }

  const alpha = Math.round((opacity / 100) * 255)
    .toString(16)
    .padStart(2, '0');

  return `#${normalized}${alpha}`;
}

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
import { GoalDetailComponent } from './goal-detail/goal-detail.component';
import { GoalFormComponent } from './goal-form/goal-form.component';
import { GoalListComponent } from './goal-list/goal-list.component';
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

interface TimelineGoal {
  goal: Goal;
  progressPercent: number;
  targetPosition: number | null;
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
    GoalDetailComponent,
    GoalFormComponent,
    GoalListComponent,
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

  protected readonly selectedGoal = computed(() => {
    const selectedGoalId = this.selectedGoalId();
    return this.state().goals.find((goal) => goal.id === selectedGoalId) ?? null;
  });

  protected readonly activeGoals = computed(() => this.state().goals);
  protected readonly onTrackGoals = computed(() =>
    this.state().goals.filter((goal) => goal.projectedCompletionDate && goal.targetDate
      ? goal.projectedCompletionDate <= goal.targetDate
      : goal.progressPercent >= 40),
  );
  protected readonly totalSaved = computed(() =>
    this.state().goals.reduce((sum, goal) => sum + goal.currentAmount, 0),
  );
  protected readonly totalTarget = computed(() =>
    this.state().goals.reduce((sum, goal) => sum + goal.targetAmount, 0),
  );
  protected readonly monthlyPace = computed(() =>
    this.state().goals.reduce(
      (sum, goal) => sum + (goal.autoSaveEnabled ? goal.plannedMonthlyContribution : goal.monthlyRate),
      0,
    ),
  );
  protected readonly overallProgress = computed(() => {
    const target = this.totalTarget();
    return target > 0 ? Math.round((this.totalSaved() / target) * 100) : 0;
  });
  protected readonly projectedYearEnd = computed(() => this.totalSaved() + this.monthlyPace() * 6);
  protected readonly averageProgress = computed(() => {
    const goals = this.state().goals;
    if (goals.length === 0) {
      return 0;
    }

    return Math.round(goals.reduce((sum, goal) => sum + goal.progressPercent, 0) / goals.length);
  });
  protected readonly timelineGoals = computed<TimelineGoal[]>(() => {
    const goals = this.state().goals;
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
      await this.loadContributions(this.selectedGoalId());
    } catch {
      this.state.set({ ...this.state(), loading: false, error: 'Unable to load goals.' });
    }
  }

  protected async selectGoal(goal: Goal): Promise<void> {
    this.selectedGoalId.set(goal.id);
    this.activePanel.set('none');
    await this.loadContributions(goal.id);
  }

  protected openCreateForm(): void {
    this.selectedGoalId.set(null);
    this.activePanel.set('create');
  }

  protected selectView(view: GoalsView): void {
    this.view.set(view);
  }

  protected goalColor(goal: Goal): string {
    return normalizeGoalColor(goal.color);
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
      await this.loadContributions(savedGoal.id);
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
      await this.loadContributions(updatedGoal.id);
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
      await this.loadContributions(this.selectedGoalId());
    } catch {
      this.state.set({ ...this.state(), saving: false, error: 'Unable to archive goal.' });
    }
  }

  private async loadContributions(goalId: number | null): Promise<void> {
    if (goalId === null) {
      this.state.set({ ...this.state(), contributions: [] });
      return;
    }

    const contributions = await firstValueFrom(this.goalService.getContributions(goalId));
    this.state.set({ ...this.state(), contributions });
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

export interface Goal {
  id: number;
  name: string;
  targetAmount: number;
  targetDate: string | null;
  icon: string;
  color: string;
  category: string;
  priority: string;
  note: string | null;
  autoSaveEnabled: boolean;
  plannedMonthlyContribution: number;
  currentAmount: number;
  progressPercent: number;
  monthlyRate: number;
  projectedCompletionDate: string | null;
  archived: boolean;
  linkedAccountId: number | null;
  linkedAccountName: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface GoalContribution {
  id: number;
  goalId: number;
  amount: number;
  note: string | null;
  contributedAt: string;
}

export interface GoalPayload {
  name: string;
  targetAmount: number;
  targetDate: string | null;
  linkedAccountId: number | null;
  icon: string;
  color: string;
  category: string;
  priority: string;
  note: string | null;
  autoSaveEnabled: boolean;
  plannedMonthlyContribution: number;
}

export interface GoalContributionPayload {
  amount: number;
  note: string | null;
  contributedAt: string | null;
}

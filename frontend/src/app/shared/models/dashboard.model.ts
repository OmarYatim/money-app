export interface DashboardSummary {
  netWorth: number;
  totalAssets: number;
  totalLiabilities: number;
  futureBalance: number;
  monthlyIncome: number;
  monthlyExpenses: number;
  dailySpending: number;
  lastSyncedAt: string | null;
}

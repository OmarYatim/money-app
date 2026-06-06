export interface SpendingByCategory {
  category: string;
  totalAmount: number;
  percentage: number;
}

export interface IncomeExpenses {
  month: string;
  totalIncome: number;
  totalExpenses: number;
  netCashFlow: number;
}

export interface TopMerchant {
  merchant: string;
  category: string;
  transactionCount: number;
  totalAmount: number;
  lastTransactionDate: string;
}

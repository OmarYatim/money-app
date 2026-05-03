import type { CategoryType } from './category.model';

export interface Transaction {
  id: number;
  accountId: number | null;
  accountName: string | null;
  date: string;
  label: string;
  wording: string | null;
  originalWording: string | null;
  value: number;
  applicationDate: string | null;
  category: CategoryType;
  categoryOverridden: boolean;
  type: string | null;
  counterpartyLabel: string | null;
  internalTransfer: boolean;
  internalTransferOverridden: boolean;
  reviewed: boolean;
  reviewedAt: string | null;
}

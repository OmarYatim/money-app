import type { CategoryType } from './category.model';

export interface Transaction {
  id: number;
  date: string;
  label: string;
  wording: string | null;
  value: number;
  category: CategoryType;
  categoryOverridden: boolean;
}

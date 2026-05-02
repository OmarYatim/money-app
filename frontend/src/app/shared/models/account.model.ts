export interface Account {
  id: number;
  connectionId: number | null;
  institutionName: string | null;
  name: string;
  type: string | null;
  accountNumberLastFour: string | null;
  balance: number;
  coming: number;
  currency: string;
  lastUpdate: string | null;
  disabled: boolean;
}

export const CATEGORY_TYPES = [
  'GROCERIES',
  'DINING',
  'TRANSPORT',
  'UTILITIES',
  'RENT',
  'HEALTH',
  'ENTERTAINMENT',
  'SHOPPING',
  'TRAVEL',
  'EDUCATION',
  'INCOME',
  'TRANSFER',
  'SAVINGS',
  'SUBSCRIPTION',
  'OTHER',
] as const;

export type CategoryType = (typeof CATEGORY_TYPES)[number];

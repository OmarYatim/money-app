import { Pipe, PipeTransform } from '@angular/core';

import type { CategoryType } from '../models/category.model';

const CATEGORY_COLOR_CLASSES: Record<CategoryType, string> = {
  GROCERIES: 'category-badge category-badge--groceries',
  DINING: 'category-badge category-badge--dining',
  TRANSPORT: 'category-badge category-badge--transport',
  UTILITIES: 'category-badge category-badge--utilities',
  RENT: 'category-badge category-badge--rent',
  HEALTH: 'category-badge category-badge--health',
  ENTERTAINMENT: 'category-badge category-badge--entertainment',
  SHOPPING: 'category-badge category-badge--shopping',
  TRAVEL: 'category-badge category-badge--travel',
  EDUCATION: 'category-badge category-badge--education',
  INCOME: 'category-badge category-badge--income',
  TRANSFER: 'category-badge category-badge--transfer',
  SAVINGS: 'category-badge category-badge--savings',
  SUBSCRIPTION: 'category-badge category-badge--subscription',
  OTHER: 'category-badge category-badge--other',
};

@Pipe({
  name: 'categoryColor',
})
export class CategoryColorPipe implements PipeTransform {
  transform(category: CategoryType | null | undefined): string {
    return category ? CATEGORY_COLOR_CLASSES[category] : CATEGORY_COLOR_CLASSES.OTHER;
  }
}

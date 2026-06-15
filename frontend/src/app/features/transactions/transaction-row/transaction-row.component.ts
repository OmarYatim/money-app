import { CurrencyPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, input, output } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';

import { LanguageService } from '../../../core/i18n/language.service';
import type { Transaction } from '../../../shared/models/transaction.model';
import { CategoryColorPipe } from '../../../shared/pipes/category-color.pipe';

@Component({
  selector: 'app-transaction-row',
  imports: [CurrencyPipe, DatePipe, MatIconModule, CategoryColorPipe, TranslatePipe],
  templateUrl: './transaction-row.component.html',
  styleUrl: './transaction-row.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TransactionRowComponent {
  private readonly languageService = inject(LanguageService);
  private readonly translate = inject(TranslateService);

  readonly transaction = input.required<Transaction>();

  readonly selected = output<Transaction>();
  readonly reviewToggled = output<Transaction>();

  protected selectTransaction(): void {
    this.selected.emit(this.transaction());
  }

  protected selectTransactionFromKeyboard(event: Event): void {
    event.preventDefault();
    this.selectTransaction();
  }

  protected toggleReviewed(event: MouseEvent): void {
    event.stopPropagation();
    this.reviewToggled.emit(this.transaction());
  }

  protected categoryLabel(category: string): string {
    this.languageService.currentLang();
    return this.translate.instant(`categories.${category.toLowerCase()}`);
  }

  protected categoryIcon(category: string): string {
    const map: Record<string, string> = {
      GROCERIES: 'local_grocery_store',
      INCOME: 'payments',
      SAVINGS: 'savings',
      DINING: 'restaurant',
      SHOPPING: 'shopping_bag',
      SUBSCRIPTION: 'subscriptions',
      TRANSPORT: 'directions_bus',
      TRAVEL: 'flight',
      TRANSFER: 'sync_alt',
      UTILITIES: 'bolt',
      RENT: 'home',
      HEALTH: 'local_hospital',
      ENTERTAINMENT: 'movie',
      EDUCATION: 'school',
      OTHER: 'more_horiz',
    };
    return map[category.toUpperCase()] ?? 'receipt';
  }

  protected categoryIconBg(category: string): string {
    const cat = category.toUpperCase();
    if (['GROCERIES', 'INCOME', 'SAVINGS'].includes(cat)) return 'rgba(44,173,106,0.14)';
    if (['DINING', 'SHOPPING', 'SUBSCRIPTION'].includes(cat)) return 'rgba(124,58,237,0.12)';
    if (['TRANSPORT', 'TRAVEL', 'TRANSFER'].includes(cat)) return 'rgba(91,95,239,0.12)';
    if (['UTILITIES', 'RENT', 'HEALTH', 'ENTERTAINMENT', 'EDUCATION'].includes(cat))
      return 'rgba(217,152,56,0.14)';
    return 'rgba(147,150,168,0.14)';
  }

  protected categoryIconColor(category: string): string {
    const cat = category.toUpperCase();
    if (['GROCERIES', 'INCOME', 'SAVINGS'].includes(cat)) return '#2cad6a';
    if (['DINING', 'SHOPPING', 'SUBSCRIPTION'].includes(cat)) return '#7c3aed';
    if (['TRANSPORT', 'TRAVEL', 'TRANSFER'].includes(cat)) return '#5b5fef';
    if (['UTILITIES', 'RENT', 'HEALTH', 'ENTERTAINMENT', 'EDUCATION'].includes(cat))
      return '#d99838';
    return '#9396a8';
  }
}

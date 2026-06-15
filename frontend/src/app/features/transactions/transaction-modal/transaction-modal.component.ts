import { CurrencyPipe, DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';

import { LanguageService } from '../../../core/i18n/language.service';
import type { CategoryType } from '../../../shared/models/category.model';
import type { Transaction } from '../../../shared/models/transaction.model';

@Component({
  selector: 'app-transaction-modal',
  imports: [CurrencyPipe, DatePipe, MatIconModule, MatProgressSpinnerModule, TranslatePipe],
  templateUrl: './transaction-modal.component.html',
  styleUrl: './transaction-modal.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TransactionModalComponent {
  private readonly languageService = inject(LanguageService);
  private readonly translate = inject(TranslateService);

  readonly transaction = input<Transaction | null>(null);
  readonly loading = input(false);
  readonly saving = input(false);
  readonly error = input<string | null>(null);
  readonly categories = input<readonly CategoryType[]>([]);

  readonly closed = output<void>();
  readonly reviewedToggled = output<void>();
  readonly categoryChanged = output<CategoryType>();
  readonly internalTransferToggled = output<void>();

  protected readonly categoryMenuOpen = signal(false);

  protected toggleCategoryMenu(): void {
    if (this.saving()) return;
    this.categoryMenuOpen.update((open) => !open);
  }

  protected selectCategory(cat: CategoryType): void {
    this.categoryMenuOpen.set(false);
    this.categoryChanged.emit(cat);
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

  protected transactionReference(transaction: Transaction): string {
    const year = new Date(transaction.date).getFullYear();
    return `NX-${String(transaction.id).padStart(6, '0')}-${year}`;
  }

  protected transactionMethod(transaction: Transaction): string {
    const type = transaction.type?.toLowerCase() ?? '';
    if (type.includes('card')) return this.t('transactions.method.card');
    if (type.includes('transfer')) return this.t('transactions.method.bankTransfer');
    if (type.includes('debit')) return this.t('transactions.method.sepaDebit');
    return transaction.type ? this.categoryLabel(transaction.type) : this.t('transactions.method.bankTransaction');
  }

  protected transactionDirection(transaction: Transaction): string {
    return transaction.value >= 0
      ? this.t('transactions.direction.credit')
      : this.t('transactions.direction.debit');
  }

  private t(key: string): string {
    this.languageService.currentLang();
    return this.translate.instant(key);
  }
}

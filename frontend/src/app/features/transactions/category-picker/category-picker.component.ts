import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MAT_BOTTOM_SHEET_DATA, MatBottomSheetRef } from '@angular/material/bottom-sheet';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';

import { LanguageService } from '../../../core/i18n/language.service';
import { CATEGORY_TYPES, type CategoryType } from '../../../shared/models/category.model';
import { CategoryColorPipe } from '../../../shared/pipes/category-color.pipe';

interface CategoryPickerData {
  selectedCategory: CategoryType;
}

@Component({
  selector: 'app-category-picker',
  imports: [MatIconModule, MatListModule, CategoryColorPipe, TranslatePipe],
  templateUrl: './category-picker.component.html',
  styleUrl: './category-picker.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CategoryPickerComponent {
  private readonly bottomSheetRef = inject<MatBottomSheetRef<CategoryPickerComponent>>(MatBottomSheetRef);
  private readonly languageService = inject(LanguageService);
  private readonly translate = inject(TranslateService);
  protected readonly data = inject<CategoryPickerData>(MAT_BOTTOM_SHEET_DATA);
  protected readonly categories = CATEGORY_TYPES;

  protected selectCategory(category: CategoryType): void {
    this.bottomSheetRef.dismiss(category);
  }

  protected categoryLabel(category: CategoryType): string {
    this.languageService.currentLang();
    return this.translate.instant(`categories.${category.toLowerCase()}`);
  }
}

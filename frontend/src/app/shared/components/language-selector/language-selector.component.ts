import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';

import { LanguageService } from '../../../core/i18n/language.service';

@Component({
  selector: 'app-language-selector',
  imports: [TranslatePipe],
  templateUrl: './language-selector.component.html',
  styleUrl: './language-selector.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LanguageSelectorComponent {
  protected readonly languageService = inject(LanguageService);
  protected readonly options = this.languageService.options;
  protected readonly currentLang = this.languageService.currentLang;

  protected switchLanguage(event: Event): void {
    this.languageService.switchTo((event.target as HTMLSelectElement).value);
  }
}

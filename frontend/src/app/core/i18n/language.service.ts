import { isPlatformBrowser } from '@angular/common';
import { Injectable, PLATFORM_ID, computed, inject, signal } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';

export const SUPPORTED_LANGUAGES = ['en', 'fr', 'es', 'de'] as const;
export type SupportedLanguage = (typeof SUPPORTED_LANGUAGES)[number];

export interface LanguageOption {
  readonly code: SupportedLanguage;
  readonly nameKey: string;
  readonly nativeName: string;
}

const LANGUAGE_STORAGE_KEY = 'nexioo.language';
const FALLBACK_LANGUAGE: SupportedLanguage = 'fr';

@Injectable({ providedIn: 'root' })
export class LanguageService {
  private readonly translate = inject(TranslateService);
  private readonly platformId = inject(PLATFORM_ID);
  private readonly isBrowser = isPlatformBrowser(this.platformId);
  private readonly selectedLanguage = signal<SupportedLanguage>(this.resolveInitialLanguage());

  readonly currentLang = this.selectedLanguage.asReadonly();
  readonly currentOption = computed(() =>
    this.options.find((option) => option.code === this.currentLang()) ?? this.options[1],
  );

  readonly options: readonly LanguageOption[] = [
    { code: 'en', nameKey: 'language.english', nativeName: 'English' },
    { code: 'fr', nameKey: 'language.french', nativeName: 'Français' },
    { code: 'es', nameKey: 'language.spanish', nativeName: 'Español' },
    { code: 'de', nameKey: 'language.german', nativeName: 'Deutsch' },
  ];

  constructor() {
    this.translate.addLangs([...SUPPORTED_LANGUAGES]);
    this.translate.setFallbackLang(FALLBACK_LANGUAGE);
    this.translate.use(this.currentLang());
  }

  switchTo(language: string): void {
    const nextLanguage = this.toSupportedLanguage(language);
    this.selectedLanguage.set(nextLanguage);
    if (this.isBrowser) {
      localStorage.setItem(LANGUAGE_STORAGE_KEY, nextLanguage);
    }
    this.translate.use(nextLanguage);
  }

  private resolveInitialLanguage(): SupportedLanguage {
    if (!this.isBrowser) {
      return FALLBACK_LANGUAGE;
    }

    const storedLanguage = localStorage.getItem(LANGUAGE_STORAGE_KEY);
    if (storedLanguage) {
      return this.toSupportedLanguage(storedLanguage);
    }

    return this.toSupportedLanguage(navigator.language);
  }

  private toSupportedLanguage(language: string): SupportedLanguage {
    const normalizedLanguage = language.toLowerCase().split('-')[0];
    return SUPPORTED_LANGUAGES.find((supported) => supported === normalizedLanguage) ?? FALLBACK_LANGUAGE;
  }
}

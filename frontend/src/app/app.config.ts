import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { withInterceptors, provideHttpClient } from '@angular/common/http';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideRouter } from '@angular/router';
import { provideCharts, withDefaultRegisterables } from 'ng2-charts';
import {
  provideTranslateLoader,
  provideTranslateService,
  TranslateHttpLoader,
} from '@ngx-translate/core';
import { provideTranslateHttpLoader } from '@ngx-translate/http-loader';

import { routes } from './app.routes';
import { authInterceptor } from './core/auth/auth.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideHttpClient(withInterceptors([authInterceptor])),
    provideRouter(routes),
    provideAnimationsAsync(),
    provideCharts(withDefaultRegisterables()),
    provideTranslateHttpLoader({
      prefix: '/assets/i18n/',
      suffix: '.json',
      enforceLoading: false,
      useHttpBackend: false,
      failOnError: true,
    }),
    provideTranslateService({
      fallbackLang: 'fr',
      lang: 'fr',
      loader: provideTranslateLoader(TranslateHttpLoader),
    }),
  ],
};

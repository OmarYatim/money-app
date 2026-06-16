import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatIconModule } from '@angular/material/icon';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { catchError, filter, map, of, startWith, switchMap } from 'rxjs';

import { AuthService } from './core/auth/auth.service';
import { LanguageService } from './core/i18n/language.service';
import { SseService } from './core/sse/sse.service';
import { UnreviewedTransactionCountService } from './features/transactions/unreviewed-transaction-count.service';
import { LanguageSelectorComponent } from './shared/components/language-selector/language-selector.component';

@Component({
  selector: 'app-root',
  imports: [
    LanguageSelectorComponent,
    MatIconModule,
    RouterLink,
    RouterLinkActive,
    RouterOutlet,
    TranslatePipe,
  ],
  templateUrl: './app.html',
  styleUrl: './app.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class App {
  private readonly router = inject(Router);
  private readonly authService = inject(AuthService);
  private readonly languageService = inject(LanguageService);
  private readonly sseService = inject(SseService);
  private readonly unreviewedTransactionCountService = inject(UnreviewedTransactionCountService);
  protected readonly profileMenuOpen = signal(false);
  protected readonly mfaWarningDismissed = signal(false);
  protected readonly unreviewedCount = this.unreviewedTransactionCountService.count;
  protected readonly unreviewedCountLabel = this.unreviewedTransactionCountService.label;

  protected readonly showShell = toSignal(
    this.router.events.pipe(
      filter((e): e is NavigationEnd => e instanceof NavigationEnd),
      map((e) => !e.urlAfterRedirects.startsWith('/login')),
    ),
    { initialValue: !this.router.url.startsWith('/login') },
  );

  protected readonly mfaStatus = toSignal(
    this.router.events.pipe(
      filter((e): e is NavigationEnd => e instanceof NavigationEnd),
      startWith(null),
      switchMap(() => {
        if (this.router.url.startsWith('/login') || !this.authService.isAuthenticated) {
          return of(null);
        }
        return this.authService.getMfaStatus().pipe(catchError(() => of(null)));
      }),
    ),
    { initialValue: null },
  );

  protected readonly showMfaWarning = computed(
    () =>
      this.showShell() &&
      !this.mfaWarningDismissed() &&
      this.authService.isAuthenticated &&
      this.mfaStatus()?.enabled === false,
  );

  protected readonly userInitials = computed(() => {
    const email = this.authService.currentEmail();
    if (!email) return '??';
    const local = email.split('@')[0];
    const parts = local.split(/[._-]/);
    if (parts.length >= 2) {
      return (parts[0][0] + parts[1][0]).toUpperCase();
    }
    return local.substring(0, 2).toUpperCase();
  });

  protected readonly userEmail = computed(() => this.authService.currentEmail() ?? '');
  protected readonly showSseReconnecting = computed(
    () => this.showShell() && this.sseService.reconnecting(),
  );

  protected readonly userName = computed(() => {
    const email = this.userEmail();
    if (!email) return 'Account';
    return email
      .split('@')[0]
      .split(/[._-]/)
      .filter(Boolean)
      .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
      .join(' ');
  });

  protected toggleProfileMenu(): void {
    this.profileMenuOpen.update((open) => !open);
  }

  protected closeProfileMenu(): void {
    this.profileMenuOpen.set(false);
  }

  protected dismissMfaWarning(): void {
    this.mfaWarningDismissed.set(true);
  }

  protected logout(): void {
    this.profileMenuOpen.set(false);
    this.authService.logout().subscribe();
  }
}

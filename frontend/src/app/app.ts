import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatIconModule } from '@angular/material/icon';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';

import { AuthService } from './core/auth/auth.service';

@Component({
  selector: 'app-root',
  imports: [MatIconModule, RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './app.html',
  styleUrl: './app.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class App {
  private readonly router = inject(Router);
  private readonly authService = inject(AuthService);
  protected readonly profileMenuOpen = signal(false);

  protected readonly showShell = toSignal(
    this.router.events.pipe(
      filter((e): e is NavigationEnd => e instanceof NavigationEnd),
      map((e) => !e.urlAfterRedirects.startsWith('/login')),
    ),
    { initialValue: !this.router.url.startsWith('/login') },
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

  protected logout(): void {
    this.profileMenuOpen.set(false);
    this.authService.logout().subscribe();
  }
}

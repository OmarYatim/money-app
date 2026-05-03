import { ChangeDetectionStrategy, Component, DestroyRef, inject, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar } from '@angular/material/snack-bar';

import { AccountService } from '../account.service';

@Component({
  selector: 'app-account-connect',
  imports: [MatButtonModule, MatIconModule],
  templateUrl: './account-connect.component.html',
  styleUrl: './account-connect.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AccountConnectComponent {
  readonly connected = output<void>();

  protected readonly loading = signal(false);

  private readonly accountService = inject(AccountService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly snackBar = inject(MatSnackBar);

  connect(): void {
    if (this.loading()) {
      return;
    }

    this.loading.set(true);
    this.accountService
      .connectBank()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => {
          window.location.href = response.webviewUrl;
          this.connected.emit();
        },
        error: () => {
          this.loading.set(false);
          this.snackBar.open('Unable to start bank connection.', 'Dismiss', {
            duration: 5000,
          });
        },
      });
  }
}

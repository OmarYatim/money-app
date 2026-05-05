import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatSnackBar } from '@angular/material/snack-bar';

import { AccountService } from '../account.service';

@Component({
  selector: 'app-account-connect',
  imports: [],
  templateUrl: './account-connect.component.html',
  styleUrl: './account-connect.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AccountConnectComponent {
  readonly label = input('Connect a bank');
  readonly loadingLabel = input('Connecting…');
  readonly icon = input('add_card');
  readonly variant = input<'button' | 'card'>('button');
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

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
import { TranslatePipe, TranslateService } from '@ngx-translate/core';

import { AccountService } from '../account.service';

@Component({
  selector: 'app-account-connect',
  imports: [TranslatePipe],
  templateUrl: './account-connect.component.html',
  styleUrl: './account-connect.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AccountConnectComponent {
  readonly labelKey = input('accounts.connectBank');
  readonly loadingLabelKey = input('accounts.connecting');
  readonly icon = input('add_card');
  readonly variant = input<'button' | 'card'>('button');
  readonly connected = output<void>();

  protected readonly loading = signal(false);

  private readonly accountService = inject(AccountService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly snackBar = inject(MatSnackBar);
  private readonly translate = inject(TranslateService);

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
          try {
            window.location.assign(response.webviewUrl);
            this.connected.emit();
            window.setTimeout(() => this.loading.set(false), 3000);
          } catch {
            this.loading.set(false);
            this.snackBar.open(this.t('accounts.errors.openConnection'), this.t('common.dismiss'), {
              duration: 5000,
            });
          }
        },
        error: () => {
          this.loading.set(false);
          this.snackBar.open(this.t('accounts.errors.startConnection'), this.t('common.dismiss'), {
            duration: 5000,
          });
        },
      });
  }

  private t(key: string): string {
    return this.translate.instant(key);
  }
}

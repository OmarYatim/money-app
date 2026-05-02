import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';

import { AccountConnectComponent } from '../account-connect/account-connect.component';

@Component({
  selector: 'app-account-list',
  imports: [AccountConnectComponent],
  templateUrl: './account-list.component.html',
  styleUrl: './account-list.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AccountListComponent {
  private readonly snackBar = inject(MatSnackBar);

  protected reloadAccounts(): void {
    this.snackBar.open('Accounts refreshed.', 'Dismiss', {
      duration: 3000,
    });
  }
}

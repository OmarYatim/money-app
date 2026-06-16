import { CurrencyPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { TranslatePipe } from '@ngx-translate/core';

import type { Account } from '../../../shared/models/account.model';

export interface ConnectedBankGroup {
  id: string;
  connectionId: number | null;
  institutionName: string;
  accounts: Account[];
  totalBalance: number;
  initial: string;
}

@Component({
  selector: 'app-connected-banks',
  imports: [CurrencyPipe, MatIconModule, TranslatePipe],
  templateUrl: './connected-banks.component.html',
  styleUrl: './connected-banks.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ConnectedBanksComponent {
  readonly accounts = input.required<Account[]>();
  readonly disconnectingConnectionId = input<number | null>(null);
  readonly disconnect = output<ConnectedBankGroup>();

  protected readonly bankGroups = computed(() => {
    const groups = new Map<string, ConnectedBankGroup>();
    this.accounts().forEach((account) => {
      const groupKey =
        account.connectionId === null ? `account-${account.id}` : `connection-${account.connectionId}`;
      const existing = groups.get(groupKey);
      if (existing) {
        existing.accounts.push(account);
        existing.totalBalance += account.balance;
        return;
      }

      const institutionName = account.institutionName ?? account.name;
      groups.set(groupKey, {
        id: groupKey,
        connectionId: account.connectionId,
        institutionName,
        accounts: [account],
        totalBalance: account.balance,
        initial: institutionName.trim().charAt(0).toUpperCase() || 'B',
      });
    });
    return Array.from(groups.values());
  });

  protected emitDisconnect(group: ConnectedBankGroup): void {
    if (group.connectionId === null || this.disconnectingConnectionId() !== null) {
      return;
    }

    this.disconnect.emit(group);
  }
}

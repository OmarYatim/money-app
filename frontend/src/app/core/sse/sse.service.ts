import { computed, effect, inject, Injectable, NgZone, signal } from '@angular/core';
import { Subject } from 'rxjs';

import { environment } from '../../../environments/environment';
import { AccountService } from '../../features/accounts/account.service';
import { DashboardService } from '../../features/dashboard/dashboard.service';
import { TransactionService } from '../../features/transactions/transaction.service';
import type { SseConnectionStatus, SseEventType } from '../../shared/models/sse-event.model';
import { AuthService } from '../auth/auth.service';

const MAX_RECONNECT_ATTEMPTS = 5;
const INITIAL_RECONNECT_DELAY_MS = 1000;
const MAX_RECONNECT_DELAY_MS = 16000;

@Injectable({ providedIn: 'root' })
export class SseService {
  private readonly authService = inject(AuthService);
  private readonly accountService = inject(AccountService);
  private readonly dashboardService = inject(DashboardService);
  private readonly transactionService = inject(TransactionService);
  private readonly zone = inject(NgZone);
  private readonly apiBaseUrl = environment.apiBaseUrl;
  private readonly eventsSubject = new Subject<SseEventType>();

  private eventSource: EventSource | null = null;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private reconnectAttempts = 0;
  private activeToken: string | null = null;

  readonly connectionStatus = signal<SseConnectionStatus>('idle');
  readonly reconnecting = computed(() => this.connectionStatus() === 'reconnecting');
  readonly events$ = this.eventsSubject.asObservable();

  constructor() {
    effect(() => {
      const token = this.authService.accessToken();
      queueMicrotask(() => this.applyAuthToken(token));
    });
  }

  private applyAuthToken(token: string | null): void {
    if (token === null) {
      this.disconnect();
      return;
    }

    if (token !== this.activeToken) {
      this.connect(token, false);
    }
  }

  private connect(token: string, reconnecting: boolean): void {
    this.clearReconnectTimer();
    this.closeEventSource();
    this.activeToken = token;
    this.connectionStatus.set(reconnecting ? 'reconnecting' : 'connecting');

    const source = new EventSource(this.streamUrl(token));
    this.eventSource = source;

    source.onopen = () => {
      this.zone.run(() => {
        this.reconnectAttempts = 0;
        this.connectionStatus.set('connected');
      });
    };
    source.onerror = () => {
      this.zone.run(() => this.scheduleReconnect());
    };

    source.addEventListener('CONNECTED', () => {
      this.zone.run(() => {
        this.reconnectAttempts = 0;
        this.connectionStatus.set('connected');
      });
    });
    source.addEventListener('ACCOUNTS_UPDATED', () => {
      this.zone.run(() => this.handleEvent('ACCOUNTS_UPDATED'));
    });
    source.addEventListener('TRANSACTIONS_UPDATED', () => {
      this.zone.run(() => this.handleEvent('TRANSACTIONS_UPDATED'));
    });
  }

  private handleEvent(eventType: SseEventType): void {
    this.eventsSubject.next(eventType);
    this.dashboardService.notifySummaryUpdated();

    if (eventType === 'ACCOUNTS_UPDATED') {
      this.accountService.notifyAccountsUpdated();
      return;
    }

    this.transactionService.notifyTransactionsUpdated();
  }

  private scheduleReconnect(): void {
    if (this.activeToken === null) {
      this.disconnect();
      return;
    }

    this.closeEventSource();
    if (this.reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
      this.connectionStatus.set('failed');
      return;
    }

    this.connectionStatus.set('reconnecting');
    const delay = Math.min(
      INITIAL_RECONNECT_DELAY_MS * 2 ** this.reconnectAttempts,
      MAX_RECONNECT_DELAY_MS,
    );
    this.reconnectAttempts += 1;
    const token = this.activeToken;
    this.reconnectTimer = setTimeout(() => {
      this.zone.run(() => this.connect(token, true));
    }, delay);
  }

  private disconnect(): void {
    this.activeToken = null;
    this.reconnectAttempts = 0;
    this.clearReconnectTimer();
    this.closeEventSource();
    this.connectionStatus.set('idle');
  }

  private closeEventSource(): void {
    this.eventSource?.close();
    this.eventSource = null;
  }

  private clearReconnectTimer(): void {
    if (this.reconnectTimer === null) {
      return;
    }

    clearTimeout(this.reconnectTimer);
    this.reconnectTimer = null;
  }

  private streamUrl(token: string): string {
    const baseUrl = this.apiBaseUrl || window.location.origin;
    const url = new URL('/api/stream/events', baseUrl);
    url.searchParams.set('access_token', token);
    return url.toString();
  }
}

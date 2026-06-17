import { computed, inject, Injectable, NgZone, signal } from '@angular/core';
import { Subject } from 'rxjs';

import { environment } from '../../../environments/environment';
import { AccountService } from '../../features/accounts/account.service';
import { DashboardService } from '../../features/dashboard/dashboard.service';
import { TransactionService } from '../../features/transactions/transaction.service';
import type { SseConnectionStatus, SseEventType } from '../../shared/models/sse-event.model';

const MAX_RECONNECT_ATTEMPTS = 5;
const INITIAL_RECONNECT_DELAY_MS = 1000;
const MAX_RECONNECT_DELAY_MS = 16000;
const HEARTBEAT_TIMEOUT_MS = 45000;
const JWT_EXPIRY_BUFFER_MS = 5000;

type TokenRefreshHandler = () => Promise<string | null>;

@Injectable({ providedIn: 'root' })
export class SseService {
  private readonly accountService = inject(AccountService);
  private readonly dashboardService = inject(DashboardService);
  private readonly transactionService = inject(TransactionService);
  private readonly zone = inject(NgZone);
  private readonly apiBaseUrl = environment.apiBaseUrl;
  private readonly eventsSubject = new Subject<SseEventType>();

  private eventSource: EventSource | null = null;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private heartbeatTimer: ReturnType<typeof setTimeout> | null = null;
  private reconnectAttempts = 0;
  private activeToken: string | null = null;
  private tokenRefreshHandler: TokenRefreshHandler | null = null;

  readonly connectionStatus = signal<SseConnectionStatus>('idle');
  readonly reconnecting = computed(() => this.connectionStatus() === 'reconnecting');
  readonly events$ = this.eventsSubject.asObservable();

  setTokenRefreshHandler(handler: TokenRefreshHandler): void {
    this.tokenRefreshHandler = handler;
  }

  connectWithToken(token: string): void {
    if (token === this.activeToken) {
      return;
    }

    this.connect(token, false);
  }

  disconnect(): void {
    this.activeToken = null;
    this.reconnectAttempts = 0;
    this.clearReconnectTimer();
    this.clearHeartbeatTimer();
    this.closeEventSource();
    this.connectionStatus.set('idle');
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
        this.scheduleHeartbeatTimeout();
        this.connectionStatus.set('connected');
      });
    };
    source.onerror = () => {
      this.zone.run(() => this.scheduleReconnect());
    };

    source.addEventListener('CONNECTED', () => {
      this.zone.run(() => {
        this.reconnectAttempts = 0;
        this.scheduleHeartbeatTimeout();
        this.connectionStatus.set('connected');
      });
    });
    source.addEventListener('HEARTBEAT', () => {
      this.zone.run(() => this.scheduleHeartbeatTimeout());
    });
    source.addEventListener('ACCOUNTS_UPDATED', () => {
      this.zone.run(() => this.handleEvent('ACCOUNTS_UPDATED'));
    });
    source.addEventListener('TRANSACTIONS_UPDATED', () => {
      this.zone.run(() => this.handleEvent('TRANSACTIONS_UPDATED'));
    });
  }

  private handleEvent(eventType: SseEventType): void {
    this.scheduleHeartbeatTimeout();
    this.eventsSubject.next(eventType);
    this.dashboardService.notifySummaryUpdated();

    if (eventType === 'ACCOUNTS_UPDATED') {
      this.accountService.notifyAccountsUpdated();
      return;
    }

    this.transactionService.notifyTransactionsUpdated();
  }

  private scheduleReconnect(): void {
    this.clearHeartbeatTimer();
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
      void this.reconnect(token);
    }, delay);
  }

  private async reconnect(token: string): Promise<void> {
    const nextToken = await this.refreshTokenIfExpired(token);
    this.zone.run(() => {
      if (nextToken === null || this.activeToken === null) {
        this.disconnect();
        return;
      }

      if (nextToken === this.activeToken && this.eventSource !== null) {
        return;
      }

      this.connect(nextToken, true);
    });
  }

  private async refreshTokenIfExpired(token: string): Promise<string | null> {
    if (!this.isTokenExpired(token)) {
      return token;
    }

    return this.tokenRefreshHandler ? this.tokenRefreshHandler() : null;
  }

  private isTokenExpired(token: string): boolean {
    try {
      const payload = JSON.parse(window.atob(token.split('.')[1] ?? '')) as { exp?: number };
      if (typeof payload.exp !== 'number') {
        return true;
      }

      return payload.exp * 1000 <= Date.now() + JWT_EXPIRY_BUFFER_MS;
    } catch {
      return true;
    }
  }

  private closeEventSource(): void {
    this.eventSource?.close();
    this.eventSource = null;
  }

  private scheduleHeartbeatTimeout(): void {
    this.clearHeartbeatTimer();
    if (this.activeToken === null) {
      return;
    }

    this.heartbeatTimer = setTimeout(() => {
      this.zone.run(() => this.scheduleReconnect());
    }, HEARTBEAT_TIMEOUT_MS);
  }

  private clearReconnectTimer(): void {
    if (this.reconnectTimer === null) {
      return;
    }

    clearTimeout(this.reconnectTimer);
    this.reconnectTimer = null;
  }

  private clearHeartbeatTimer(): void {
    if (this.heartbeatTimer === null) {
      return;
    }

    clearTimeout(this.heartbeatTimer);
    this.heartbeatTimer = null;
  }

  private streamUrl(token: string): string {
    const baseUrl = this.apiBaseUrl || window.location.origin;
    const url = new URL('/api/stream/events', baseUrl);
    url.searchParams.set('access_token', token);
    return url.toString();
  }
}

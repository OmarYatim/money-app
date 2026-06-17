import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { EMPTY, Observable, catchError, finalize, firstValueFrom, map, of, shareReplay, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  AuthenticatedResponse,
  LoginRequest,
  LoginResponse,
  MfaCodeRequest,
  MfaEnrolmentResponse,
  MfaStatusResponse,
  MfaValidateRequest,
  RegisterChallengeResponse,
  RegisterRequest,
  RegisterVerificationRequest,
} from '../../shared/models/auth.model';
import { SseService } from '../sse/sse.service';

const ACCESS_TOKEN_REFRESH_BUFFER_MS = 60000;
const ACCESS_TOKEN_MIN_REFRESH_DELAY_MS = 1000;

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly sseService = inject(SseService);
  private readonly apiBaseUrl = environment.apiBaseUrl;
  private refreshRequest$: Observable<AuthenticatedResponse> | null = null;
  private accessTokenRefreshTimer: ReturnType<typeof setTimeout> | null = null;

  readonly accessToken = signal<string | null>(null);
  readonly currentEmail = signal<string | null>(null);

  constructor() {
    this.sseService.setTokenRefreshHandler(() => this.refreshAccessTokenForSse());
  }

  get isAuthenticated(): boolean {
    return this.accessToken() !== null;
  }

  login(request: LoginRequest): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>(`${this.apiBaseUrl}/api/auth/login`, request, { withCredentials: true })
      .pipe(
        tap((res) => {
          if (res.status === 'authenticated') {
            this.storeTokens(res);
          }
        }),
      );
  }

  startRegistration(request: RegisterRequest): Observable<RegisterChallengeResponse> {
    return this.http.post<RegisterChallengeResponse>(
      `${this.apiBaseUrl}/api/auth/register/start`,
      request,
    );
  }

  verifyRegistration(request: RegisterVerificationRequest): Observable<AuthenticatedResponse> {
    return this.http
      .post<AuthenticatedResponse>(`${this.apiBaseUrl}/api/auth/register/verify`, request, {
        withCredentials: true,
      })
      .pipe(tap((res) => this.storeTokens(res)));
  }

  refresh(): Observable<AuthenticatedResponse> {
    this.refreshRequest$ ??= this.http
      .post<AuthenticatedResponse>(
        `${this.apiBaseUrl}/api/auth/refresh`,
        {},
        { withCredentials: true },
      )
      .pipe(
        tap((res) => this.storeTokens(res)),
        finalize(() => {
          this.refreshRequest$ = null;
        }),
        shareReplay({ bufferSize: 1, refCount: false }),
      );

    return this.refreshRequest$;
  }

  validateMfa(request: MfaValidateRequest): Observable<AuthenticatedResponse> {
    return this.http
      .post<AuthenticatedResponse>(`${this.apiBaseUrl}/api/auth/mfa/validate`, request, {
        withCredentials: true,
      })
      .pipe(tap((res) => this.storeTokens(res)));
  }

  getMfaStatus(): Observable<MfaStatusResponse> {
    return this.http.get<MfaStatusResponse>(`${this.apiBaseUrl}/api/auth/mfa/status`);
  }

  enrolMfa(): Observable<MfaEnrolmentResponse> {
    return this.http.post<MfaEnrolmentResponse>(`${this.apiBaseUrl}/api/auth/mfa/enrol`, {});
  }

  verifyMfaEnrolment(request: MfaCodeRequest): Observable<MfaStatusResponse> {
    return this.http.post<MfaStatusResponse>(
      `${this.apiBaseUrl}/api/auth/mfa/verify-enrolment`,
      request,
    );
  }

  disableMfa(request: MfaCodeRequest): Observable<MfaStatusResponse> {
    return this.http.post<MfaStatusResponse>(`${this.apiBaseUrl}/api/auth/mfa/disable`, request);
  }

  deleteAccount(): Observable<void> {
    return this.http
      .delete<void>(`${this.apiBaseUrl}/api/users/me`, { withCredentials: true })
      .pipe(tap(() => this.clearSession()));
  }

  logout(): Observable<void> {
    return this.http
      .post<void>(`${this.apiBaseUrl}/api/auth/logout`, {}, { withCredentials: true })
      .pipe(
        catchError(() => EMPTY),
        finalize(() => this.clearSession()),
      );
  }

  clearSession(): void {
    this.clearAccessTokenRefreshTimer();
    this.accessToken.set(null);
    this.currentEmail.set(null);
    this.sseService.disconnect();
    this.router.navigate(['/login']);
  }

  private storeTokens(res: AuthenticatedResponse): void {
    this.accessToken.set(res.accessToken);
    this.currentEmail.set(res.email);
    this.scheduleAccessTokenRefresh(res.accessToken);
    this.sseService.connectWithToken(res.accessToken);
  }

  private refreshAccessTokenForSse(): Promise<string | null> {
    return firstValueFrom(
      this.refresh().pipe(
        map((res) => res.accessToken),
        catchError(() => {
          this.clearSession();
          return of(null);
        }),
      ),
    );
  }

  private scheduleAccessTokenRefresh(token: string): void {
    this.clearAccessTokenRefreshTimer();

    const expiresAt = this.accessTokenExpiresAt(token);
    if (expiresAt === null) {
      return;
    }

    const refreshDelay = Math.max(
      expiresAt - Date.now() - ACCESS_TOKEN_REFRESH_BUFFER_MS,
      ACCESS_TOKEN_MIN_REFRESH_DELAY_MS,
    );
    this.accessTokenRefreshTimer = setTimeout(() => {
      void this.refreshAccessTokenBeforeExpiry();
    }, refreshDelay);
  }

  private async refreshAccessTokenBeforeExpiry(): Promise<void> {
    try {
      await firstValueFrom(this.refresh());
    } catch {
      this.clearSession();
    }
  }

  private clearAccessTokenRefreshTimer(): void {
    if (this.accessTokenRefreshTimer === null) {
      return;
    }

    clearTimeout(this.accessTokenRefreshTimer);
    this.accessTokenRefreshTimer = null;
  }

  private accessTokenExpiresAt(token: string): number | null {
    try {
      const payload = JSON.parse(window.atob(token.split('.')[1] ?? '')) as { exp?: number };
      return typeof payload.exp === 'number' ? payload.exp * 1000 : null;
    } catch {
      return null;
    }
  }
}

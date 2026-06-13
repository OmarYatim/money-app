import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { EMPTY, Observable, catchError, finalize, shareReplay, tap } from 'rxjs';
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

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly apiBaseUrl = environment.apiBaseUrl;
  private refreshRequest$: Observable<AuthenticatedResponse> | null = null;

  readonly accessToken = signal<string | null>(null);
  readonly currentEmail = signal<string | null>(null);

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

  logout(): Observable<void> {
    return this.http
      .post<void>(`${this.apiBaseUrl}/api/auth/logout`, {}, { withCredentials: true })
      .pipe(
        catchError(() => EMPTY),
        finalize(() => this.clearSession()),
      );
  }

  clearSession(): void {
    this.accessToken.set(null);
    this.currentEmail.set(null);
    this.router.navigate(['/login']);
  }

  private storeTokens(res: AuthenticatedResponse): void {
    this.accessToken.set(res.accessToken);
    this.currentEmail.set(res.email);
  }
}

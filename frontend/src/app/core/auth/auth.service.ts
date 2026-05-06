import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { EMPTY, Observable, catchError, finalize, shareReplay, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { LoginRequest, LoginResponse, RegisterRequest } from '../../shared/models/auth.model';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly apiBaseUrl = environment.apiBaseUrl;
  private refreshRequest$: Observable<LoginResponse> | null = null;

  readonly accessToken = signal<string | null>(null);
  readonly currentEmail = signal<string | null>(null);

  get isAuthenticated(): boolean {
    return this.accessToken() !== null;
  }

  login(request: LoginRequest): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>(`${this.apiBaseUrl}/api/auth/login`, request, { withCredentials: true })
      .pipe(tap((res) => this.storeTokens(res)));
  }

  register(request: RegisterRequest): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>(`${this.apiBaseUrl}/api/auth/register`, request, {
        withCredentials: true,
      })
      .pipe(tap((res) => this.storeTokens(res)));
  }

  refresh(): Observable<LoginResponse> {
    this.refreshRequest$ ??= this.http
      .post<LoginResponse>(`${this.apiBaseUrl}/api/auth/refresh`, {}, { withCredentials: true })
      .pipe(
        tap((res) => this.storeTokens(res)),
        finalize(() => {
          this.refreshRequest$ = null;
        }),
        shareReplay({ bufferSize: 1, refCount: false }),
      );

    return this.refreshRequest$;
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

  private storeTokens(res: LoginResponse): void {
    this.accessToken.set(res.accessToken);
    this.currentEmail.set(res.email);
  }
}

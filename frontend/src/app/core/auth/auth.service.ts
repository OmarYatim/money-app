import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { EMPTY, Observable, catchError, finalize, tap } from 'rxjs';
import { LoginRequest, LoginResponse, RegisterRequest } from '../../shared/models/auth.model';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  readonly accessToken = signal<string | null>(null);
  readonly currentEmail = signal<string | null>(null);

  get isAuthenticated(): boolean {
    return this.accessToken() !== null;
  }

  login(request: LoginRequest): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>('/api/auth/login', request)
      .pipe(tap((res) => this.storeTokens(res)));
  }

  register(request: RegisterRequest): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>('/api/auth/register', request)
      .pipe(tap((res) => this.storeTokens(res)));
  }

  refresh(): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>('/api/auth/refresh', {}, { withCredentials: true })
      .pipe(tap((res) => this.storeTokens(res)));
  }

  logout(): Observable<void> {
    return this.http
      .post<void>('/api/auth/logout', {}, { withCredentials: true })
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

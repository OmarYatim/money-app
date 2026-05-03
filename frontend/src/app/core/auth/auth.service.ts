import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  accessToken: string;
  email: string;
}

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
    return this.http.post<LoginResponse>('/api/auth/login', request).pipe(
      tap((res) => this.storeTokens(res)),
    );
  }

  register(request: RegisterRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>('/api/auth/register', request).pipe(
      tap((res) => this.storeTokens(res)),
    );
  }

  refresh(): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>('/api/auth/refresh', {}, { withCredentials: true })
      .pipe(tap((res) => this.storeTokens(res)));
  }

  logout(): void {
    this.http
      .post('/api/auth/logout', {}, { withCredentials: true })
      .subscribe({ error: () => {} });
    this.clearTokens();
    this.router.navigate(['/login']);
  }

  private storeTokens(res: LoginResponse): void {
    this.accessToken.set(res.accessToken);
    this.currentEmail.set(res.email);
  }

  private clearTokens(): void {
    this.accessToken.set(null);
    this.currentEmail.set(null);
  }
}

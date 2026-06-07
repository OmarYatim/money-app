import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';

type AuthMode = 'login' | 'register';
type LoginStep = 'credentials' | 'mfa';

@Component({
  selector: 'app-login',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
})
export class LoginComponent {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly mode = signal<AuthMode>('login');
  readonly step = signal<LoginStep>('credentials');
  readonly errorMessage = signal<string | null>(null);
  readonly loading = signal(false);
  private readonly mfaToken = signal<string | null>(null);
  readonly mfaEmail = signal<string | null>(null);

  readonly form = new FormGroup({
    email: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.email],
    }),
    password: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required],
    }),
  });

  readonly mfaForm = new FormGroup({
    code: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/^\d{6}$/)],
    }),
  });

  private readonly formStatus = toSignal(this.form.statusChanges, {
    initialValue: this.form.status,
  });
  private readonly mfaFormStatus = toSignal(this.mfaForm.statusChanges, {
    initialValue: this.mfaForm.status,
  });

  readonly isFormValid = computed(() => this.formStatus() === 'VALID');
  readonly isMfaFormValid = computed(() => this.mfaFormStatus() === 'VALID');
  readonly submitLabel = computed(() => {
    if (this.loading()) {
      return this.mode() === 'login' ? 'Signing in…' : 'Creating account…';
    }
    return this.mode() === 'login' ? 'Sign in' : 'Create account';
  });
  readonly mfaSubmitLabel = computed(() => (this.loading() ? 'Verifying…' : 'Verify code'));

  setMode(mode: AuthMode): void {
    this.mode.set(mode);
    this.errorMessage.set(null);
    this.step.set('credentials');
    this.mfaToken.set(null);
    this.mfaEmail.set(null);
    this.form.reset();
    this.mfaForm.reset();
  }

  toggleMode(): void {
    this.setMode(this.mode() === 'login' ? 'register' : 'login');
  }

  onSubmit(): void {
    if (this.form.invalid) return;
    this.errorMessage.set(null);
    this.loading.set(true);
    const { email, password } = this.form.getRawValue();
    const action$ =
      this.mode() === 'login'
        ? this.authService.login({ email, password })
        : this.authService.register({ email, password });

    action$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (result) => {
        this.loading.set(false);
        if (result.status === 'mfa_required') {
          this.mfaToken.set(result.mfaToken);
          this.mfaEmail.set(result.email);
          this.step.set('mfa');
          this.mfaForm.reset();
          return;
        }
        this.router.navigate(['/accounts']);
      },
      error: (err: { status: number }) => {
        this.loading.set(false);
        this.errorMessage.set(this.resolveError(err.status));
      },
    });
  }

  onSubmitMfa(): void {
    const token = this.mfaToken();
    if (this.mfaForm.invalid || token === null) return;
    this.errorMessage.set(null);
    this.loading.set(true);
    this.authService
      .validateMfa({ code: this.mfaForm.controls.code.value, mfaToken: token })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => this.router.navigate(['/accounts']),
        error: () => {
          this.loading.set(false);
          this.errorMessage.set('Invalid or expired code. Sign in again if the code keeps failing.');
        },
      });
  }

  backToCredentials(): void {
    this.step.set('credentials');
    this.mfaToken.set(null);
    this.mfaEmail.set(null);
    this.errorMessage.set(null);
    this.loading.set(false);
    this.mfaForm.reset();
  }

  private resolveError(status: number): string {
    if (this.mode() === 'login') {
      return status === 401 ? 'Invalid email or password.' : 'Login failed. Please try again.';
    }
    return status === 409
      ? 'An account with this email already exists.'
      : 'Registration failed. Please try again.';
  }
}

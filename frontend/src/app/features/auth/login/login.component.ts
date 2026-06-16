import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  signal,
} from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { AuthService } from '../../../core/auth/auth.service';
import { LanguageService } from '../../../core/i18n/language.service';
import { LanguageSelectorComponent } from '../../../shared/components/language-selector/language-selector.component';
import type {
  ApiErrorResponse,
  ValidationErrorResponse,
} from '../../../shared/models/api-error.model';

type AuthMode = 'login' | 'register';
type LoginStep = 'credentials' | 'mfa' | 'register-code';

const RATE_LIMIT_ERROR_CODE = 'RATE_LIMIT_EXCEEDED';

interface PhoneCountry {
  code: string;
  name: string;
  dialCode: string;
  digits: number[];
  hint: string;
}

@Component({
  selector: 'app-login',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [LanguageSelectorComponent, ReactiveFormsModule, TranslatePipe],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
})
export class LoginComponent {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly languageService = inject(LanguageService);
  private readonly translate = inject(TranslateService);

  readonly mode = signal<AuthMode>('login');
  readonly step = signal<LoginStep>('credentials');
  readonly errorMessage = signal<string | null>(null);
  readonly rateLimitRetrySeconds = signal<number | null>(null);
  readonly loading = signal(false);
  private readonly mfaToken = signal<string | null>(null);
  readonly mfaEmail = signal<string | null>(null);
  readonly registerEmail = signal<string | null>(null);
  private rateLimitTimer: ReturnType<typeof setInterval> | null = null;
  readonly phoneCountries: PhoneCountry[] = [
    { code: 'US', name: 'United States', dialCode: '+1', digits: [10], hint: '10 digits' },
    { code: 'CA', name: 'Canada', dialCode: '+1', digits: [10], hint: '10 digits' },
    { code: 'FR', name: 'France', dialCode: '+33', digits: [9], hint: '9 digits' },
    { code: 'GB', name: 'United Kingdom', dialCode: '+44', digits: [10], hint: '10 digits' },
    { code: 'DE', name: 'Germany', dialCode: '+49', digits: [10, 11], hint: '10 or 11 digits' },
    { code: 'ES', name: 'Spain', dialCode: '+34', digits: [9], hint: '9 digits' },
    { code: 'IT', name: 'Italy', dialCode: '+39', digits: [9, 10], hint: '9 or 10 digits' },
  ];

  readonly form = new FormGroup({
    email: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.email],
    }),
    password: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required],
    }),
    firstName: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required],
    }),
    lastName: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required],
    }),
    phoneCountry: new FormControl('US', {
      nonNullable: true,
      validators: [Validators.required],
    }),
    phoneNational: new FormControl('', {
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

  readonly registerCodeForm = new FormGroup({
    code: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/^\d{6}$/)],
    }),
  });

  private readonly formStatus = toSignal(this.form.statusChanges, {
    initialValue: this.form.status,
  });
  private readonly formValue = toSignal(this.form.valueChanges, {
    initialValue: this.form.getRawValue(),
  });
  private readonly passwordValue = toSignal(this.form.controls.password.valueChanges, {
    initialValue: this.form.controls.password.value,
  });
  private readonly phoneCountryValue = toSignal(this.form.controls.phoneCountry.valueChanges, {
    initialValue: this.form.controls.phoneCountry.value,
  });
  private readonly phoneNationalValue = toSignal(this.form.controls.phoneNational.valueChanges, {
    initialValue: this.form.controls.phoneNational.value,
  });
  private readonly mfaFormStatus = toSignal(this.mfaForm.statusChanges, {
    initialValue: this.mfaForm.status,
  });
  private readonly registerCodeFormStatus = toSignal(this.registerCodeForm.statusChanges, {
    initialValue: this.registerCodeForm.status,
  });

  readonly selectedPhoneCountry = computed(
    () =>
      this.phoneCountries.find((country) => country.code === this.phoneCountryValue()) ??
      this.phoneCountries[0],
  );
  readonly normalizedPhone = computed(
    () => `${this.selectedPhoneCountry().dialCode}${this.phoneNationalDigits()}`,
  );
  readonly phoneValid = computed(() => {
    const digits = this.phoneNationalDigits();
    return digits.length > 0 && this.selectedPhoneCountry().digits.includes(digits.length);
  });
  readonly phoneHint = computed(() => {
    const country = this.selectedPhoneCountry();
    this.languageService.currentLang();
    if (this.phoneNationalDigits().length === 0) {
      return this.t('auth.phoneHint.empty', { country: country.name, hint: country.hint });
    }
    return this.phoneValid()
      ? `${country.dialCode}${this.phoneNationalDigits()}`
      : this.t('auth.phoneHint.invalid', { country: country.name, hint: country.hint });
  });
  readonly isFormValid = computed(() => {
    this.formValue();
    this.formStatus();
    if (this.mode() === 'login') {
      return this.form.controls.email.valid && this.form.controls.password.valid;
    }
    return (
      this.form.controls.email.valid &&
      this.form.controls.password.valid &&
      this.form.controls.firstName.valid &&
      this.form.controls.lastName.valid &&
      this.form.controls.phoneCountry.valid &&
      this.form.controls.phoneNational.valid &&
      this.phoneValid() &&
      this.passwordStrong()
    );
  });
  readonly isMfaFormValid = computed(() => this.mfaFormStatus() === 'VALID');
  readonly isRegisterCodeFormValid = computed(() => this.registerCodeFormStatus() === 'VALID');
  readonly loginRateLimited = computed(
    () => this.mode() === 'login' && this.rateLimitRetrySeconds() !== null,
  );
  readonly passwordScore = computed(() => {
    const password = this.passwordValue();
    return [
      password.length >= 12,
      /[a-z]/.test(password),
      /[A-Z]/.test(password),
      /[0-9]/.test(password),
      /[^A-Za-z0-9]/.test(password),
    ].filter(Boolean).length;
  });
  readonly passwordHint = computed(() =>
    this.mode() === 'register' && !this.passwordStrong()
      ? this.t('auth.passwordHint')
      : null,
  );
  readonly submitLabel = computed(() => {
    this.languageService.currentLang();
    if (this.loading()) {
      return this.mode() === 'login' ? this.t('auth.signingIn') : this.t('auth.creatingAccount');
    }
    if (this.loginRateLimited()) {
      return this.t('auth.tryAgainSoon');
    }
    return this.mode() === 'login' ? this.t('auth.login') : this.t('auth.register');
  });
  readonly mfaSubmitLabel = computed(() => (this.loading() ? this.t('auth.verifying') : this.t('auth.verifyCode')));
  readonly registerCodeSubmitLabel = computed(() =>
    this.loading() ? this.t('auth.confirming') : this.t('auth.confirmAccount'),
  );

  constructor() {
    this.destroyRef.onDestroy(() => this.clearRateLimitCountdown());
  }

  setMode(mode: AuthMode): void {
    this.mode.set(mode);
    this.errorMessage.set(null);
    this.clearRateLimitCountdown();
    this.step.set('credentials');
    this.mfaToken.set(null);
    this.mfaEmail.set(null);
    this.registerEmail.set(null);
    this.form.reset();
    this.mfaForm.reset();
    this.registerCodeForm.reset();
  }

  toggleMode(): void {
    this.setMode(this.mode() === 'login' ? 'register' : 'login');
  }

  onSubmit(): void {
    if (this.loginRateLimited()) {
      return;
    }
    if (!this.isFormValid()) {
      this.form.markAllAsTouched();
      this.errorMessage.set(this.resolveValidationError());
      return;
    }
    this.errorMessage.set(null);
    this.clearRateLimitCountdown();
    this.loading.set(true);
    const { email, password } = this.form.getRawValue();
    if (this.mode() === 'register') {
      const { firstName, lastName } = this.form.getRawValue();
      this.authService
        .startRegistration({
          email,
          password,
          firstName,
          lastName,
          phone: this.normalizedPhone(),
        })
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({
          next: (result) => {
            this.loading.set(false);
            this.registerEmail.set(result.email);
            this.step.set('register-code');
            this.registerCodeForm.reset();
          },
          error: (err: HttpErrorResponse) => {
            this.loading.set(false);
            this.errorMessage.set(this.resolveError(err));
          },
        });
      return;
    }

    this.authService.login({ email, password }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
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
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.errorMessage.set(this.resolveError(err));
      },
    });
  }

  onSubmitRegisterCode(): void {
    const email = this.registerEmail();
    if (this.registerCodeForm.invalid || email === null) {
      this.registerCodeForm.markAllAsTouched();
      return;
    }
    this.errorMessage.set(null);
    this.loading.set(true);
    this.authService
      .verifyRegistration({ email, code: this.registerCodeForm.controls.code.value })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => this.router.navigate(['/accounts']),
        error: () => {
          this.loading.set(false);
          this.errorMessage.set(this.t('auth.errors.invalidRegisterCode'));
        },
      });
  }

  onSubmitMfa(): void {
    const token = this.mfaToken();
    if (this.mfaForm.invalid || token === null) {
      this.mfaForm.markAllAsTouched();
      return;
    }
    this.errorMessage.set(null);
    this.loading.set(true);
    this.authService
      .validateMfa({ code: this.mfaForm.controls.code.value, mfaToken: token })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => this.router.navigate(['/accounts']),
        error: () => {
          this.loading.set(false);
          this.errorMessage.set(this.t('auth.errors.invalidMfaCode'));
        },
      });
  }

  backToCredentials(): void {
    this.step.set('credentials');
    this.mfaToken.set(null);
    this.mfaEmail.set(null);
    this.registerEmail.set(null);
    this.errorMessage.set(null);
    this.loading.set(false);
    this.mfaForm.reset();
    this.registerCodeForm.reset();
  }

  private resolveError(error: HttpErrorResponse): string {
    if (this.mode() === 'login') {
      if (this.isRateLimitError(error)) {
        return this.resolveRateLimitError(error);
      }
      return error.status === 401 ? this.t('auth.errors.invalidCredentials') : this.t('auth.errors.loginFailed');
    }
    if (error.status === 400) {
      return this.resolveRegistrationValidationError(error.error);
    }
    if (this.isApiErrorResponse(error.error) && error.error.message) {
      return this.formatValidationMessage(error.error.message);
    }
    return error.status === 409
      ? this.t('auth.errors.accountExists')
      : this.t('auth.errors.registrationFailed');
  }

  private resolveRegistrationValidationError(errorBody: unknown): string {
    if (this.isValidationErrorResponse(errorBody)) {
      const fields = errorBody.fields;
      const message = fields['password'] ?? fields['registerRequest'] ?? Object.values(fields)[0];
      if (message) {
        return this.formatValidationMessage(message);
      }
    }
    return this.t('auth.errors.checkSignupFields');
  }

  private isValidationErrorResponse(errorBody: unknown): errorBody is ValidationErrorResponse {
    return (
      typeof errorBody === 'object' &&
      errorBody !== null &&
      'code' in errorBody &&
      'fields' in errorBody &&
      errorBody.code === 'VALIDATION_ERROR' &&
      typeof errorBody.fields === 'object' &&
      errorBody.fields !== null
    );
  }

  private isApiErrorResponse(errorBody: unknown): errorBody is ApiErrorResponse {
    return (
      typeof errorBody === 'object' &&
      errorBody !== null &&
      'code' in errorBody &&
      typeof errorBody.code === 'string'
    );
  }

  private isRateLimitError(error: HttpErrorResponse): boolean {
    return (
      error.status === 429 &&
      this.isApiErrorResponse(error.error) &&
      error.error.code === RATE_LIMIT_ERROR_CODE
    );
  }

  private resolveRateLimitError(error: HttpErrorResponse): string {
    const retrySeconds = this.retryAfterSeconds(error);
    if (retrySeconds !== null) {
      this.startRateLimitCountdown(retrySeconds);
      return this.t('auth.errors.tooManyRequestsRetry', { time: this.formatRetryAfter(retrySeconds) });
    }
    this.clearRateLimitCountdown();
    return this.isApiErrorResponse(error.error) && error.error.message
      ? this.formatValidationMessage(error.error.message)
      : this.t('auth.errors.tooManyRequestsLater');
  }

  private retryAfterSeconds(error: HttpErrorResponse): number | null {
    const retryAfter = error.headers.get('Retry-After');
    if (retryAfter === null) {
      return null;
    }
    const retrySeconds = Number.parseInt(retryAfter, 10);
    return Number.isFinite(retrySeconds) && retrySeconds > 0 ? retrySeconds : null;
  }

  private startRateLimitCountdown(seconds: number): void {
    this.clearRateLimitCountdown();
    this.rateLimitRetrySeconds.set(seconds);
    this.rateLimitTimer = setInterval(() => {
      const remaining = this.rateLimitRetrySeconds();
      if (remaining === null || remaining <= 1) {
        this.clearRateLimitCountdown();
        this.errorMessage.set(null);
        return;
      }
      const nextRemaining = remaining - 1;
      this.rateLimitRetrySeconds.set(nextRemaining);
      this.errorMessage.set(
        this.t('auth.errors.tooManyRequestsRetry', { time: this.formatRetryAfter(nextRemaining) }),
      );
    }, 1000);
  }

  private clearRateLimitCountdown(): void {
    if (this.rateLimitTimer !== null) {
      clearInterval(this.rateLimitTimer);
      this.rateLimitTimer = null;
    }
    this.rateLimitRetrySeconds.set(null);
  }

  private formatRetryAfter(seconds: number): string {
    if (seconds < 60) {
      return this.t(seconds === 1 ? 'time.second' : 'time.seconds', { count: seconds });
    }
    const minutes = Math.ceil(seconds / 60);
    return this.t(minutes === 1 ? 'time.minute' : 'time.minutes', { count: minutes });
  }

  private formatValidationMessage(message: string): string {
    return `${message.charAt(0).toUpperCase()}${message.slice(1)}.`;
  }

  private resolveValidationError(): string {
    if (this.mode() === 'login') {
      return this.t('auth.errors.enterEmailPassword');
    }
    if (!this.phoneValid()) {
      return this.t('auth.errors.enterValidPhone');
    }
    if (!this.passwordStrong()) {
      return this.t('auth.errors.useStrongerPassword');
    }
    return this.t('auth.errors.completeRequiredFields');
  }

  private passwordStrong(): boolean {
    const password = this.passwordValue();
    return password.length >= 12 && this.passwordScore() >= 4;
  }

  private phoneNationalDigits(): string {
    const digits = this.phoneNationalValue().replace(/\D/g, '');
    const dialDigits = this.selectedPhoneCountry().dialCode.replace(/\D/g, '');
    return digits.startsWith(dialDigits) ? digits.slice(dialDigits.length) : digits;
  }

  private t(key: string, params?: Record<string, unknown>): string {
    return this.translate.instant(key, params);
  }
}

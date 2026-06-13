import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';
import { PageActionsComponent } from '../../shared/components/page-actions/page-actions.component';
import type { MfaEnrolmentResponse } from '../../shared/models/auth.model';

type SettingsTab = 'profile' | 'security' | 'email' | 'privacy' | 'delete';
type RetentionPeriod = '6m' | '24m' | '60m';

interface SettingsTabItem {
  id: SettingsTab;
  label: string;
  icon: string;
  danger?: boolean;
}

interface RetentionOption {
  value: RetentionPeriod;
  label: string;
  hint: string;
}

@Component({
  selector: 'app-account-settings',
  imports: [MatIconModule, ReactiveFormsModule, RouterLink, PageActionsComponent],
  templateUrl: './account-settings.component.html',
  styleUrl: './account-settings.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AccountSettingsComponent {
  private readonly authService = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly route = inject(ActivatedRoute);

  protected readonly activeTab = signal<SettingsTab>('profile');
  protected readonly toast = signal<string | null>(null);
  protected readonly confirmDelete = signal(false);
  protected readonly showPassword = signal(false);
  protected readonly mfaEnabled = signal(false);
  protected readonly mfaLoading = signal(false);
  protected readonly mfaError = signal<string | null>(null);
  protected readonly mfaEnrolment = signal<MfaEnrolmentResponse | null>(null);
  protected readonly marketingConsent = signal(false);
  protected readonly analyticsConsent = signal(true);
  protected readonly thirdPartyConsent = signal(false);
  protected readonly profilingConsent = signal(true);
  protected readonly retention = signal<RetentionPeriod>('24m');

  protected readonly email = computed(() => this.authService.currentEmail() ?? '');
  protected readonly initials = computed(() => {
    const email = this.email();
    if (!email) return '??';
    const local = email.split('@')[0];
    const parts = local.split(/[._-]/);
    if (parts.length >= 2) return (parts[0][0] + parts[1][0]).toUpperCase();
    return local.substring(0, 2).toUpperCase();
  });

  protected readonly tabs: SettingsTabItem[] = [
    { id: 'profile', label: 'Profile', icon: 'person' },
    { id: 'security', label: 'Login & security', icon: 'lock' },
    { id: 'email', label: 'Email', icon: 'mail' },
    { id: 'privacy', label: 'Privacy (GDPR)', icon: 'shield' },
    { id: 'delete', label: 'Delete account', icon: 'logout', danger: true },
  ];

  protected readonly retentionOptions: RetentionOption[] = [
    { value: '6m', label: '6 months', hint: 'Minimum legal retention for financial records' },
    { value: '24m', label: '24 months', hint: 'Recommended for tax-year data' },
    { value: '60m', label: '5 years', hint: 'Full statutory window for audits' },
  ];

  protected readonly profileForm = new FormGroup({
    displayName: new FormControl('Omar Yatim', { nonNullable: true, validators: [Validators.required] }),
    username: new FormControl('omar.yatim', { nonNullable: true, validators: [Validators.required] }),
    phone: new FormControl('+33 6 12 34 56 78', { nonNullable: true }),
    country: new FormControl('France', { nonNullable: true }),
    currency: new FormControl('EUR', { nonNullable: true }),
  });

  protected readonly passwordForm = new FormGroup({
    currentPassword: new FormControl('', { nonNullable: true }),
    newPassword: new FormControl('', { nonNullable: true }),
    confirmPassword: new FormControl('', { nonNullable: true }),
  });

  protected readonly mfaVerifyForm = new FormGroup({
    code: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/^\d{6}$/)],
    }),
  });

  protected readonly mfaDisableForm = new FormGroup({
    code: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/^\d{6}$/)],
    }),
  });

  protected readonly emailForm = new FormGroup({
    newEmail: new FormControl('', { nonNullable: true, validators: [Validators.email] }),
    password: new FormControl('', { nonNullable: true }),
  });

  protected readonly passwordScore = computed(() => {
    const password = this.passwordForm.controls.newPassword.value;
    return [
      password.length >= 12,
      /[A-Z]/.test(password),
      /[0-9]/.test(password),
      /[^A-Za-z0-9]/.test(password),
    ].filter(Boolean).length;
  });

  constructor() {
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      const tab = params.get('tab');
      if (this.isSettingsTab(tab)) {
        this.activeTab.set(tab);
      }
    });
    this.loadMfaStatus();
  }

  protected setActiveTab(tab: SettingsTab): void {
    this.activeTab.set(tab);
  }

  protected flash(message: string): void {
    this.toast.set(message);
    window.setTimeout(() => this.toast.set(null), 2400);
  }

  protected saveProfile(): void {
    this.flash('Profile saved.');
  }

  protected updatePassword(): void {
    this.passwordForm.reset();
    this.flash('Password updated.');
  }

  protected startMfaEnrolment(): void {
    this.mfaLoading.set(true);
    this.mfaError.set(null);
    this.authService
      .enrolMfa()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => {
          this.mfaEnrolment.set(response);
          this.mfaLoading.set(false);
        },
        error: (error: HttpErrorResponse) => {
          this.mfaLoading.set(false);
          this.mfaError.set(this.resolveMfaError(error, 'Could not start MFA setup.'));
        },
      });
  }

  protected verifyMfaEnrolment(): void {
    if (this.mfaVerifyForm.invalid) return;
    this.mfaLoading.set(true);
    this.mfaError.set(null);
    this.authService
      .verifyMfaEnrolment({ code: this.mfaVerifyForm.controls.code.value })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => {
          this.mfaEnabled.set(response.enabled);
          this.mfaEnrolment.set(null);
          this.mfaVerifyForm.reset();
          this.mfaLoading.set(false);
          this.flash('Two-factor authentication enabled.');
        },
        error: (error: HttpErrorResponse) => {
          this.mfaLoading.set(false);
          this.mfaError.set(this.resolveMfaError(error, 'Invalid or expired code.'));
        },
      });
  }

  protected cancelMfaEnrolment(): void {
    this.mfaEnrolment.set(null);
    this.mfaError.set(null);
    this.mfaVerifyForm.reset();
  }

  protected disableMfa(): void {
    if (this.mfaDisableForm.invalid) return;
    this.mfaLoading.set(true);
    this.mfaError.set(null);
    this.authService
      .disableMfa({ code: this.mfaDisableForm.controls.code.value })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => {
          this.mfaEnabled.set(response.enabled);
          this.mfaDisableForm.reset();
          this.mfaLoading.set(false);
          this.flash('Two-factor authentication disabled.');
        },
        error: (error: HttpErrorResponse) => {
          this.mfaLoading.set(false);
          this.mfaError.set(this.resolveMfaError(error, 'Invalid or expired code.'));
        },
      });
  }

  protected requestEmailChange(): void {
    const email = this.emailForm.controls.newEmail.value;
    this.emailForm.reset();
    this.flash(`Verification email sent to ${email}.`);
  }

  protected requestDataExport(): void {
    this.flash('Data export queued. You will receive an email link within 24 h.');
  }

  protected openDeleteConfirmation(): void {
    this.confirmDelete.set(true);
  }

  protected cancelDelete(): void {
    this.confirmDelete.set(false);
  }

  protected confirmDeletion(): void {
    this.confirmDelete.set(false);
    this.flash('Account scheduled for deletion.');
  }

  private loadMfaStatus(): void {
    this.mfaLoading.set(true);
    this.authService
      .getMfaStatus()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => {
          this.mfaEnabled.set(response.enabled);
          this.mfaLoading.set(false);
        },
        error: (error: HttpErrorResponse) => {
          this.mfaLoading.set(false);
          this.mfaError.set(this.resolveMfaError(error, 'Could not load MFA status.'));
        },
      });
  }

  private resolveMfaError(error: HttpErrorResponse, fallback: string): string {
    if (error.status === 401) {
      return 'Invalid or expired code.';
    }
    if (error.status === 409) {
      return error.error?.message ?? fallback;
    }
    return fallback;
  }

  private isSettingsTab(tab: string | null): tab is SettingsTab {
    return this.tabs.some((item) => item.id === tab);
  }
}

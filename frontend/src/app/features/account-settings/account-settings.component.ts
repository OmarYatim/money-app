import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';

import { AuthService } from '../../core/auth/auth.service';
import { PageActionsComponent } from '../../shared/components/page-actions/page-actions.component';
import type { MfaEnrolmentResponse } from '../../shared/models/auth.model';

type SettingsTab = 'profile' | 'security' | 'email' | 'privacy' | 'delete';
type RetentionPeriod = '6m' | '24m' | '60m';

interface SettingsTabItem {
  id: SettingsTab;
  labelKey: string;
  icon: string;
  danger?: boolean;
}

interface RetentionOption {
  value: RetentionPeriod;
  labelKey: string;
  hintKey: string;
}

interface CountryOption {
  value: string;
  labelKey: string;
}

@Component({
  selector: 'app-account-settings',
  imports: [MatIconModule, ReactiveFormsModule, RouterLink, TranslatePipe, PageActionsComponent],
  templateUrl: './account-settings.component.html',
  styleUrl: './account-settings.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AccountSettingsComponent {
  private readonly authService = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly route = inject(ActivatedRoute);
  private readonly translate = inject(TranslateService);

  protected readonly activeTab = signal<SettingsTab>('profile');
  protected readonly toast = signal<string | null>(null);
  protected readonly confirmDelete = signal(false);
  protected readonly deleteLoading = signal(false);
  protected readonly deleteError = signal<string | null>(null);
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
    { id: 'profile', labelKey: 'accountSettings.tabs.profile', icon: 'person' },
    { id: 'security', labelKey: 'accountSettings.tabs.security', icon: 'lock' },
    { id: 'email', labelKey: 'accountSettings.tabs.email', icon: 'mail' },
    { id: 'privacy', labelKey: 'accountSettings.tabs.privacy', icon: 'shield' },
    { id: 'delete', labelKey: 'accountSettings.tabs.delete', icon: 'logout', danger: true },
  ];

  protected readonly retentionOptions: RetentionOption[] = [
    {
      value: '6m',
      labelKey: 'accountSettings.privacy.retention.options.6m.label',
      hintKey: 'accountSettings.privacy.retention.options.6m.hint',
    },
    {
      value: '24m',
      labelKey: 'accountSettings.privacy.retention.options.24m.label',
      hintKey: 'accountSettings.privacy.retention.options.24m.hint',
    },
    {
      value: '60m',
      labelKey: 'accountSettings.privacy.retention.options.60m.label',
      hintKey: 'accountSettings.privacy.retention.options.60m.hint',
    },
  ];

  protected readonly countryOptions: CountryOption[] = [
    { value: 'France', labelKey: 'accountSettings.profile.countries.france' },
    { value: 'Spain', labelKey: 'accountSettings.profile.countries.spain' },
    { value: 'Germany', labelKey: 'accountSettings.profile.countries.germany' },
    { value: 'Italy', labelKey: 'accountSettings.profile.countries.italy' },
    { value: 'Portugal', labelKey: 'accountSettings.profile.countries.portugal' },
    { value: 'Netherlands', labelKey: 'accountSettings.profile.countries.netherlands' },
    { value: 'Belgium', labelKey: 'accountSettings.profile.countries.belgium' },
    { value: 'Ireland', labelKey: 'accountSettings.profile.countries.ireland' },
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

  protected readonly deleteConfirmation = new FormControl(false, {
    nonNullable: true,
    validators: [Validators.requiredTrue],
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

  protected flashKey(key: string): void {
    this.flash(this.translate.instant(key));
  }

  protected flash(message: string): void {
    this.toast.set(message);
    window.setTimeout(() => this.toast.set(null), 2400);
  }

  protected saveProfile(): void {
    this.flashKey('accountSettings.messages.profileSaved');
  }

  protected updatePassword(): void {
    this.passwordForm.reset();
    this.flashKey('accountSettings.messages.passwordUpdated');
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
          this.mfaError.set(this.resolveMfaError(error, 'accountSettings.errors.mfaStart'));
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
          this.flashKey('accountSettings.messages.mfaEnabled');
        },
        error: (error: HttpErrorResponse) => {
          this.mfaLoading.set(false);
          this.mfaError.set(this.resolveMfaError(error, 'accountSettings.errors.invalidCode'));
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
          this.flashKey('accountSettings.messages.mfaDisabled');
        },
        error: (error: HttpErrorResponse) => {
          this.mfaLoading.set(false);
          this.mfaError.set(this.resolveMfaError(error, 'accountSettings.errors.invalidCode'));
        },
      });
  }

  protected requestEmailChange(): void {
    const email = this.emailForm.controls.newEmail.value;
    this.emailForm.reset();
    this.flash(this.translate.instant('accountSettings.messages.verificationSent', { email }));
  }

  protected openDeleteConfirmation(): void {
    this.deleteError.set(null);
    this.deleteConfirmation.reset(false);
    this.confirmDelete.set(true);
  }

  protected cancelDelete(): void {
    if (this.deleteLoading()) return;
    this.deleteError.set(null);
    this.deleteConfirmation.reset(false);
    this.confirmDelete.set(false);
  }

  protected confirmDeletion(): void {
    if (this.deleteConfirmation.invalid || this.deleteLoading()) return;

    this.deleteLoading.set(true);
    this.deleteError.set(null);
    this.authService
      .deleteAccount()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        error: (error: HttpErrorResponse) => {
          this.deleteLoading.set(false);
          this.deleteError.set(
            error.status === 401
              ? this.translate.instant('accountSettings.errors.deleteExpired')
              : this.translate.instant('accountSettings.errors.deleteFailed'),
          );
        },
      });
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
          this.mfaError.set(this.resolveMfaError(error, 'accountSettings.errors.mfaLoad'));
        },
      });
  }

  private resolveMfaError(error: HttpErrorResponse, fallbackKey: string): string {
    if (error.status === 401) {
      return this.translate.instant('accountSettings.errors.invalidCode');
    }
    if (error.status === 409) {
      return error.error?.message ?? this.translate.instant(fallbackKey);
    }
    return this.translate.instant(fallbackKey);
  }

  private isSettingsTab(tab: string | null): tab is SettingsTab {
    return this.tabs.some((item) => item.id === tab);
  }
}

import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';

type SettingsTab = 'profile' | 'security' | 'email' | 'privacy' | 'delete';

interface SettingsTabItem {
  id: SettingsTab;
  label: string;
  icon: string;
  danger?: boolean;
}

@Component({
  selector: 'app-account-settings',
  imports: [MatIconModule, ReactiveFormsModule, RouterLink],
  templateUrl: './account-settings.component.html',
  styleUrl: './account-settings.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AccountSettingsComponent {
  private readonly authService = inject(AuthService);

  protected readonly activeTab = signal<SettingsTab>('profile');
  protected readonly toast = signal<string | null>(null);
  protected readonly confirmDelete = signal(false);
  protected readonly showPassword = signal(false);
  protected readonly twoFactorEnabled = signal(true);
  protected readonly biometricLoginEnabled = signal(true);
  protected readonly magicLinkEnabled = signal(false);
  protected readonly marketingConsent = signal(false);
  protected readonly analyticsConsent = signal(true);
  protected readonly thirdPartyConsent = signal(false);
  protected readonly profilingConsent = signal(true);
  protected readonly retention = signal<'6m' | '24m' | '60m'>('24m');

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
}

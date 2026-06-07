export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  phone: string;
}

export interface RegisterChallengeResponse {
  email: string;
  expiresAt: string;
}

export interface RegisterVerificationRequest {
  email: string;
  code: string;
}

export interface AuthenticatedResponse {
  status: 'authenticated';
  accessToken: string;
  email: string;
  mfaToken: null;
}

export interface MfaRequiredResponse {
  status: 'mfa_required';
  accessToken: null;
  email: string;
  mfaToken: string;
}

export type LoginResponse = AuthenticatedResponse | MfaRequiredResponse;

export interface MfaValidateRequest {
  code: string;
  mfaToken: string;
}

export interface MfaStatusResponse {
  enabled: boolean;
}

export interface MfaEnrolmentResponse {
  qrCodeDataUrl: string;
  secret: string;
}

export interface MfaCodeRequest {
  code: string;
}

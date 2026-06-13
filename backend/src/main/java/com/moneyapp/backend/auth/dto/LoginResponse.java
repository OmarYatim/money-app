package com.moneyapp.backend.auth.dto;

public record LoginResponse(String status, String accessToken, String email, String mfaToken) {

  public static LoginResponse authenticated(String accessToken, String email) {
    return new LoginResponse("authenticated", accessToken, email, null);
  }

  public static LoginResponse mfaRequired(String mfaToken, String email) {
    return new LoginResponse("mfa_required", null, email, mfaToken);
  }
}

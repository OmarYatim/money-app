package com.moneyapp.backend.auth.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank @Email String email,
    @NotBlank @Size(min = 12, message = "password must be at least 12 characters") String password,
    @NotBlank @Size(max = 100) String firstName,
    @NotBlank @Size(max = 100) String lastName,
    @NotBlank
        @Size(max = 32)
        @Pattern(regexp = "^\\+?[0-9]{7,20}$", message = "phone must be valid")
        String phone) {

  @AssertTrue(message = "password must include at least 3 of uppercase, lowercase, number, symbol")
  public boolean isPasswordStrong() {
    if (password == null) {
      return true;
    }
    int score = 0;
    score += password.matches(".*[a-z].*") ? 1 : 0;
    score += password.matches(".*[A-Z].*") ? 1 : 0;
    score += password.matches(".*\\d.*") ? 1 : 0;
    score += password.matches(".*[^A-Za-z0-9].*") ? 1 : 0;
    return score >= 3;
  }
}

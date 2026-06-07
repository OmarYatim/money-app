package com.moneyapp.backend.auth.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class RegisterRequestValidationTest {

  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  void acceptsTwelveCharacterPasswordWithThreeCharacterClasses() {
    RegisterRequest request =
        new RegisterRequest(
            "person@example.com", "BAhoq/guXLKB#OOw", "Test", "User", "+15551234567");

    assertThat(validator.validate(request)).isEmpty();
  }
}

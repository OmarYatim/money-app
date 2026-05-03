package com.moneyapp.backend.banking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moneyapp.backend.banking.exception.InvalidBankConnectionStateException;
import com.moneyapp.backend.banking.repository.BankConnectionStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
    properties = {
      "powens.domain=powens.test",
      "powens.client-id=test-client-id",
      "powens.client-secret=test-client-secret",
      "powens.manage-token=test-manage-token",
      "powens.redirect-url=https://local.nexioo.me/api/bank/callback",
      "app.jwt.secret=test-secret-key-must-be-at-least-32-chars!!",
      "app.jwt.expiration-ms=900000"
    })
@ActiveProfiles("test")
class BankConnectionStateServiceTest {

  @Autowired private BankConnectionStateRepository bankConnectionStateRepository;

  @Autowired private BankConnectionStateService bankConnectionStateService;

  @BeforeEach
  void setUp() {
    bankConnectionStateRepository.deleteAll();
  }

  @Test
  void consumeMarksStateAsConsumed() {
    bankConnectionStateService.create(1L, "valid-state");

    bankConnectionStateService.consume(1L, "valid-state");

    assertThat(
            bankConnectionStateRepository.findByUserIdAndStateAndConsumedFalse(1L, "valid-state"))
        .isEmpty();
  }

  @Test
  void consumeRejectsMissingState() {
    assertThatThrownBy(() -> bankConnectionStateService.consume(1L, null))
        .isInstanceOf(InvalidBankConnectionStateException.class)
        .hasMessage("Invalid state parameter");
  }

  @Test
  void consumeRejectsMismatchedState() {
    bankConnectionStateService.create(1L, "valid-state");

    assertThatThrownBy(() -> bankConnectionStateService.consume(1L, "invalid-state"))
        .isInstanceOf(InvalidBankConnectionStateException.class)
        .hasMessage("Invalid state parameter");
  }
}

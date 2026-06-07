package com.moneyapp.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moneyapp.backend.auth.dto.MfaEnrolmentResponse;
import com.moneyapp.backend.auth.dto.RegisterRequest;
import com.moneyapp.backend.auth.entity.AppUser;
import com.moneyapp.backend.auth.repository.AppUserRepository;
import com.moneyapp.backend.auth.repository.MfaLoginTokenRepository;
import com.moneyapp.backend.auth.repository.RefreshTokenRepository;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.exceptions.CodeGenerationException;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

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
class MfaServiceTest {

  @Autowired private AuthenticationService authenticationService;
  @Autowired private MfaService mfaService;
  @Autowired private AppUserRepository appUserRepository;
  @Autowired private MfaLoginTokenRepository mfaLoginTokenRepository;
  @Autowired private RefreshTokenRepository refreshTokenRepository;

  @BeforeEach
  void setUp() {
    mfaLoginTokenRepository.deleteAll();
    refreshTokenRepository.deleteAll();
    appUserRepository.deleteAll();
  }

  @Test
  void enrolReturnsQrCodeAndStoresEncryptedSecret() {
    authenticationService.register(
        new RegisterRequest("enrol@example.com", "secret"), new MockHttpServletResponse());

    MfaEnrolmentResponse result = mfaService.enrol("enrol@example.com");

    AppUser user = appUserRepository.findByEmail("enrol@example.com").orElseThrow();
    assertThat(result.qrCodeDataUrl()).startsWith("data:image/png;base64,");
    assertThat(result.secret()).isNotBlank();
    assertThat(user.getTotpSecret()).isNotBlank().isNotEqualTo(result.secret());
    assertThat(user.isMfaEnabled()).isFalse();
  }

  @Test
  void verifyEnrolmentEnablesMfa() throws CodeGenerationException {
    authenticationService.register(
        new RegisterRequest("verify@example.com", "secret"), new MockHttpServletResponse());
    MfaEnrolmentResponse enrolment = mfaService.enrol("verify@example.com");

    mfaService.verifyEnrolment("verify@example.com", currentCode(enrolment.secret()));

    AppUser user = appUserRepository.findByEmail("verify@example.com").orElseThrow();
    assertThat(user.isMfaEnabled()).isTrue();
    assertThat(user.getTotpSecret()).isNotBlank();
  }

  @Test
  void verifyEnrolmentRejectsInvalidCode() {
    authenticationService.register(
        new RegisterRequest("bad-code@example.com", "secret"), new MockHttpServletResponse());
    mfaService.enrol("bad-code@example.com");

    assertThatThrownBy(() -> mfaService.verifyEnrolment("bad-code@example.com", "000000"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("401");
  }

  @Test
  void disableMfaClearsSecret() throws CodeGenerationException {
    authenticationService.register(
        new RegisterRequest("disable@example.com", "secret"), new MockHttpServletResponse());
    MfaEnrolmentResponse enrolment = mfaService.enrol("disable@example.com");
    mfaService.verifyEnrolment("disable@example.com", currentCode(enrolment.secret()));

    mfaService.disable("disable@example.com", currentCode(enrolment.secret()));

    AppUser user = appUserRepository.findByEmail("disable@example.com").orElseThrow();
    assertThat(user.isMfaEnabled()).isFalse();
    assertThat(user.getTotpSecret()).isNull();
  }

  private String currentCode(String secret) throws CodeGenerationException {
    return new DefaultCodeGenerator().generate(secret, Instant.now().getEpochSecond() / 30);
  }
}

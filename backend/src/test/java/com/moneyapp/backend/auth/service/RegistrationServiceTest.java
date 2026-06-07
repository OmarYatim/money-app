package com.moneyapp.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moneyapp.backend.auth.dto.LoginResponse;
import com.moneyapp.backend.auth.dto.RegisterChallengeResponse;
import com.moneyapp.backend.auth.dto.RegisterRequest;
import com.moneyapp.backend.auth.dto.RegisterVerificationRequest;
import com.moneyapp.backend.auth.entity.AppUser;
import com.moneyapp.backend.auth.repository.AppUserRepository;
import com.moneyapp.backend.auth.repository.MfaLoginTokenRepository;
import com.moneyapp.backend.auth.repository.PendingRegistrationRepository;
import com.moneyapp.backend.auth.repository.RefreshTokenRepository;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessagePreparator;
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
class RegistrationServiceTest {

  private static final Pattern CODE_PATTERN = Pattern.compile("\\b(\\d{6})\\b");

  @Autowired private RegistrationService registrationService;
  @Autowired private AppUserRepository appUserRepository;
  @Autowired private PendingRegistrationRepository pendingRegistrationRepository;
  @Autowired private MfaLoginTokenRepository mfaLoginTokenRepository;
  @Autowired private RefreshTokenRepository refreshTokenRepository;
  @Autowired private CapturingMailSender mailSender;

  @BeforeEach
  void setUp() {
    mailSender.clear();
    mfaLoginTokenRepository.deleteAll();
    refreshTokenRepository.deleteAll();
    pendingRegistrationRepository.deleteAll();
    appUserRepository.deleteAll();
  }

  @Test
  void startSendsEmailAndDoesNotCreateUser() {
    RegisterChallengeResponse result =
        registrationService.start(validRequest("Person@Example.com"));

    assertThat(result.email()).isEqualTo("person@example.com");
    assertThat(result.expiresAt()).isNotNull();
    assertThat(appUserRepository.findByEmail("person@example.com")).isEmpty();
    assertThat(pendingRegistrationRepository.findByEmail("person@example.com")).isPresent();
    SimpleMailMessage message = sentMessage();
    assertThat(message.getTo()).containsExactly("person@example.com");
    assertThat(message.getText()).containsPattern("\\b\\d{6}\\b");
  }

  @Test
  void verifyCreatesUserAndIssuesTokens() {
    registrationService.start(validRequest("person@example.com"));
    String code = extractCode(sentMessage());
    MockHttpServletResponse response = new MockHttpServletResponse();

    LoginResponse result =
        registrationService.verify(
            new RegisterVerificationRequest("person@example.com", code), response);

    AppUser user = appUserRepository.findByEmail("person@example.com").orElseThrow();
    assertThat(result.status()).isEqualTo("authenticated");
    assertThat(result.accessToken()).isNotBlank();
    assertThat(response.getCookies()[0].getName()).isEqualTo("refreshToken");
    assertThat(user.getFirstName()).isEqualTo("Omar");
    assertThat(user.getLastName()).isEqualTo("Yatim");
    assertThat(user.getPhone()).isEqualTo("+15551234567");
    assertThat(user.getEmailVerifiedAt()).isNotNull();
    assertThat(pendingRegistrationRepository.findByEmail("person@example.com")).isEmpty();
  }

  @Test
  void verifyRejectsWrongCode() {
    registrationService.start(validRequest("person@example.com"));

    assertThatThrownBy(
            () ->
                registrationService.verify(
                    new RegisterVerificationRequest("person@example.com", "000000"),
                    new MockHttpServletResponse()))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("401");
  }

  private RegisterRequest validRequest(String email) {
    return new RegisterRequest(email, "Stronger1!", "Omar", "Yatim", "+15551234567");
  }

  private SimpleMailMessage sentMessage() {
    assertThat(mailSender.messages).hasSize(1);
    return mailSender.messages.get(0);
  }

  private String extractCode(SimpleMailMessage message) {
    Matcher matcher = CODE_PATTERN.matcher(message.getText());
    assertThat(matcher.find()).isTrue();
    return matcher.group(1);
  }

  @TestConfiguration
  static class RegistrationServiceTestConfig {

    @Bean
    @Primary
    CapturingMailSender capturingMailSender() {
      return new CapturingMailSender();
    }
  }

  static class CapturingMailSender implements JavaMailSender {

    private final List<SimpleMailMessage> messages = new ArrayList<>();

    void clear() {
      messages.clear();
    }

    @Override
    public void send(SimpleMailMessage simpleMessage) throws MailException {
      messages.add(simpleMessage);
    }

    @Override
    public void send(SimpleMailMessage... simpleMessages) throws MailException {
      messages.addAll(List.of(simpleMessages));
    }

    @Override
    public MimeMessage createMimeMessage() {
      return new MimeMessage(Session.getDefaultInstance(new Properties()));
    }

    @Override
    public MimeMessage createMimeMessage(InputStream contentStream) {
      try {
        return new MimeMessage(Session.getDefaultInstance(new Properties()), contentStream);
      } catch (Exception e) {
        throw new IllegalStateException("Unable to create test MIME message", e);
      }
    }

    @Override
    public void send(MimeMessage mimeMessage) throws MailException {}

    @Override
    public void send(MimeMessage... mimeMessages) throws MailException {}

    @Override
    public void send(MimeMessagePreparator mimeMessagePreparator) throws MailException {}

    @Override
    public void send(MimeMessagePreparator... mimeMessagePreparators) throws MailException {}
  }
}

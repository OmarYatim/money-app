package com.moneyapp.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moneyapp.backend.auth.dto.LoginRequest;
import com.moneyapp.backend.auth.dto.LoginResponse;
import com.moneyapp.backend.auth.dto.RegisterRequest;
import com.moneyapp.backend.auth.repository.AppUserRepository;
import com.moneyapp.backend.auth.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
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
class AuthenticationServiceTest {

  @Autowired private AuthenticationService authenticationService;
  @Autowired private AppUserRepository appUserRepository;
  @Autowired private RefreshTokenRepository refreshTokenRepository;

  @BeforeEach
  void setUp() {
    refreshTokenRepository.deleteAll();
    appUserRepository.deleteAll();
  }

  @Test
  void registerCreatesUserAndReturnsAccessToken() {
    MockHttpServletResponse response = new MockHttpServletResponse();
    LoginResponse result =
        authenticationService.register(
            new RegisterRequest("newuser@example.com", "password123"), response);

    assertThat(result.email()).isEqualTo("newuser@example.com");
    assertThat(result.accessToken()).isNotBlank();
    assertThat(response.getCookies()).hasSize(1);
    assertThat(response.getCookies()[0].getName()).isEqualTo("refreshToken");
    assertThat(response.getCookies()[0].isHttpOnly()).isTrue();
  }

  @Test
  void registerRejectsDuplicateEmail() {
    MockHttpServletResponse response = new MockHttpServletResponse();
    authenticationService.register(new RegisterRequest("dup@example.com", "password123"), response);

    assertThatThrownBy(
            () ->
                authenticationService.register(
                    new RegisterRequest("dup@example.com", "other"), new MockHttpServletResponse()))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("409");
  }

  @Test
  void loginWithCorrectCredentialsReturnsAccessToken() {
    MockHttpServletResponse reg = new MockHttpServletResponse();
    authenticationService.register(new RegisterRequest("login@example.com", "secret"), reg);

    MockHttpServletResponse res = new MockHttpServletResponse();
    LoginResponse result =
        authenticationService.login(new LoginRequest("login@example.com", "secret"), res);

    assertThat(result.email()).isEqualTo("login@example.com");
    assertThat(result.accessToken()).isNotBlank();
    assertThat(res.getCookies()[0].getName()).isEqualTo("refreshToken");
  }

  @Test
  void loginWithWrongPasswordThrows401() {
    MockHttpServletResponse reg = new MockHttpServletResponse();
    authenticationService.register(new RegisterRequest("login2@example.com", "secret"), reg);

    assertThatThrownBy(
            () ->
                authenticationService.login(
                    new LoginRequest("login2@example.com", "wrong"), new MockHttpServletResponse()))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("401");
  }

  @Test
  void refreshIssuesNewAccessTokenAndRotatesRefreshToken() {
    MockHttpServletResponse reg = new MockHttpServletResponse();
    authenticationService.register(new RegisterRequest("refresh@example.com", "secret"), reg);
    String originalRefresh = reg.getCookies()[0].getValue();

    MockHttpServletRequest req = new MockHttpServletRequest();
    req.setCookies(new jakarta.servlet.http.Cookie("refreshToken", originalRefresh));
    MockHttpServletResponse res = new MockHttpServletResponse();
    LoginResponse result = authenticationService.refresh(req, res);

    assertThat(result.accessToken()).isNotBlank();
    assertThat(res.getCookies()[0].getValue()).isNotEqualTo(originalRefresh);
  }

  @Test
  void refreshWithInvalidTokenThrows401() {
    MockHttpServletRequest req = new MockHttpServletRequest();
    req.setCookies(new jakarta.servlet.http.Cookie("refreshToken", "not-a-real-token"));

    assertThatThrownBy(() -> authenticationService.refresh(req, new MockHttpServletResponse()))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("401");
  }

  @Test
  void logoutClearsRefreshCookie() {
    MockHttpServletResponse reg = new MockHttpServletResponse();
    authenticationService.register(new RegisterRequest("logout@example.com", "secret"), reg);
    String token = reg.getCookies()[0].getValue();

    MockHttpServletRequest req = new MockHttpServletRequest();
    req.setCookies(new jakarta.servlet.http.Cookie("refreshToken", token));
    MockHttpServletResponse res = new MockHttpServletResponse();
    authenticationService.logout(req, res);

    assertThat(res.getCookies()[0].getMaxAge()).isZero();
  }
}

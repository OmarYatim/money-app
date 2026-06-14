package com.moneyapp.backend.auth.service;

import com.moneyapp.backend.auth.dto.LoginRequest;
import com.moneyapp.backend.auth.dto.LoginResponse;
import com.moneyapp.backend.auth.entity.AppUser;
import com.moneyapp.backend.auth.entity.RefreshToken;
import com.moneyapp.backend.auth.repository.AppUserRepository;
import com.moneyapp.backend.config.AppProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

  private static final String REFRESH_COOKIE_NAME = "refreshToken";
  private static final int REFRESH_COOKIE_MAX_AGE = 30 * 24 * 60 * 60; // 30 days in seconds

  private final AppUserRepository appUserRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final RefreshTokenService refreshTokenService;
  private final MfaLoginTokenService mfaLoginTokenService;
  private final LoginFailureTracker loginFailureTracker;
  private final AppProperties appProperties;

  @Transactional
  public LoginResponse issueTokensForRegistration(AppUser user, HttpServletResponse response) {
    return issueTokens(user, response);
  }

  @Transactional
  public LoginResponse login(LoginRequest request, HttpServletResponse response) {
    loginFailureTracker.delayIfRequired(request.email());
    AppUser user =
        appUserRepository
            .findByEmail(request.email())
            .filter(u -> u.getPasswordHash() != null)
            .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
            .orElseThrow(
                () -> {
                  loginFailureTracker.recordFailure(request.email());
                  return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bad credentials");
                });
    loginFailureTracker.clearFailures(request.email());
    if (user.isMfaEnabled()) {
      String mfaToken = mfaLoginTokenService.create(user.getId());
      return LoginResponse.mfaRequired(mfaToken, user.getEmail());
    }
    refreshTokenService.revokeAllForUser(user.getId());
    return issueTokens(user, response);
  }

  @Transactional
  public LoginResponse refresh(HttpServletRequest request, HttpServletResponse response) {
    String rawToken = extractRefreshCookie(request);
    RefreshToken refreshToken =
        refreshTokenService
            .validate(rawToken)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token"));
    AppUser user =
        appUserRepository
            .findById(refreshToken.getUserId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    refreshTokenService.revokeByToken(rawToken);
    return issueTokens(user, response);
  }

  @Transactional
  public void logout(HttpServletRequest request, HttpServletResponse response) {
    try {
      String rawToken = extractRefreshCookie(request);
      refreshTokenService.revokeByToken(rawToken);
    } catch (ResponseStatusException ignored) {
      // already missing — still clear the cookie
    }
    clearRefreshCookie(response);
  }

  @Transactional
  public LoginResponse issueTokensForMfa(AppUser user, HttpServletResponse response) {
    refreshTokenService.revokeAllForUser(user.getId());
    return issueTokens(user, response);
  }

  private LoginResponse issueTokens(AppUser user, HttpServletResponse response) {
    String accessToken = jwtService.generateToken(user.getEmail());
    RefreshToken refreshToken = refreshTokenService.create(user.getId());
    setRefreshCookie(response, refreshToken.getToken());
    return LoginResponse.authenticated(accessToken, user.getEmail());
  }

  private String extractRefreshCookie(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No refresh token");
    }
    return Arrays.stream(cookies)
        .filter(c -> REFRESH_COOKIE_NAME.equals(c.getName()))
        .map(Cookie::getValue)
        .findFirst()
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No refresh token"));
  }

  private void setRefreshCookie(HttpServletResponse response, String token) {
    Cookie cookie = new Cookie(REFRESH_COOKIE_NAME, token);
    cookie.setHttpOnly(true);
    cookie.setSecure(appProperties.auth().refreshCookieSecure());
    cookie.setPath("/api/auth");
    cookie.setMaxAge(REFRESH_COOKIE_MAX_AGE);
    response.addCookie(cookie);
  }

  private void clearRefreshCookie(HttpServletResponse response) {
    Cookie cookie = new Cookie(REFRESH_COOKIE_NAME, "");
    cookie.setHttpOnly(true);
    cookie.setSecure(appProperties.auth().refreshCookieSecure());
    cookie.setPath("/api/auth");
    cookie.setMaxAge(0);
    response.addCookie(cookie);
  }
}

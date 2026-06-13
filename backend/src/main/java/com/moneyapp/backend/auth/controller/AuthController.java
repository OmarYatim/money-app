package com.moneyapp.backend.auth.controller;

import com.moneyapp.backend.auth.dto.LoginRequest;
import com.moneyapp.backend.auth.dto.LoginResponse;
import com.moneyapp.backend.auth.dto.MfaDisableRequest;
import com.moneyapp.backend.auth.dto.MfaEnrolmentResponse;
import com.moneyapp.backend.auth.dto.MfaStatusResponse;
import com.moneyapp.backend.auth.dto.MfaValidateRequest;
import com.moneyapp.backend.auth.dto.MfaVerifyEnrolmentRequest;
import com.moneyapp.backend.auth.dto.RegisterChallengeResponse;
import com.moneyapp.backend.auth.dto.RegisterRequest;
import com.moneyapp.backend.auth.dto.RegisterVerificationRequest;
import com.moneyapp.backend.auth.service.AuthenticationService;
import com.moneyapp.backend.auth.service.MfaService;
import com.moneyapp.backend.auth.service.RegistrationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthenticationService authenticationService;
  private final MfaService mfaService;
  private final RegistrationService registrationService;

  @PostMapping("/register/start")
  public RegisterChallengeResponse startRegistration(@Valid @RequestBody RegisterRequest request) {
    return registrationService.start(request);
  }

  @PostMapping("/register/verify")
  @ResponseStatus(HttpStatus.CREATED)
  public LoginResponse verifyRegistration(
      @Valid @RequestBody RegisterVerificationRequest request, HttpServletResponse response) {
    return registrationService.verify(request, response);
  }

  @PostMapping("/login")
  public LoginResponse login(
      @Valid @RequestBody LoginRequest request, HttpServletResponse response) {
    return authenticationService.login(request, response);
  }

  @PostMapping("/refresh")
  public LoginResponse refresh(HttpServletRequest request, HttpServletResponse response) {
    return authenticationService.refresh(request, response);
  }

  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout(HttpServletRequest request, HttpServletResponse response) {
    authenticationService.logout(request, response);
  }

  @GetMapping("/mfa/status")
  public MfaStatusResponse mfaStatus(Authentication authentication) {
    return mfaService.status(authentication.getName());
  }

  @PostMapping("/mfa/enrol")
  public MfaEnrolmentResponse enrolMfa(Authentication authentication) {
    return mfaService.enrol(authentication.getName());
  }

  @PostMapping("/mfa/verify-enrolment")
  public MfaStatusResponse verifyMfaEnrolment(
      Authentication authentication, @Valid @RequestBody MfaVerifyEnrolmentRequest request) {
    return mfaService.verifyEnrolment(authentication.getName(), request.code());
  }

  @PostMapping("/mfa/disable")
  public MfaStatusResponse disableMfa(
      Authentication authentication, @Valid @RequestBody MfaDisableRequest request) {
    return mfaService.disable(authentication.getName(), request.code());
  }

  @PostMapping("/mfa/validate")
  public LoginResponse validateMfa(
      @Valid @RequestBody MfaValidateRequest request, HttpServletResponse response) {
    return mfaService.validate(request.code(), request.mfaToken(), response);
  }
}

package com.moneyapp.backend.auth.service;

import com.moneyapp.backend.auth.dto.LoginResponse;
import com.moneyapp.backend.auth.dto.RegisterChallengeResponse;
import com.moneyapp.backend.auth.dto.RegisterRequest;
import com.moneyapp.backend.auth.dto.RegisterVerificationRequest;
import com.moneyapp.backend.auth.entity.AppUser;
import com.moneyapp.backend.auth.entity.PendingRegistration;
import com.moneyapp.backend.auth.repository.AppUserRepository;
import com.moneyapp.backend.auth.repository.PendingRegistrationRepository;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class RegistrationService {

  private static final int EXPIRATION_MINUTES = 10;
  private static final int MAX_ATTEMPTS = 5;

  private final AppUserRepository appUserRepository;
  private final PendingRegistrationRepository pendingRegistrationRepository;
  private final PasswordEncoder passwordEncoder;
  private final VerificationCodeService verificationCodeService;
  private final RegistrationEmailService registrationEmailService;
  private final AuthenticationService authenticationService;

  @Transactional
  public RegisterChallengeResponse start(RegisterRequest request) {
    String normalizedEmail = normalizeEmail(request.email());
    if (appUserRepository.findByEmail(normalizedEmail).isPresent()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already in use");
    }

    pendingRegistrationRepository.deleteByEmail(normalizedEmail);
    pendingRegistrationRepository.deleteByExpiresAtBefore(LocalDateTime.now());
    String code = verificationCodeService.generateCode();
    LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(EXPIRATION_MINUTES);
    pendingRegistrationRepository.save(
        PendingRegistration.builder()
            .email(normalizedEmail)
            .firstName(request.firstName().trim())
            .lastName(request.lastName().trim())
            .phone(request.phone().trim())
            .passwordHash(passwordEncoder.encode(request.password()))
            .codeHash(verificationCodeService.hash(code, normalizedEmail))
            .expiresAt(expiresAt)
            .build());
    registrationEmailService.sendConfirmationCode(normalizedEmail, code);
    return new RegisterChallengeResponse(normalizedEmail, expiresAt);
  }

  @Transactional
  public LoginResponse verify(RegisterVerificationRequest request, HttpServletResponse response) {
    String normalizedEmail = normalizeEmail(request.email());
    PendingRegistration pendingRegistration =
        pendingRegistrationRepository
            .findByEmail(normalizedEmail)
            .filter(candidate -> candidate.getExpiresAt().isAfter(LocalDateTime.now()))
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Invalid or expired code"));

    if (pendingRegistration.getAttempts() >= MAX_ATTEMPTS) {
      pendingRegistrationRepository.delete(pendingRegistration);
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired code");
    }

    if (!pendingRegistration
        .getCodeHash()
        .equals(verificationCodeService.hash(request.code(), normalizedEmail))) {
      pendingRegistration.setAttempts(pendingRegistration.getAttempts() + 1);
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired code");
    }

    if (appUserRepository.findByEmail(normalizedEmail).isPresent()) {
      pendingRegistrationRepository.delete(pendingRegistration);
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already in use");
    }

    AppUser user =
        appUserRepository.save(
            AppUser.builder()
                .email(normalizedEmail)
                .firstName(pendingRegistration.getFirstName())
                .lastName(pendingRegistration.getLastName())
                .phone(pendingRegistration.getPhone())
                .passwordHash(pendingRegistration.getPasswordHash())
                .emailVerifiedAt(LocalDateTime.now())
                .build());
    pendingRegistrationRepository.delete(pendingRegistration);
    return authenticationService.issueTokensForRegistration(user, response);
  }

  private String normalizeEmail(String email) {
    return email.trim().toLowerCase();
  }
}

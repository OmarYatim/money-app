package com.moneyapp.backend.auth.service;

import static dev.samstevens.totp.util.Utils.getDataUriForImage;

import com.moneyapp.backend.auth.dto.LoginResponse;
import com.moneyapp.backend.auth.dto.MfaEnrolmentResponse;
import com.moneyapp.backend.auth.dto.MfaStatusResponse;
import com.moneyapp.backend.auth.entity.AppUser;
import com.moneyapp.backend.auth.repository.AppUserRepository;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class MfaService {

  private static final String ISSUER = "Nexioo";
  private static final int DIGITS = 6;
  private static final int PERIOD_SECONDS = 30;

  private final AppUserRepository appUserRepository;
  private final TotpSecretCipher totpSecretCipher;
  private final AuthenticationService authenticationService;
  private final MfaLoginTokenService mfaLoginTokenService;

  @Transactional(readOnly = true)
  public MfaStatusResponse status(String email) {
    AppUser user = findUser(email);
    return new MfaStatusResponse(user.isMfaEnabled());
  }

  @Transactional
  public MfaEnrolmentResponse enrol(String email) {
    AppUser user = findUser(email);
    if (user.isMfaEnabled()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "MFA is already enabled");
    }

    SecretGenerator secretGenerator = new DefaultSecretGenerator();
    String secret = secretGenerator.generate();
    user.setTotpSecret(totpSecretCipher.encrypt(secret));
    user.setMfaEnabled(false);

    return new MfaEnrolmentResponse(generateQrCodeDataUrl(user.getEmail(), secret), secret);
  }

  @Transactional
  public MfaStatusResponse verifyEnrolment(String email, String code) {
    AppUser user = findUser(email);
    if (user.getTotpSecret() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MFA enrolment has not started");
    }

    String secret = totpSecretCipher.decrypt(user.getTotpSecret());
    if (!isValidCode(secret, code)) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired code");
    }

    user.setMfaEnabled(true);
    return new MfaStatusResponse(true);
  }

  @Transactional
  public MfaStatusResponse disable(String email, String code) {
    AppUser user = findUser(email);
    if (!user.isMfaEnabled() || user.getTotpSecret() == null) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "MFA is not enabled");
    }

    String secret = totpSecretCipher.decrypt(user.getTotpSecret());
    if (!isValidCode(secret, code)) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired code");
    }

    user.setMfaEnabled(false);
    user.setTotpSecret(null);
    return new MfaStatusResponse(false);
  }

  @Transactional
  public LoginResponse validate(String code, String mfaToken, HttpServletResponse response) {
    Long userId = mfaLoginTokenService.userIdForValidation(mfaToken);
    AppUser user =
        appUserRepository
            .findById(userId)
            .filter(AppUser::isMfaEnabled)
            .filter(candidate -> candidate.getTotpSecret() != null)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Invalid or expired code"));

    String secret = totpSecretCipher.decrypt(user.getTotpSecret());
    if (!isValidCode(secret, code)) {
      mfaLoginTokenService.recordFailedAttempt(mfaToken);
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired code");
    }

    mfaLoginTokenService.consume(mfaToken);
    return authenticationService.issueTokensForMfa(user, response);
  }

  private AppUser findUser(String email) {
    return appUserRepository
        .findByEmail(email)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
  }

  private String generateQrCodeDataUrl(String email, String secret) {
    try {
      QrData data =
          new QrData.Builder()
              .label(email)
              .secret(secret)
              .issuer(ISSUER)
              .algorithm(HashingAlgorithm.SHA1)
              .digits(DIGITS)
              .period(PERIOD_SECONDS)
              .build();
      QrGenerator generator = new ZxingPngQrGenerator();
      byte[] imageData = generator.generate(data);
      return getDataUriForImage(imageData, generator.getImageMimeType());
    } catch (QrGenerationException e) {
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "Unable to generate MFA QR code");
    }
  }

  private boolean isValidCode(String secret, String code) {
    CodeVerifier verifier =
        new DefaultCodeVerifier(
            new DefaultCodeGenerator(HashingAlgorithm.SHA1, DIGITS), new SystemTimeProvider());
    return verifier.isValidCode(secret, code);
  }
}

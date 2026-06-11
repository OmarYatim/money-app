package com.moneyapp.backend.auth.service;

import com.moneyapp.backend.config.AppProperties;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Slf4j
public class RegistrationEmailService {

  private final JavaMailSender mailSender;
  private final AppProperties appProperties;
  private final Environment environment;

  public void sendConfirmationCode(String email, String code) {
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(appProperties.mail().from());
    message.setTo(email);
    message.setSubject("Your Nexioo confirmation code");
    message.setText(
        """
        Your Nexioo confirmation code is %s.

        This code expires in 10 minutes. If you did not request it, you can ignore this email.
        """
            .formatted(code));
    try {
      log.info(
          "Sending signup confirmation email from={} to={} smtpHost={} smtpPort={} smtpUsername={} smtpUsernameSegments={} smtpAuth={} smtpStartTls={}",
          appProperties.mail().from(),
          maskEmail(email),
          mailHost(),
          mailPort(),
          maskValue(mailUsername()),
          segmentCount(mailUsername()),
          environment.getProperty("spring.mail.properties.mail.smtp.auth"),
          environment.getProperty("spring.mail.properties.mail.smtp.starttls.enable"));
      mailSender.send(message);
    } catch (MailException e) {
      log.warn(
          "Signup confirmation email send failed from={} to={} smtpHost={} smtpPort={} smtpUsername={} smtpErrorChain={}",
          appProperties.mail().from(),
          maskEmail(email),
          mailHost(),
          mailPort(),
          maskValue(mailUsername()),
          errorChain(e),
          e);
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "Email confirmation service could not send the code");
    }
  }

  private String mailHost() {
    return environment.getProperty("spring.mail.host");
  }

  private String mailPort() {
    return environment.getProperty("spring.mail.port");
  }

  private String mailUsername() {
    return environment.getProperty("spring.mail.username");
  }

  private String maskEmail(String email) {
    int at = email.indexOf('@');
    if (at <= 1) {
      return "***";
    }
    return email.charAt(0) + "***" + email.substring(at);
  }

  private String maskValue(String value) {
    if (value == null || value.isBlank()) {
      return "<empty>";
    }
    return "*** (len=" + value.length() + ")";
  }

  private int segmentCount(String value) {
    if (value == null || value.isBlank()) {
      return 0;
    }
    return value.split("\\.", -1).length;
  }

  private List<String> errorChain(Throwable throwable) {
    List<String> chain = new ArrayList<>();
    Throwable current = throwable;
    while (current != null) {
      chain.add(current.getClass().getSimpleName() + ": " + current.getMessage());
      current = current.getCause();
    }
    return chain;
  }
}

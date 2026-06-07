package com.moneyapp.backend.auth.service;

import com.moneyapp.backend.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class RegistrationEmailService {

  private final JavaMailSender mailSender;
  private final AppProperties appProperties;

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
      mailSender.send(message);
    } catch (MailException e) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "Email confirmation service is not configured");
    }
  }
}

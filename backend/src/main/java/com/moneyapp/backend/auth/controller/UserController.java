package com.moneyapp.backend.auth.controller;

import com.moneyapp.backend.auth.service.UserDeletionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

  private final UserDeletionService userDeletionService;

  @DeleteMapping("/me")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteCurrentUser(Authentication authentication) {
    userDeletionService.deleteAuthenticatedUser(authentication.getName());
  }
}

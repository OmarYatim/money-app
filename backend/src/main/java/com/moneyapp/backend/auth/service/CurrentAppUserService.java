package com.moneyapp.backend.auth.service;

import com.moneyapp.backend.auth.entity.AppUser;
import com.moneyapp.backend.auth.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class CurrentAppUserService {

  private final AppUserRepository appUserRepository;

  @Transactional(readOnly = true)
  public AppUser resolveExisting(String email) {
    return appUserRepository
        .findByEmail(email)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
  }

  @Transactional
  public AppUser resolveForWrite(String email) {
    return appUserRepository
        .findByEmail(email)
        .orElseGet(() -> appUserRepository.save(AppUser.builder().email(email).build()));
  }
}

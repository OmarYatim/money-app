package com.moneyapp.backend.auth.service;

import com.moneyapp.backend.auth.entity.AppUser;
import com.moneyapp.backend.auth.repository.AppUserRepository;
import com.moneyapp.backend.auth.repository.MfaLoginTokenRepository;
import com.moneyapp.backend.auth.repository.PendingRegistrationRepository;
import com.moneyapp.backend.auth.repository.RefreshTokenRepository;
import com.moneyapp.backend.banking.repository.AccountRepository;
import com.moneyapp.backend.banking.repository.BankConnectionStateRepository;
import com.moneyapp.backend.banking.repository.UserConnectionRepository;
import com.moneyapp.backend.banking.service.PowensClient;
import com.moneyapp.backend.goals.repository.GoalContributionRepository;
import com.moneyapp.backend.goals.repository.GoalRepository;
import com.moneyapp.backend.reports.repository.NetWorthSnapshotRepository;
import com.moneyapp.backend.sync.repository.SyncEventRepository;
import com.moneyapp.backend.transaction.repository.TransactionRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class UserDeletionService {

  private final AppUserRepository appUserRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final MfaLoginTokenRepository mfaLoginTokenRepository;
  private final PendingRegistrationRepository pendingRegistrationRepository;
  private final SyncEventRepository syncEventRepository;
  private final BankConnectionStateRepository bankConnectionStateRepository;
  private final GoalContributionRepository goalContributionRepository;
  private final GoalRepository goalRepository;
  private final NetWorthSnapshotRepository netWorthSnapshotRepository;
  private final TransactionRepository transactionRepository;
  private final AccountRepository accountRepository;
  private final UserConnectionRepository userConnectionRepository;
  private final PowensClient powensClient;

  @Transactional
  public void deleteAuthenticatedUser(String email) {
    AppUser appUser =
        appUserRepository
            .findByEmail(email)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));

    revokePowensUser(appUser);
    hardDeleteUserData(appUser);
  }

  private void revokePowensUser(AppUser appUser) {
    if (isBlank(appUser.getPowensToken()) || isBlank(appUser.getPowensUserId())) {
      return;
    }

    powensClient.deleteUser(appUser.getPowensToken(), appUser.getPowensUserId());
  }

  private void hardDeleteUserData(AppUser appUser) {
    Long userId = appUser.getId();
    appUser.setDeletedAt(LocalDateTime.now());
    appUserRepository.saveAndFlush(appUser);

    refreshTokenRepository.deleteByUserId(userId);
    mfaLoginTokenRepository.deleteByUserId(userId);
    pendingRegistrationRepository.deleteByEmail(appUser.getEmail());
    syncEventRepository.deleteByUserId(userId);
    bankConnectionStateRepository.deleteByUserId(userId);
    goalContributionRepository.deleteByGoalUserId(userId);
    goalRepository.deleteByUserId(userId);
    netWorthSnapshotRepository.deleteByUserId(userId);
    transactionRepository.deleteByUserId(userId);
    accountRepository.deleteByUserId(userId);
    userConnectionRepository.deleteByUserId(userId);
    appUserRepository.delete(appUser);
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}

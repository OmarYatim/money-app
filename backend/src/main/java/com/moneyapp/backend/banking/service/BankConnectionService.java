package com.moneyapp.backend.banking.service;

import com.moneyapp.backend.auth.entity.AppUser;
import com.moneyapp.backend.auth.service.CurrentAppUserService;
import com.moneyapp.backend.banking.dto.BankConnectResponse;
import com.moneyapp.backend.banking.dto.BankConnectionCallbackResponse;
import com.moneyapp.backend.banking.entity.Account;
import com.moneyapp.backend.banking.entity.UserConnection;
import com.moneyapp.backend.banking.repository.AccountRepository;
import com.moneyapp.backend.transaction.repository.TransactionRepository;
import com.moneyapp.backend.transaction.service.TransactionService;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BankConnectionService {

  private final PowensAuthService powensAuthService;
  private final CurrentAppUserService currentAppUserService;
  private final BankConnectionStateService bankConnectionStateService;
  private final UserConnectionService userConnectionService;
  private final AccountService accountService;
  private final AccountRepository accountRepository;
  private final TransactionService transactionService;
  private final TransactionRepository transactionRepository;
  private final ConnectionStatusService connectionStatusService;
  private final PowensClient powensClient;
  private final PowensWebviewService powensWebviewService;

  public BankConnectResponse createConnectLink(String email) {
    AppUser appUser = powensAuthService.ensurePowensUser(email);
    String temporaryCode = powensAuthService.createTemporaryWebviewCode(appUser);
    String state = UUID.randomUUID().toString();
    bankConnectionStateService.create(appUser.getId(), state);

    return BankConnectResponse.builder()
        .webviewUrl(powensWebviewService.buildConnectUrl(temporaryCode, state))
        .state(state)
        .build();
  }

  public BankConnectionCallbackResponse handleCallback(
      String connectionIds, String error, String state) {
    Long userId = bankConnectionStateService.consume(state);
    AppUser appUser = currentAppUserService.resolveExisting(userId);

    if (error != null && !error.isBlank()) {
      return BankConnectionCallbackResponse.builder()
          .status("cancelled")
          .message("Bank connection cancelled.")
          .connectionIds(List.of())
          .build();
    }

    List<Long> parsedConnectionIds = parseConnectionIds(connectionIds);
    userConnectionService.upsertActiveConnections(appUser.getId(), parsedConnectionIds);
    AccountService.AccountSyncResult syncResult = accountService.syncAccounts(appUser);
    transactionService.syncTransactions(appUser, syncResult.ibans());
    connectionStatusService.getStatus(appUser.getEmail());

    return BankConnectionCallbackResponse.builder()
        .status("connected")
        .message("Bank connection completed.")
        .connectionIds(parsedConnectionIds)
        .build();
  }

  @Transactional
  public void disconnectConnection(String email, Long connectionId, boolean deleteData) {
    AppUser appUser = currentAppUserService.resolveExisting(email);
    UserConnection userConnection =
        userConnectionService.findOwnedConnectionOrThrow(appUser.getId(), connectionId);

    powensClient.deleteConnection(appUser.getPowensToken(), connectionId);

    if (deleteData) {
      hardDeleteConnectionData(appUser.getId(), userConnection);
    } else {
      softDisableConnectionAccounts(appUser.getId(), connectionId);
    }
  }

  private void softDisableConnectionAccounts(Long userId, Long connectionId) {
    accountRepository.findByUserIdAndConnectionId(userId, connectionId).stream()
        .filter(account -> !account.isDisabled())
        .forEach(
            account -> {
              account.setDisabled(true);
              accountRepository.save(account);
            });
  }

  private void hardDeleteConnectionData(Long userId, UserConnection userConnection) {
    List<Account> accounts =
        accountRepository.findByUserIdAndConnectionId(userId, userConnection.getConnectionId());
    List<Long> accountIds = accounts.stream().map(Account::getId).toList();
    if (!accountIds.isEmpty()) {
      transactionRepository.deleteByUserIdAndAccountIdIn(userId, accountIds);
      accountRepository.deleteAll(accounts);
    }
    userConnectionService.delete(userConnection.getId());
  }

  private List<Long> parseConnectionIds(String connectionIds) {
    if (connectionIds == null || connectionIds.isBlank()) {
      return List.of();
    }

    return Arrays.stream(connectionIds.split(","))
        .map(String::trim)
        .filter(value -> !value.isBlank())
        .map(Long::parseLong)
        .toList();
  }
}

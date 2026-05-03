package com.moneyapp.backend.banking.service;

import com.moneyapp.backend.auth.entity.AppUser;
import com.moneyapp.backend.auth.service.CurrentAppUserService;
import com.moneyapp.backend.banking.dto.AccountResponse;
import com.moneyapp.backend.banking.dto.PowensAccountResponse;
import com.moneyapp.backend.banking.dto.PowensAccountsResponse;
import com.moneyapp.backend.banking.entity.Account;
import com.moneyapp.backend.banking.mapper.AccountMapper;
import com.moneyapp.backend.banking.repository.AccountRepository;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountService {

  private final CurrentAppUserService currentAppUserService;
  private final AccountRepository accountRepository;
  private final PowensClient powensClient;

  @Transactional(readOnly = true)
  public List<AccountResponse> findAccounts(String email) {
    AppUser appUser = currentAppUserService.resolveExisting(email);
    return accountRepository.findByUserIdAndDisabledFalseOrderByNameAsc(appUser.getId()).stream()
        .map(AccountMapper::toResponse)
        .toList();
  }

  public record AccountSyncResult(List<Account> accounts, Set<String> ibans) {}

  @Transactional
  public AccountSyncResult syncAccounts(AppUser appUser) {
    if (appUser == null || appUser.getId() == null || isBlank(appUser.getPowensToken())) {
      throw new IllegalStateException("Powens user identity is required before syncing accounts");
    }

    PowensAccountsResponse response = powensClient.fetchAccounts(appUser.getPowensToken());
    if (response == null || response.accounts() == null) {
      return new AccountSyncResult(List.of(), Set.of());
    }

    List<Account> syncedAccounts =
        response.accounts().stream()
            .filter(account -> account.id() != null)
            .map(account -> upsertAccount(appUser.getId(), account))
            .toList();
    disableDuplicateVisibleAccounts(appUser.getId(), syncedAccounts);

    Set<String> ibans =
        response.accounts().stream()
            .map(PowensAccountResponse::iban)
            .filter(iban -> iban != null && !iban.isBlank())
            .collect(Collectors.toSet());

    return new AccountSyncResult(syncedAccounts, ibans);
  }

  private Account upsertAccount(Long userId, PowensAccountResponse powensAccount) {
    String accountNumberLastFour = lastFour(powensAccount.iban());
    String currency = defaultCurrency(currencyCode(powensAccount.currency()));
    Account account = resolveAccount(userId, powensAccount, accountNumberLastFour, currency);

    account.setConnectionId(powensAccount.connectionId());
    account.setInstitutionName(powensAccount.institutionName());
    account.setName(powensAccount.name());
    account.setType(powensAccount.type());
    account.setExternalAccountId(powensAccount.id());
    account.setAccountNumberLastFour(accountNumberLastFour);
    account.setBalance(defaultMoney(powensAccount.balance()));
    account.setComing(defaultMoney(powensAccount.coming()));
    account.setCurrency(currency);
    account.setLastUpdate(powensAccount.lastUpdate());
    account.setDisabled(Boolean.TRUE.equals(powensAccount.disabled()));
    return accountRepository.save(account);
  }

  private Account resolveAccount(
      Long userId,
      PowensAccountResponse powensAccount,
      String accountNumberLastFour,
      String currency) {
    Optional<Account> existingByExternalId =
        accountRepository.findByUserIdAndExternalAccountId(userId, powensAccount.id());
    if (existingByExternalId.isPresent()) {
      return existingByExternalId.get();
    }

    return accountRepository
        .findFirstByUserIdAndInstitutionNameAndNameAndTypeAndAccountNumberLastFourAndCurrencyAndDisabledFalse(
            userId,
            powensAccount.institutionName(),
            powensAccount.name(),
            powensAccount.type(),
            accountNumberLastFour,
            currency)
        .orElseGet(
            () -> Account.builder().userId(userId).externalAccountId(powensAccount.id()).build());
  }

  private void disableDuplicateVisibleAccounts(Long userId, List<Account> syncedAccounts) {
    Set<AccountIdentity> seen = new HashSet<>();
    Set<Long> syncedAccountIds =
        syncedAccounts.stream().map(Account::getId).collect(Collectors.toSet());
    accountRepository.findByUserIdAndDisabledFalseOrderByNameAsc(userId).stream()
        .sorted((left, right) -> comparePreferred(left, right, syncedAccountIds))
        .forEach(
            account -> {
              if (!seen.add(AccountIdentity.from(account))) {
                account.setDisabled(true);
                accountRepository.save(account);
              }
            });
  }

  private int comparePreferred(Account left, Account right, Set<Long> syncedAccountIds) {
    boolean leftSynced = syncedAccountIds.contains(left.getId());
    boolean rightSynced = syncedAccountIds.contains(right.getId());
    if (leftSynced != rightSynced) {
      return leftSynced ? -1 : 1;
    }

    return Long.compare(nullSafeId(right), nullSafeId(left));
  }

  private long nullSafeId(Account account) {
    return account.getId() == null ? 0L : account.getId();
  }

  private String lastFour(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }

    String trimmedValue = value.trim();
    return trimmedValue.substring(Math.max(0, trimmedValue.length() - 4));
  }

  private BigDecimal defaultMoney(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }

  private String defaultCurrency(String currency) {
    return isBlank(currency) ? "EUR" : currency;
  }

  private String currencyCode(PowensAccountResponse.PowensCurrency currency) {
    return currency == null ? null : currency.id();
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private record AccountIdentity(
      String institutionName,
      String name,
      String type,
      String accountNumberLastFour,
      String currency) {

    private static AccountIdentity from(Account account) {
      return new AccountIdentity(
          account.getInstitutionName(),
          account.getName(),
          account.getType(),
          account.getAccountNumberLastFour(),
          account.getCurrency());
    }
  }
}

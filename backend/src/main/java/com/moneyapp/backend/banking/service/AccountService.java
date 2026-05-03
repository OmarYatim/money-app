package com.moneyapp.backend.banking.service;

import com.moneyapp.backend.auth.entity.AppUser;
import com.moneyapp.backend.auth.repository.AppUserRepository;
import com.moneyapp.backend.banking.dto.AccountResponse;
import com.moneyapp.backend.banking.dto.PowensAccountResponse;
import com.moneyapp.backend.banking.dto.PowensAccountsResponse;
import com.moneyapp.backend.banking.entity.Account;
import com.moneyapp.backend.banking.mapper.AccountMapper;
import com.moneyapp.backend.banking.repository.AccountRepository;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountService {

  private final AppUserRepository appUserRepository;
  private final AccountRepository accountRepository;
  private final PowensClient powensClient;

  @Transactional(readOnly = true)
  public List<AccountResponse> findAccounts(String email) {
    return appUserRepository
        .findByEmail(email)
        .map(AppUser::getId)
        .map(accountRepository::findByUserIdAndDisabledFalseOrderByNameAsc)
        .orElse(List.of())
        .stream()
        .map(AccountMapper::toResponse)
        .toList();
  }

  @Transactional
  public List<Account> syncAccounts(AppUser appUser) {
    if (appUser == null || appUser.getId() == null || isBlank(appUser.getPowensToken())) {
      throw new IllegalStateException("Powens user identity is required before syncing accounts");
    }

    PowensAccountsResponse response = powensClient.fetchAccounts(appUser.getPowensToken());
    if (response == null || response.accounts() == null) {
      return List.of();
    }

    return response.accounts().stream()
        .filter(account -> account.id() != null)
        .map(account -> upsertAccount(appUser.getId(), account))
        .toList();
  }

  private Account upsertAccount(Long userId, PowensAccountResponse powensAccount) {
    Account account =
        accountRepository
            .findByUserIdAndExternalAccountId(userId, powensAccount.id())
            .orElseGet(
                () ->
                    Account.builder().userId(userId).externalAccountId(powensAccount.id()).build());

    account.setConnectionId(powensAccount.connectionId());
    account.setInstitutionName(powensAccount.institutionName());
    account.setName(powensAccount.name());
    account.setType(powensAccount.type());
    account.setAccountNumberLastFour(lastFour(powensAccount.iban()));
    account.setBalance(defaultMoney(powensAccount.balance()));
    account.setComing(defaultMoney(powensAccount.coming()));
    account.setCurrency(defaultCurrency(currencyCode(powensAccount.currency())));
    account.setLastUpdate(powensAccount.lastUpdate());
    account.setDisabled(Boolean.TRUE.equals(powensAccount.disabled()));
    return accountRepository.save(account);
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
}

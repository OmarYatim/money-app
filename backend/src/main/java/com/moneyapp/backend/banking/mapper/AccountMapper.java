package com.moneyapp.backend.banking.mapper;

import com.moneyapp.backend.banking.dto.AccountResponse;
import com.moneyapp.backend.banking.entity.Account;

public final class AccountMapper {

  private AccountMapper() {}

  public static AccountResponse toResponse(Account account) {
    return AccountResponse.builder()
        .id(account.getId())
        .connectionId(account.getConnectionId())
        .institutionName(account.getInstitutionName())
        .name(account.getName())
        .type(account.getType())
        .accountNumberLastFour(account.getAccountNumberLastFour())
        .balance(account.getBalance())
        .coming(account.getComing())
        .currency(account.getCurrency())
        .lastUpdate(account.getLastUpdate())
        .disabled(account.isDisabled())
        .build();
  }
}

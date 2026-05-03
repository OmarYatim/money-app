package com.moneyapp.backend.banking.repository;

import com.moneyapp.backend.banking.entity.Account;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Long> {

  List<Account> findByUserIdAndDisabledFalseOrderByNameAsc(Long userId);

  Optional<Account> findByUserIdAndExternalAccountId(Long userId, Long externalAccountId);

  Optional<Account>
      findFirstByUserIdAndInstitutionNameAndNameAndTypeAndAccountNumberLastFourAndCurrencyAndDisabledFalse(
          Long userId,
          String institutionName,
          String name,
          String type,
          String accountNumberLastFour,
          String currency);
}

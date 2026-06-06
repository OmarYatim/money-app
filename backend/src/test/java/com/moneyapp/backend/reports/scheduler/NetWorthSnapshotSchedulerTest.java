package com.moneyapp.backend.reports.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import com.moneyapp.backend.auth.entity.AppUser;
import com.moneyapp.backend.auth.repository.AppUserRepository;
import com.moneyapp.backend.banking.entity.Account;
import com.moneyapp.backend.banking.repository.AccountRepository;
import com.moneyapp.backend.reports.repository.NetWorthSnapshotRepository;
import com.moneyapp.backend.transaction.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
    properties = {
      "powens.domain=powens.test",
      "powens.client-id=test-client-id",
      "powens.client-secret=test-client-secret",
      "powens.manage-token=test-manage-token",
      "powens.redirect-url=https://local.nexioo.me/api/bank/callback"
    })
@ActiveProfiles("test")
class NetWorthSnapshotSchedulerTest {

  @Autowired private AppUserRepository appUserRepository;

  @Autowired private AccountRepository accountRepository;

  @Autowired private TransactionRepository transactionRepository;

  @Autowired private NetWorthSnapshotRepository netWorthSnapshotRepository;

  @Autowired private NetWorthSnapshotScheduler scheduler;

  @BeforeEach
  void setUp() {
    netWorthSnapshotRepository.deleteAll();
    transactionRepository.deleteAll();
    accountRepository.deleteAll();
    appUserRepository.deleteAll();
  }

  @Test
  void createDailySnapshotsCreatesOnlyOneSnapshotPerUserPerDay() {
    AppUser appUser = appUserRepository.save(AppUser.builder().email("person@example.com").build());
    accountRepository.save(account(appUser.getId(), 1L, "checking", "1500"));
    accountRepository.save(account(appUser.getId(), 2L, "savings", "3000"));
    accountRepository.save(account(appUser.getId(), 3L, "credit", "-800"));

    scheduler.createDailySnapshots();
    scheduler.createDailySnapshots();

    assertThat(
            netWorthSnapshotRepository.findByUserIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(
                appUser.getId(), LocalDate.now(), LocalDate.now()))
        .singleElement()
        .satisfies(
            snapshot -> {
              assertThat(snapshot.getSnapshotDate()).isEqualTo(LocalDate.now());
              assertThat(snapshot.getNetWorth()).isEqualByComparingTo("3700");
            });
  }

  private Account account(Long userId, Long externalAccountId, String type, String balance) {
    return Account.builder()
        .userId(userId)
        .externalAccountId(externalAccountId)
        .name(type + " account")
        .type(type)
        .balance(new BigDecimal(balance))
        .coming(BigDecimal.ZERO)
        .currency("EUR")
        .build();
  }
}

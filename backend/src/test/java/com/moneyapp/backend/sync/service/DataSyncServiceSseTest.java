package com.moneyapp.backend.sync.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.moneyapp.backend.auth.entity.AppUser;
import com.moneyapp.backend.banking.repository.UserConnectionRepository;
import com.moneyapp.backend.banking.service.AccountService;
import com.moneyapp.backend.reports.service.NetWorthSnapshotService;
import com.moneyapp.backend.stream.service.SseEmitterService;
import com.moneyapp.backend.sync.enums.SyncEventTrigger;
import com.moneyapp.backend.sync.repository.SyncEventRepository;
import com.moneyapp.backend.transaction.entity.Transaction;
import com.moneyapp.backend.transaction.service.TransactionService;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;

class DataSyncServiceSseTest {

  @Test
  void emitsDataUpdatedAfterSuccessfulSync() {
    CapturingSseEmitterService sseEmitterService = new CapturingSseEmitterService();
    DataSyncService service =
        new DataSyncService(
            syncEventRepository(),
            userConnectionRepository(),
            new FakeAccountService(),
            new FakeTransactionService(),
            new ConcurrentTaskScheduler(),
            new FakeNetWorthSnapshotService(),
            new FakeGoalService(),
            sseEmitterService);

    service.sync(AppUser.builder().id(42L).build(), SyncEventTrigger.MANUAL, null);

    assertThat(sseEmitterService.updatedUserId).isEqualTo(42L);
  }

  private SyncEventRepository syncEventRepository() {
    return (SyncEventRepository)
        Proxy.newProxyInstance(
            SyncEventRepository.class.getClassLoader(),
            new Class<?>[] {SyncEventRepository.class},
            (proxy, method, args) -> {
              if ("save".equals(method.getName())) {
                return args[0];
              }
              throw new UnsupportedOperationException(method.getName());
            });
  }

  private UserConnectionRepository userConnectionRepository() {
    return (UserConnectionRepository)
        Proxy.newProxyInstance(
            UserConnectionRepository.class.getClassLoader(),
            new Class<?>[] {UserConnectionRepository.class},
            (proxy, method, args) -> {
              throw new UnsupportedOperationException(method.getName());
            });
  }

  private static class CapturingSseEmitterService extends SseEmitterService {
    private Long updatedUserId;

    @Override
    public void emitDataUpdated(Long userId) {
      updatedUserId = userId;
    }
  }

  private static class FakeAccountService extends AccountService {
    FakeAccountService() {
      super(null, null, null, null);
    }

    @Override
    public AccountSyncResult syncAccounts(AppUser appUser) {
      return new AccountSyncResult(List.of(), Set.of());
    }
  }

  private static class FakeTransactionService extends TransactionService {
    FakeTransactionService() {
      super(null, null, null, null, null);
    }

    @Override
    public List<Transaction> syncTransactions(AppUser appUser, Set<String> knownIbans) {
      return List.of();
    }
  }

  private static class FakeNetWorthSnapshotService extends NetWorthSnapshotService {
    FakeNetWorthSnapshotService() {
      super(null, null);
    }

    @Override
    public boolean createSnapshotIfMissing(AppUser user) {
      return true;
    }
  }

  private static class FakeGoalService extends com.moneyapp.backend.goals.service.GoalService {
    FakeGoalService() {
      super(null, null, null, null, null);
    }

    @Override
    public void refreshLinkedAccountGoals(
        Long userId, List<com.moneyapp.backend.banking.entity.Account> accounts) {}
  }
}

package com.moneyapp.backend.goals.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moneyapp.backend.auth.entity.AppUser;
import com.moneyapp.backend.auth.repository.AppUserRepository;
import com.moneyapp.backend.banking.entity.Account;
import com.moneyapp.backend.banking.repository.AccountRepository;
import com.moneyapp.backend.goals.dto.CreateContributionRequest;
import com.moneyapp.backend.goals.dto.CreateGoalRequest;
import com.moneyapp.backend.goals.dto.GoalProgressResponse;
import com.moneyapp.backend.goals.entity.Goal;
import com.moneyapp.backend.goals.repository.GoalContributionRepository;
import com.moneyapp.backend.goals.repository.GoalRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(
    properties = {
      "powens.domain=powens.test",
      "powens.client-id=test-client-id",
      "powens.client-secret=test-client-secret",
      "powens.manage-token=test-manage-token",
      "powens.redirect-url=https://local.nexioo.me/api/bank/callback",
      "app.jwt.secret=test-secret-key-must-be-at-least-32-chars!!",
      "app.jwt.expiration-ms=900000"
    })
@ActiveProfiles("test")
class GoalServiceTest {

  @Autowired private AppUserRepository appUserRepository;

  @Autowired private AccountRepository accountRepository;

  @Autowired private GoalRepository goalRepository;

  @Autowired private GoalContributionRepository goalContributionRepository;

  @Autowired private GoalService goalService;

  @Autowired private GoalProgressService goalProgressService;

  @BeforeEach
  void setUp() {
    goalContributionRepository.deleteAll();
    goalRepository.deleteAll();
    accountRepository.deleteAll();
    appUserRepository.deleteAll();
  }

  @Test
  void createGoalStoresDefaultValues() {
    AppUser appUser = appUserRepository.save(AppUser.builder().email("person@example.com").build());

    GoalProgressResponse response =
        goalService.createGoal(
            appUser.getEmail(),
            new CreateGoalRequest(
                "Holiday Japan", new BigDecimal("3000"), LocalDate.of(2027, 6, 1), null));

    assertThat(response.id()).isNotNull();
    assertThat(response.name()).isEqualTo("Holiday Japan");
    assertThat(response.targetAmount()).isEqualByComparingTo("3000");
    assertThat(response.currentAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(response.progressPercent()).isEqualByComparingTo("0.0");
    assertThat(response.archived()).isFalse();
    assertThat(goalRepository.findById(response.id()))
        .get()
        .satisfies(
            goal -> {
              assertThat(goal.isArchived()).isFalse();
              assertThat(goal.getCurrentAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            });
  }

  @Test
  void computeProgressUsesManualContributionsAndProjectsCompletionDate() {
    AppUser appUser = appUserRepository.save(AppUser.builder().email("person@example.com").build());
    Goal goal =
        goalRepository.save(
            Goal.builder()
                .userId(appUser.getId())
                .name("Emergency fund")
                .targetAmount(new BigDecimal("1000"))
                .currentAmount(BigDecimal.ZERO)
                .createdAt(LocalDateTime.now().minusMonths(2))
                .build());
    goal.setCreatedAt(LocalDateTime.now().minusMonths(2));
    goalService.addContribution(
        appUser.getEmail(),
        goal.getId(),
        new CreateContributionRequest(new BigDecimal("200"), null, LocalDate.now().minusMonths(1)));
    goalService.addContribution(
        appUser.getEmail(),
        goal.getId(),
        new CreateContributionRequest(new BigDecimal("150"), null, LocalDate.now()));

    GoalProgressResponse response = goalProgressService.computeProgress(goal);

    assertThat(response.currentAmount()).isEqualByComparingTo("350");
    assertThat(response.progressPercent()).isEqualByComparingTo("35.0");
    assertThat(response.monthlyRate()).isEqualByComparingTo("175.00");
    assertThat(response.projectedCompletionDate()).isAfter(LocalDate.now().plusMonths(3));
  }

  @Test
  void computeProgressReturnsNoProjectionWhenCurrentAmountIsZero() {
    AppUser appUser = appUserRepository.save(AppUser.builder().email("person@example.com").build());
    Goal goal =
        goalRepository.save(
            Goal.builder()
                .userId(appUser.getId())
                .name("New goal")
                .targetAmount(new BigDecimal("500"))
                .currentAmount(BigDecimal.ZERO)
                .build());

    GoalProgressResponse response = goalProgressService.computeProgress(goal);

    assertThat(response.projectedCompletionDate()).isNull();
    assertThat(response.progressPercent()).isEqualByComparingTo("0.0");
  }

  @Test
  void linkedAccountGoalUsesAccountBalance() {
    AppUser appUser = appUserRepository.save(AppUser.builder().email("person@example.com").build());
    Account account =
        accountRepository.save(
            Account.builder()
                .userId(appUser.getId())
                .externalAccountId(100L)
                .name("Savings")
                .balance(new BigDecimal("500"))
                .coming(BigDecimal.ZERO)
                .currency("EUR")
                .build());

    GoalProgressResponse response =
        goalService.createGoal(
            appUser.getEmail(),
            new CreateGoalRequest(
                "Deposit", new BigDecimal("1000"), LocalDate.of(2027, 1, 1), account.getId()));

    assertThat(response.currentAmount()).isEqualByComparingTo("500");

    account.setBalance(new BigDecimal("750"));
    accountRepository.save(account);
    goalService.refreshLinkedAccountGoals(appUser.getId(), List.of(account));

    assertThat(goalRepository.findById(response.id()))
        .get()
        .extracting(Goal::getCurrentAmount)
        .satisfies(amount -> assertThat((BigDecimal) amount).isEqualByComparingTo("750"));
  }

  @Test
  void findGoalsSortsTargetDateWithNullLast() {
    AppUser appUser = appUserRepository.save(AppUser.builder().email("person@example.com").build());
    goalRepository.save(goal(appUser.getId(), "Goal A", LocalDate.of(2028, 1, 1)));
    goalRepository.save(goal(appUser.getId(), "Goal B", LocalDate.of(2026, 12, 1)));
    goalRepository.save(goal(appUser.getId(), "Goal C", null));

    List<GoalProgressResponse> response = goalService.findGoals(appUser.getEmail());

    assertThat(response)
        .extracting(GoalProgressResponse::name)
        .containsExactly("Goal B", "Goal A", "Goal C");
  }

  @Test
  void archiveGoalSoftDeletesWithoutDeletingContributions() {
    AppUser appUser = appUserRepository.save(AppUser.builder().email("person@example.com").build());
    GoalProgressResponse goal =
        goalService.createGoal(
            appUser.getEmail(), new CreateGoalRequest("Trip", new BigDecimal("800"), null, null));
    goalService.addContribution(
        appUser.getEmail(),
        goal.id(),
        new CreateContributionRequest(new BigDecimal("100"), "Gift", LocalDate.now()));

    goalService.archiveGoal(appUser.getEmail(), goal.id());

    assertThat(goalRepository.findById(goal.id()))
        .get()
        .extracting(Goal::isArchived)
        .isEqualTo(true);
    assertThat(goalService.findGoals(appUser.getEmail())).isEmpty();
    assertThat(goalContributionRepository.findByGoalIdOrderByContributedAtAscIdAsc(goal.id()))
        .hasSize(1);
  }

  @Test
  void addContributionUpdatesCurrentAmountImmediately() {
    AppUser appUser = appUserRepository.save(AppUser.builder().email("person@example.com").build());
    GoalProgressResponse goal =
        goalService.createGoal(
            appUser.getEmail(), new CreateGoalRequest("Camera", new BigDecimal("500"), null, null));

    GoalProgressResponse response =
        goalService.addContribution(
            appUser.getEmail(),
            goal.id(),
            new CreateContributionRequest(
                new BigDecimal("150"), "Birthday money", LocalDate.of(2026, 4, 21)));

    assertThat(response.currentAmount()).isEqualByComparingTo("150");
    assertThat(goalRepository.findById(goal.id()))
        .get()
        .extracting(Goal::getCurrentAmount)
        .satisfies(amount -> assertThat((BigDecimal) amount).isEqualByComparingTo("150"));
    assertThat(goalContributionRepository.findByGoalIdOrderByContributedAtAscIdAsc(goal.id()))
        .singleElement()
        .satisfies(
            contribution -> {
              assertThat(contribution.getAmount()).isEqualByComparingTo("150");
              assertThat(contribution.getNote()).isEqualTo("Birthday money");
            });
  }

  @Test
  void anotherUserCannotAccessOrContributeToGoal() {
    AppUser owner = appUserRepository.save(AppUser.builder().email("owner@example.com").build());
    AppUser other = appUserRepository.save(AppUser.builder().email("other@example.com").build());
    GoalProgressResponse goal =
        goalService.createGoal(
            owner.getEmail(), new CreateGoalRequest("Private", new BigDecimal("500"), null, null));

    assertThatThrownBy(() -> goalService.getGoal(other.getEmail(), goal.id()))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("403 FORBIDDEN");
    assertThatThrownBy(
            () ->
                goalService.addContribution(
                    other.getEmail(),
                    goal.id(),
                    new CreateContributionRequest(new BigDecimal("100"), null, LocalDate.now())))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("403 FORBIDDEN");
    assertThatThrownBy(() -> goalService.archiveGoal(other.getEmail(), goal.id()))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("403 FORBIDDEN");
  }

  private Goal goal(Long userId, String name, LocalDate targetDate) {
    return Goal.builder()
        .userId(userId)
        .name(name)
        .targetAmount(new BigDecimal("1000"))
        .targetDate(targetDate)
        .currentAmount(BigDecimal.ZERO)
        .build();
  }
}

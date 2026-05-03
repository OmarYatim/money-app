package com.moneyapp.backend.transaction.service;

import com.moneyapp.backend.auth.entity.AppUser;
import com.moneyapp.backend.auth.repository.AppUserRepository;
import com.moneyapp.backend.banking.entity.Account;
import com.moneyapp.backend.banking.repository.AccountRepository;
import com.moneyapp.backend.banking.service.PowensClient;
import com.moneyapp.backend.transaction.dto.PowensTransactionResponse;
import com.moneyapp.backend.transaction.dto.PowensTransactionsResponse;
import com.moneyapp.backend.transaction.dto.TransactionResponse;
import com.moneyapp.backend.transaction.entity.Transaction;
import com.moneyapp.backend.transaction.enums.CategoryType;
import com.moneyapp.backend.transaction.mapper.TransactionMapper;
import com.moneyapp.backend.transaction.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class TransactionService {

  private final TransactionRepository transactionRepository;
  private final AccountRepository accountRepository;
  private final AppUserRepository appUserRepository;
  private final PowensClient powensClient;
  private final CategoryMappingService categoryMappingService;

  @Transactional(readOnly = true)
  public List<TransactionResponse> findTransactions(String email) {
    return appUserRepository
        .findByEmail(email)
        .map(AppUser::getId)
        .map(transactionRepository::findByUserIdOrderByDateDescIdDesc)
        .orElse(List.of())
        .stream()
        .map(TransactionMapper::toResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public TransactionResponse getTransaction(String email, Long transactionId) {
    Long userId = requireUserId(email);
    Transaction transaction = requireTransaction(transactionId);
    verifyOwner(transaction, userId);
    return TransactionMapper.toResponse(transaction);
  }

  @Transactional
  public TransactionResponse updateCategory(String email, Long transactionId, String category) {
    Long userId = requireUserId(email);
    Transaction transaction = requireTransaction(transactionId);
    verifyOwner(transaction, userId);

    CategoryType categoryType = parseCategory(category);
    transaction.setCategory(categoryType.name());
    transaction.setCategoryOverridden(true);
    return TransactionMapper.toResponse(transactionRepository.save(transaction));
  }

  @Transactional
  public List<Transaction> syncTransactions(AppUser appUser) {
    if (appUser == null || appUser.getId() == null || isBlank(appUser.getPowensToken())) {
      throw new IllegalStateException(
          "Powens user identity is required before syncing transactions");
    }

    PowensTransactionsResponse response = powensClient.fetchTransactions(appUser.getPowensToken());
    if (response == null || response.transactions() == null) {
      return List.of();
    }

    return response.transactions().stream()
        .filter(transaction -> transaction.id() != null)
        .map(transaction -> upsertTransaction(appUser.getId(), transaction))
        .toList();
  }

  private Transaction upsertTransaction(Long userId, PowensTransactionResponse powensTransaction) {
    Transaction transaction =
        transactionRepository
            .findByUserIdAndExternalTransactionId(userId, powensTransaction.id())
            .orElseGet(
                () ->
                    Transaction.builder()
                        .userId(userId)
                        .externalTransactionId(powensTransaction.id())
                        .build());

    transaction.setExternalAccountId(powensTransaction.accountId());
    transaction.setAccountId(resolveAccountId(userId, powensTransaction.accountId()));
    transaction.setDate(defaultDate(powensTransaction.date()));
    transaction.setLabel(defaultLabel(powensTransaction));
    transaction.setWording(powensTransaction.wording());
    transaction.setValue(defaultMoney(powensTransaction.value()));

    if (!transaction.isCategoryOverridden()) {
      transaction.setCategory(
          categoryMappingService.map(firstCategory(powensTransaction.categories())).name());
    }

    return transactionRepository.save(transaction);
  }

  private Long requireUserId(String email) {
    return appUserRepository
        .findByEmail(email)
        .map(AppUser::getId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
  }

  private Transaction requireTransaction(Long transactionId) {
    return transactionRepository
        .findById(transactionId)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"));
  }

  private void verifyOwner(Transaction transaction, Long userId) {
    if (!transaction.getUserId().equals(userId)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
    }
  }

  private CategoryType parseCategory(String category) {
    if (isBlank(category)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "category is required");
    }

    try {
      return CategoryType.valueOf(category);
    } catch (IllegalArgumentException exception) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, category + " is not a valid category", exception);
    }
  }

  private Long resolveAccountId(Long userId, Long externalAccountId) {
    if (externalAccountId == null) {
      return null;
    }

    return accountRepository
        .findByUserIdAndExternalAccountId(userId, externalAccountId)
        .map(Account::getId)
        .orElse(null);
  }

  private String firstCategory(List<String> categories) {
    if (categories == null || categories.isEmpty()) {
      return null;
    }

    return categories.get(0);
  }

  private LocalDate defaultDate(LocalDate date) {
    return date == null ? LocalDate.now() : date;
  }

  private String defaultLabel(PowensTransactionResponse transaction) {
    if (!isBlank(transaction.label())) {
      return transaction.label();
    }

    if (!isBlank(transaction.wording())) {
      return transaction.wording();
    }

    return "Unknown transaction";
  }

  private BigDecimal defaultMoney(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}

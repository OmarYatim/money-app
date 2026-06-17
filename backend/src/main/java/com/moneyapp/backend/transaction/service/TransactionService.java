package com.moneyapp.backend.transaction.service;

import com.moneyapp.backend.auth.entity.AppUser;
import com.moneyapp.backend.auth.service.CurrentAppUserService;
import com.moneyapp.backend.banking.entity.Account;
import com.moneyapp.backend.banking.mapper.AccountMapper;
import com.moneyapp.backend.banking.repository.AccountRepository;
import com.moneyapp.backend.banking.service.PowensClient;
import com.moneyapp.backend.transaction.dto.PowensTransactionResponse;
import com.moneyapp.backend.transaction.dto.PowensTransactionsResponse;
import com.moneyapp.backend.transaction.dto.TransactionFilter;
import com.moneyapp.backend.transaction.dto.TransactionResponse;
import com.moneyapp.backend.transaction.dto.TransactionSummaryResponse;
import com.moneyapp.backend.transaction.entity.Transaction;
import com.moneyapp.backend.transaction.enums.CategoryType;
import com.moneyapp.backend.transaction.mapper.TransactionMapper;
import com.moneyapp.backend.transaction.repository.TransactionRepository;
import com.moneyapp.backend.transaction.spec.TransactionSpecification;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class TransactionService {

  private static final Pattern DIGIT_RUN = Pattern.compile("\\d{10,}");
  private static final int MIN_IBAN_DIGIT_MATCH_LENGTH = 10;

  private final TransactionRepository transactionRepository;
  private final AccountRepository accountRepository;
  private final CurrentAppUserService currentAppUserService;
  private final PowensClient powensClient;
  private final CategoryMappingService categoryMappingService;

  @Transactional(readOnly = true)
  public Page<TransactionResponse> findTransactions(
      String email, TransactionFilter filter, Pageable pageable) {
    AppUser appUser = currentAppUserService.resolveExisting(email);
    TransactionFilter normalizedFilter = normalizeFilter(filter);
    validateCategory(normalizedFilter.category());

    Page<Transaction> transactions =
        transactionRepository.findAll(
            TransactionSpecification.forUserWithFilters(appUser.getId(), normalizedFilter),
            pageable);
    Map<Long, Account> accountsById = findAccountsById(appUser.getId(), transactions.getContent());

    return transactions.map(
        transaction ->
            TransactionMapper.toResponse(
                transaction, accountName(accountsById.get(transaction.getAccountId()))));
  }

  @Transactional(readOnly = true)
  public TransactionSummaryResponse summarizeTransactions(String email, TransactionFilter filter) {
    AppUser appUser = currentAppUserService.resolveExisting(email);
    TransactionFilter normalizedFilter = normalizeFilter(filter);
    validateCategory(normalizedFilter.category());

    List<Transaction> transactions =
        transactionRepository.findAll(
            TransactionSpecification.forUserWithFilters(appUser.getId(), normalizedFilter));
    List<Transaction> cashFlowTransactions =
        transactions.stream().filter(transaction -> !transaction.isInternalTransfer()).toList();
    BigDecimal totalIn =
        cashFlowTransactions.stream()
            .filter(transaction -> transaction.getValue().compareTo(BigDecimal.ZERO) > 0)
            .map(Transaction::getValue)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal totalOut =
        cashFlowTransactions.stream()
            .filter(transaction -> transaction.getValue().compareTo(BigDecimal.ZERO) < 0)
            .map(transaction -> transaction.getValue().abs())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    long unreviewedCount =
        transactions.stream().filter(transaction -> !transaction.isReviewed()).count();

    return new TransactionSummaryResponse(
        transactions.size(), unreviewedCount, totalIn, totalOut, totalIn.subtract(totalOut));
  }

  @Transactional(readOnly = true)
  public TransactionResponse getTransaction(String email, Long transactionId) {
    Long userId = requireUserId(email);
    Transaction transaction = requireTransaction(transactionId);
    verifyOwner(transaction, userId);
    Map<Long, Account> accountsById = findAccountsById(userId, List.of(transaction));
    return TransactionMapper.toResponse(
        transaction, accountName(accountsById.get(transaction.getAccountId())));
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
  public List<Transaction> syncTransactions(AppUser appUser, Set<String> knownIbans) {
    if (appUser == null || appUser.getId() == null || isBlank(appUser.getPowensToken())) {
      throw new IllegalStateException(
          "Powens user identity is required before syncing transactions");
    }

    PowensTransactionsResponse response = powensClient.fetchTransactions(appUser.getPowensToken());
    if (response == null || response.transactions() == null) {
      return List.of();
    }

    List<Transaction> saved =
        response.transactions().stream()
            .filter(transaction -> transaction.id() != null)
            .map(transaction -> upsertTransaction(appUser.getId(), transaction))
            .toList();
    detectInternalTransfers(appUser.getId(), knownIbans);
    return saved;
  }

  @Transactional
  public TransactionResponse updateInternalTransfer(
      String email, Long transactionId, boolean internalTransfer) {
    Long userId = requireUserId(email);
    Transaction transaction = requireTransaction(transactionId);
    verifyOwner(transaction, userId);

    transaction.setInternalTransfer(internalTransfer);
    transaction.setInternalTransferOverridden(true);
    return TransactionMapper.toResponse(transactionRepository.save(transaction));
  }

  @Transactional
  public TransactionResponse updateReviewed(String email, Long transactionId, boolean reviewed) {
    Long userId = requireUserId(email);
    Transaction transaction = requireTransaction(transactionId);
    verifyOwner(transaction, userId);

    transaction.setReviewed(reviewed);
    transaction.setReviewedAt(reviewed ? LocalDateTime.now() : null);
    return TransactionMapper.toResponse(transactionRepository.save(transaction));
  }

  private void detectInternalTransfers(Long userId, Set<String> knownIbans) {
    List<Transaction> candidates =
        transactionRepository.findByUserIdAndTypeAndInternalTransferOverriddenFalse(
            userId, "transfer");

    Set<Long> matched = new HashSet<>();

    if (!knownIbans.isEmpty()) {
      // Primary: detect by IBAN digit sequence in transaction wording
      for (Transaction t : candidates) {
        if (matchesKnownAccountByIban(t.getWording(), knownIbans)) {
          matched.add(t.getId());
        }
      }
      // Secondary: find credit pairs for each IBAN-detected debit
      for (Transaction debit : candidates) {
        if (debit.getValue().compareTo(BigDecimal.ZERO) >= 0) continue;
        if (!matched.contains(debit.getId())) continue;
        BigDecimal creditAmount = debit.getValue().negate();
        candidates.stream()
            .filter(c -> c.getValue().compareTo(creditAmount) == 0)
            .filter(c -> !matched.contains(c.getId()))
            .filter(c -> Math.abs(ChronoUnit.DAYS.between(c.getDate(), debit.getDate())) <= 1)
            .findFirst()
            .ifPresent(credit -> matched.add(credit.getId()));
      }
    } else {
      // Fallback when IBANs are unavailable: match by value, date, and registered accounts
      for (Transaction debit : candidates) {
        if (debit.getValue().compareTo(BigDecimal.ZERO) >= 0) continue;
        if (debit.getAccountId() == null) continue;
        if (matched.contains(debit.getId())) continue;
        BigDecimal creditAmount = debit.getValue().negate();
        candidates.stream()
            .filter(c -> c.getValue().compareTo(creditAmount) == 0)
            .filter(c -> c.getAccountId() != null && !c.getAccountId().equals(debit.getAccountId()))
            .filter(c -> !matched.contains(c.getId()))
            .filter(c -> Math.abs(ChronoUnit.DAYS.between(c.getDate(), debit.getDate())) <= 1)
            .findFirst()
            .ifPresent(
                credit -> {
                  matched.add(debit.getId());
                  matched.add(credit.getId());
                });
      }
    }

    List<Transaction> toUpdate = new ArrayList<>();
    for (Transaction t : candidates) {
      boolean shouldBeInternal = matched.contains(t.getId());
      if (t.isInternalTransfer() != shouldBeInternal) {
        t.setInternalTransfer(shouldBeInternal);
        toUpdate.add(t);
      }
    }
    if (!toUpdate.isEmpty()) {
      transactionRepository.saveAll(toUpdate);
    }
  }

  private boolean matchesKnownAccountByIban(String wording, Set<String> knownIbans) {
    if (wording == null) return false;
    List<String> wordingDigitRuns = extractDigitRuns(wording);
    if (wordingDigitRuns.isEmpty()) return false;
    return knownIbans.stream()
        .filter(iban -> iban != null && !iban.isBlank())
        .anyMatch(
            iban -> {
              String ibanDigits = iban.replaceAll("[^0-9]", "");
              return wordingDigitRuns.stream().anyMatch(ibanDigits::contains);
            });
  }

  private List<String> extractDigitRuns(String text) {
    List<String> runs = new ArrayList<>();
    Matcher matcher = DIGIT_RUN.matcher(text);
    while (matcher.find()) {
      runs.add(matcher.group());
    }
    return runs;
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
    transaction.setOriginalWording(powensTransaction.originalWording());
    transaction.setApplicationDate(powensTransaction.applicationDate());
    transaction.setValue(defaultMoney(powensTransaction.value()));
    transaction.setType(powensTransaction.type());
    transaction.setCounterpartyLabel(counterpartyLabel(powensTransaction.counterparty()));

    if (!transaction.isCategoryOverridden()) {
      transaction.setCategory(categoryMappingService.map(powensTransaction.idCategory()).name());
    }

    return transactionRepository.save(transaction);
  }

  private Long requireUserId(String email) {
    return currentAppUserService.resolveExisting(email).getId();
  }

  private TransactionFilter normalizeFilter(TransactionFilter filter) {
    if (filter == null) {
      return new TransactionFilter(null, null, null, null, null, null, null, null, null);
    }

    return new TransactionFilter(
        filter.accountId(),
        normalizeText(filter.category()),
        filter.minDate(),
        filter.maxDate(),
        filter.minAmount(),
        filter.maxAmount(),
        normalizeText(filter.keyword()),
        filter.reviewed(),
        filter.internalTransfer());
  }

  private Map<Long, Account> findAccountsById(Long userId, List<Transaction> transactions) {
    List<Long> accountIds =
        transactions.stream()
            .map(Transaction::getAccountId)
            .filter(accountId -> accountId != null)
            .distinct()
            .toList();
    if (accountIds.isEmpty()) {
      return Collections.emptyMap();
    }

    return accountRepository.findByUserIdAndIdIn(userId, accountIds).stream()
        .collect(Collectors.toMap(Account::getId, Function.identity()));
  }

  private String accountName(Account account) {
    return AccountMapper.displayName(account);
  }

  private String counterpartyLabel(PowensTransactionResponse.PowensCounterparty counterparty) {
    return counterparty == null ? null : counterparty.label();
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

    return validateCategory(category);
  }

  private CategoryType validateCategory(String category) {
    if (isBlank(category)) {
      return null;
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

  private String normalizeText(String value) {
    return isBlank(value) ? null : value.trim();
  }
}

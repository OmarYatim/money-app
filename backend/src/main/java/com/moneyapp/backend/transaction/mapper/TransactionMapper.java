package com.moneyapp.backend.transaction.mapper;

import com.moneyapp.backend.transaction.dto.TransactionResponse;
import com.moneyapp.backend.transaction.entity.Transaction;

public final class TransactionMapper {

  private TransactionMapper() {}

  public static TransactionResponse toResponse(Transaction transaction) {
    return toResponse(transaction, null);
  }

  public static TransactionResponse toResponse(Transaction transaction, String accountName) {
    return new TransactionResponse(
        transaction.getId(),
        transaction.getAccountId(),
        accountName,
        transaction.getDate(),
        transaction.getLabel(),
        transaction.getWording(),
        transaction.getOriginalWording(),
        transaction.getValue(),
        transaction.getApplicationDate(),
        transaction.getCategory(),
        transaction.isCategoryOverridden(),
        transaction.getType(),
        transaction.getCounterpartyLabel(),
        transaction.isInternalTransfer(),
        transaction.isInternalTransferOverridden(),
        transaction.isReviewed(),
        transaction.getReviewedAt());
  }
}

package com.moneyapp.backend.transaction.mapper;

import com.moneyapp.backend.transaction.dto.TransactionResponse;
import com.moneyapp.backend.transaction.entity.Transaction;

public final class TransactionMapper {

  private TransactionMapper() {}

  public static TransactionResponse toResponse(Transaction transaction) {
    return new TransactionResponse(
        transaction.getId(),
        transaction.getDate(),
        transaction.getLabel(),
        transaction.getWording(),
        transaction.getValue(),
        transaction.getCategory(),
        transaction.isCategoryOverridden(),
        transaction.isInternalTransfer(),
        transaction.isInternalTransferOverridden());
  }
}

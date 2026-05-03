package com.moneyapp.backend.transaction.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateTransactionReviewedRequest(@NotNull Boolean reviewed) {}

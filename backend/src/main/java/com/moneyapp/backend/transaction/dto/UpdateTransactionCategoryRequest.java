package com.moneyapp.backend.transaction.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateTransactionCategoryRequest(
    @NotBlank(message = "category is required") String category) {}

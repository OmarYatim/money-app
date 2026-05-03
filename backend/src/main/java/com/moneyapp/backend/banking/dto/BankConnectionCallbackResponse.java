package com.moneyapp.backend.banking.dto;

import java.util.List;
import lombok.Builder;

@Builder
public record BankConnectionCallbackResponse(
    String status, String message, List<Long> connectionIds) {}

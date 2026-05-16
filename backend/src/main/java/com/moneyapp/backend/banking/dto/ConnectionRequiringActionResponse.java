package com.moneyapp.backend.banking.dto;

import lombok.Builder;

@Builder
public record ConnectionRequiringActionResponse(
    Long connectionId, String state, String errorMessage) {}

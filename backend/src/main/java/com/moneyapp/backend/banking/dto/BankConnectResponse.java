package com.moneyapp.backend.banking.dto;

import lombok.Builder;

@Builder
public record BankConnectResponse(String webviewUrl, String state) {}

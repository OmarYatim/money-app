package com.moneyapp.backend.banking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateAccountRequest(
    @NotBlank(message = "name is required") @Size(max = 255, message = "name is too long")
        String name) {}

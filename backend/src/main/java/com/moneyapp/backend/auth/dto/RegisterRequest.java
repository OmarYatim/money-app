package com.moneyapp.backend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank @Email String email,
    @NotBlank
        @Size(min = 12, message = "password must be at least 12 characters")
        @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$",
            message = "password must include uppercase, lowercase, number, and symbol")
        String password,
    @NotBlank @Size(max = 100) String firstName,
    @NotBlank @Size(max = 100) String lastName,
    @NotBlank
        @Size(max = 32)
        @Pattern(regexp = "^\\+?[0-9]{7,20}$", message = "phone must be valid")
        String phone) {}

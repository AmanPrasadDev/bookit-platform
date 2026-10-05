package com.bookit.catalogue.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateCinemaRequest(
        @NotNull(message = "Organization ID is required")
        UUID organizationId,

        @NotNull(message = "City ID is required")
        UUID cityId,

        @NotBlank(message = "Cinema name is required")
        @Size(max = 255, message = "Cinema name cannot exceed 255 characters")
        String name,

        @NotBlank(message = "Address is required")
        String address,

        BigDecimal latitude,
        BigDecimal longitude,
        String timeZone
) {}

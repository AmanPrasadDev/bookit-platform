package com.bookit.catalogue.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateScreenRequest(
        @NotNull(message = "Cinema ID is required")
        UUID cinemaId,

        @NotBlank(message = "Screen name is required")
        @Size(max = 100, message = "Screen name cannot exceed 100 characters")
        String name
) {}

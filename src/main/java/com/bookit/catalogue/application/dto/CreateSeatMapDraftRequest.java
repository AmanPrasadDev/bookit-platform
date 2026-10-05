package com.bookit.catalogue.application.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateSeatMapDraftRequest(
        @NotNull(message = "Screen ID is required")
        UUID screenId
) {}

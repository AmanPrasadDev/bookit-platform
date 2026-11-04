package com.bookit.hold.application.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Customer input for placing an exclusive temporary hold on a batch of seats.
 * Enforces anti-scalping bounds (1 to 10 seats max per session).
 */
public record CreateSeatHoldRequest(
        @NotNull(message = "Show ID is required")
        UUID showId,

        @NotEmpty(message = "At least one seat must be selected")
        @Size(min = 1, max = 10, message = "A maximum of 10 seats can be held in a single booking session")
        List<UUID> seatIds
) {}

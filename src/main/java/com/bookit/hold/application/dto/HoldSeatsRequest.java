package com.bookit.hold.application.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Customer request payload for holding seats for a specific show.
 * Contains only the seat IDs; showId is supplied via URL path variable.
 */
public record HoldSeatsRequest(
        @NotEmpty(message = "At least one seat must be selected")
        @Size(min = 1, max = 10, message = "A maximum of 10 seats can be held in a single booking session")
        List<UUID> seatIds
) {}

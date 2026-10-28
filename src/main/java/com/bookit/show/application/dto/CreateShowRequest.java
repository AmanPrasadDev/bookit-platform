package com.bookit.show.application.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/**
 * Operator input for scheduling a DRAFT show. {@code endsAt} is intentionally absent:
 * the service derives it from the movie runtime plus a cleaning buffer.
 */
public record CreateShowRequest(
        @NotNull(message = "Movie ID is required")
        UUID movieId,

        @NotNull(message = "Screen ID is required")
        UUID screenId,

        @NotNull(message = "Seat map version ID is required")
        UUID seatMapVersionId,

        @NotNull(message = "Format ID is required")
        UUID formatId,

        @NotNull(message = "Show start time is required")
        Instant startsAt,

        @NotNull(message = "Booking open time is required")
        Instant bookingOpensAt,

        @NotNull(message = "Booking close time is required")
        Instant bookingClosesAt
) {}

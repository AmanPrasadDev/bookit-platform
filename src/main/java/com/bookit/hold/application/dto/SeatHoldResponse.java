package com.bookit.hold.application.dto;

import com.bookit.hold.domain.SeatHold;
import com.bookit.hold.domain.SeatHoldStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Public representation of an active or terminated seat hold session.
 */
public record SeatHoldResponse(
        UUID id,
        UUID customerId,
        UUID showId,
        List<UUID> seatIds,
        SeatHoldStatus status,
        Instant expiresAt,
        Instant createdAt
) {
    public static SeatHoldResponse fromDomain(SeatHold hold, List<UUID> seatIds) {
        return new SeatHoldResponse(
                hold.getId(),
                hold.getCustomerId(),
                hold.getShowId(),
                seatIds,
                hold.getStatus(),
                hold.getExpiresAt(),
                hold.getCreatedAt()
        );
    }
}

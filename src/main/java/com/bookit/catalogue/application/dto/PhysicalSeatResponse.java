package com.bookit.catalogue.application.dto;

import com.bookit.catalogue.domain.PhysicalSeat;
import com.bookit.catalogue.domain.SeatStatus;
import com.bookit.catalogue.domain.SeatType;

import java.util.UUID;

public record PhysicalSeatResponse(
        UUID id,
        String rowLabel,
        int seatNumber,
        SeatType seatType,
        SeatStatus status
) {
    public static PhysicalSeatResponse fromDomain(PhysicalSeat seat) {
        return new PhysicalSeatResponse(
                seat.getId(),
                seat.getRowLabel(),
                seat.getSeatNumber(),
                seat.getSeatType(),
                seat.getStatus()
        );
    }
}

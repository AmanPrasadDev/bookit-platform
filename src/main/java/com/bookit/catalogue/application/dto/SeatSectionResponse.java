package com.bookit.catalogue.application.dto;

import com.bookit.catalogue.domain.SeatSection;

import java.util.List;
import java.util.UUID;

public record SeatSectionResponse(
        UUID id,
        String name,
        String categoryCode,
        int displayOrder,
        List<PhysicalSeatResponse> seats
) {
    public static SeatSectionResponse fromDomain(SeatSection section, List<PhysicalSeatResponse> seats) {
        return new SeatSectionResponse(
                section.getId(),
                section.getName(),
                section.getCategoryCode(),
                section.getDisplayOrder(),
                seats
        );
    }
}

package com.bookit.catalogue.application.dto;

import com.bookit.catalogue.domain.SeatMapStatus;
import com.bookit.catalogue.domain.SeatMapVersion;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SeatMapResponse(
        UUID id,
        UUID screenId,
        int versionNumber,
        SeatMapStatus status,
        int totalSeats,
        Instant publishedAt,
        List<SeatSectionResponse> sections
) {
    public static SeatMapResponse fromDomain(SeatMapVersion version, List<SeatSectionResponse> sections) {
        return new SeatMapResponse(
                version.getId(),
                version.getScreenId(),
                version.getVersionNumber(),
                version.getStatus(),
                version.getTotalSeats(),
                version.getPublishedAt(),
                sections
        );
    }
}

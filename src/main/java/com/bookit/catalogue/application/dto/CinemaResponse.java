package com.bookit.catalogue.application.dto;

import com.bookit.catalogue.domain.Cinema;

import java.math.BigDecimal;
import java.util.UUID;

public record CinemaResponse(
        UUID id,
        UUID organizationId,
        UUID cityId,
        String name,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        String timeZone,
        String status
) {
    public static CinemaResponse fromDomain(Cinema cinema) {
        return new CinemaResponse(
                cinema.getId(),
                cinema.getOrganizationId(),
                cinema.getCityId(),
                cinema.getName(),
                cinema.getAddress(),
                cinema.getLatitude(),
                cinema.getLongitude(),
                cinema.getTimeZone(),
                cinema.getStatus()
        );
    }
}

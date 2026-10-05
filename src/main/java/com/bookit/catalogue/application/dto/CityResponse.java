package com.bookit.catalogue.application.dto;

import com.bookit.catalogue.domain.City;
import java.util.UUID;

public record CityResponse(
        UUID id,
        String name,
        String state,
        String countryCode,
        String timeZone,
        String status
) {
    public static CityResponse fromDomain(City city) {
        return new CityResponse(
                city.getId(),
                city.getName(),
                city.getState(),
                city.getCountryCode(),
                city.getTimeZone(),
                city.getStatus()
        );
    }
}

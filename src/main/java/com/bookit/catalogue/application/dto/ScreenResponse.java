package com.bookit.catalogue.application.dto;

import com.bookit.catalogue.domain.Screen;
import java.util.UUID;

public record ScreenResponse(
        UUID id,
        UUID cinemaId,
        String name,
        String status
) {
    public static ScreenResponse fromDomain(Screen screen) {
        return new ScreenResponse(
                screen.getId(),
                screen.getCinemaId(),
                screen.getName(),
                screen.getStatus()
        );
    }
}

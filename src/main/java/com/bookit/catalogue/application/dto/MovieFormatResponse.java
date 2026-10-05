package com.bookit.catalogue.application.dto;

import com.bookit.catalogue.domain.MovieFormat;
import java.util.UUID;

public record MovieFormatResponse(
        UUID id,
        String code,
        String displayName
) {
    public static MovieFormatResponse fromDomain(MovieFormat format) {
        return new MovieFormatResponse(
                format.getId(),
                format.getCode(),
                format.getDisplayName()
        );
    }
}

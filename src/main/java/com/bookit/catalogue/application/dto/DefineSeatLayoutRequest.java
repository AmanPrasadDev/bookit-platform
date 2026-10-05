package com.bookit.catalogue.application.dto;

import com.bookit.catalogue.domain.SeatType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record DefineSeatLayoutRequest(
        @NotEmpty(message = "At least one section must be defined")
        @Valid
        List<SectionDefinition> sections
) {
    public record SectionDefinition(
            @NotBlank(message = "Section name is required")
            @Size(max = 50, message = "Section name cannot exceed 50 characters")
            String name,

            @NotBlank(message = "Category code is required (e.g. RECLINER, PRIME, CLASSIC)")
            @Size(max = 20, message = "Category code cannot exceed 20 characters")
            String categoryCode,

            @Min(value = 1, message = "Display order must be positive")
            int displayOrder,

            @NotEmpty(message = "At least one seat must be defined in section")
            @Valid
            List<SeatDefinition> seats
    ) {}

    public record SeatDefinition(
            @NotBlank(message = "Row label is required (e.g. A, B, C)")
            @Size(max = 10, message = "Row label cannot exceed 10 characters")
            String rowLabel,

            @Min(value = 1, message = "Seat number must be positive")
            int seatNumber,

            @NotNull(message = "Seat type is required")
            SeatType seatType
    ) {}
}

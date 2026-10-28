package com.bookit.show.application.dto;

import com.bookit.show.domain.Show;
import com.bookit.show.domain.ShowStatus;

import java.time.Instant;
import java.util.UUID;

public record ShowResponse(
        UUID id,
        UUID movieId,
        UUID screenId,
        UUID seatMapVersionId,
        UUID formatId,
        Instant startsAt,
        Instant endsAt,
        Instant bookingOpensAt,
        Instant bookingClosesAt,
        ShowStatus status
) {
    public static ShowResponse fromDomain(Show show) {
        return new ShowResponse(
                show.getId(),
                show.getMovieId(),
                show.getScreenId(),
                show.getSeatMapVersionId(),
                show.getFormatId(),
                show.getStartsAt(),
                show.getEndsAt(),
                show.getBookingOpensAt(),
                show.getBookingClosesAt(),
                show.getStatus()
        );
    }
}

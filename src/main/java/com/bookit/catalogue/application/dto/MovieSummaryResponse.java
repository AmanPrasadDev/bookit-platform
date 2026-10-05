package com.bookit.catalogue.application.dto;

import com.bookit.catalogue.domain.Movie;
import com.bookit.catalogue.domain.MovieCertificate;
import com.bookit.catalogue.domain.MovieStatus;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

public record MovieSummaryResponse(
        UUID id,
        String title,
        int runtimeMinutes,
        MovieCertificate certificate,
        LocalDate releaseDate,
        MovieStatus status,
        Set<String> languages,
        Set<String> genres
) {
    public static MovieSummaryResponse fromDomain(Movie movie) {
        return new MovieSummaryResponse(
                movie.getId(),
                movie.getTitle(),
                movie.getRuntimeMinutes(),
                movie.getCertificate(),
                movie.getReleaseDate(),
                movie.getStatus(),
                movie.getLanguages(),
                movie.getGenres()
        );
    }
}

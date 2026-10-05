package com.bookit.catalogue.application.dto;

import com.bookit.catalogue.domain.Movie;
import com.bookit.catalogue.domain.MovieCertificate;
import com.bookit.catalogue.domain.MovieStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record MovieDetailResponse(
        UUID id,
        String title,
        String synopsis,
        int runtimeMinutes,
        MovieCertificate certificate,
        LocalDate releaseDate,
        MovieStatus status,
        Set<String> languages,
        Set<String> genres,
        List<MovieFormatResponse> supportedFormats
) {
    public static MovieDetailResponse fromDomain(Movie movie, List<MovieFormatResponse> supportedFormats) {
        return new MovieDetailResponse(
                movie.getId(),
                movie.getTitle(),
                movie.getSynopsis(),
                movie.getRuntimeMinutes(),
                movie.getCertificate(),
                movie.getReleaseDate(),
                movie.getStatus(),
                movie.getLanguages(),
                movie.getGenres(),
                supportedFormats
        );
    }
}

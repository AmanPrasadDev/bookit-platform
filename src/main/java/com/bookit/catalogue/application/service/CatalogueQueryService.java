package com.bookit.catalogue.application.service;

import com.bookit.catalogue.application.dto.*;
import com.bookit.catalogue.domain.*;
import com.bookit.catalogue.domain.exception.ResourceNotFoundException;
import com.bookit.catalogue.infrastructure.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogueQueryService {

    private final CityRepository cityRepository;
    private final CinemaRepository cinemaRepository;
    private final ScreenRepository screenRepository;
    private final SeatMapVersionRepository seatMapVersionRepository;
    private final SeatSectionRepository seatSectionRepository;
    private final PhysicalSeatRepository physicalSeatRepository;
    private final MovieRepository movieRepository;
    private final MovieFormatRepository movieFormatRepository;

    /**
     * Return all active cities, ordered alphabetically by name.
     */
    public List<CityResponse> getActiveCities() {
        return cityRepository.findAllByStatusOrderByNameAsc("ACTIVE")
                .stream()
                .map(CityResponse::fromDomain)
                .toList();
    }

    /**
     * Return movies filtered by status. If status is null, default to RELEASED.
     */
    public List<MovieSummaryResponse> getMovies(MovieStatus status) {
        MovieStatus targetStatus = (status != null) ? status : MovieStatus.RELEASED;
        return movieRepository.findAllByStatus(targetStatus)
                .stream()
                .map(MovieSummaryResponse::fromDomain)
                .toList();
    }

    /**
     * Return movie details by ID, including its supported formats.
     * Throw ResourceNotFoundException if movie does not exist.
     */
    public MovieDetailResponse getMovieById(UUID movieId) {
        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new ResourceNotFoundException("Movie not found with ID: " + movieId));

        List<MovieFormatResponse> supportedFormats = Collections.emptyList();
        if (movie.getSupportedFormatIds() != null && !movie.getSupportedFormatIds().isEmpty()) {
            supportedFormats = movieFormatRepository.findAllById(movie.getSupportedFormatIds())
                    .stream()
                    .map(MovieFormatResponse::fromDomain)
                    .toList();
        }

        return MovieDetailResponse.fromDomain(movie, supportedFormats);
    }

    /**
     * Return all ACTIVE cinemas in a given city.
     */
    public List<CinemaResponse> getCinemasByCity(UUID cityId) {
        return cinemaRepository.findAllByCityIdAndStatus(cityId, "ACTIVE")
                .stream()
                .map(CinemaResponse::fromDomain)
                .toList();
    }

    /**
     * Return cinema by ID. Throw ResourceNotFoundException if not found.
     */
    public CinemaResponse getCinemaById(UUID cinemaId) {
        return cinemaRepository.findById(cinemaId)
                .map(CinemaResponse::fromDomain)
                .orElseThrow(() -> new ResourceNotFoundException("Cinema not found with ID: " + cinemaId));
    }

    /**
     * Return all screens in a given cinema. Throw ResourceNotFoundException if cinema doesn't exist.
     */
    public List<ScreenResponse> getScreensByCinema(UUID cinemaId) {
        if (!cinemaRepository.existsById(cinemaId)) {
            throw new ResourceNotFoundException("Cinema not found with ID: " + cinemaId);
        }

        return screenRepository.findAllByCinemaId(cinemaId)
                .stream()
                .map(ScreenResponse::fromDomain)
                .toList();
    }

    /**
     * Return the latest PUBLISHED seat map layout for a screen, with all its sections and seats grouped.
     * Throw ResourceNotFoundException if screen doesn't exist or if no published seat map exists.
     */
    public SeatMapResponse getActiveSeatMapForScreen(UUID screenId) {
        if (!screenRepository.existsById(screenId)) {
            throw new ResourceNotFoundException("Screen not found with ID: " + screenId);
        }

        SeatMapVersion version = seatMapVersionRepository
                .findFirstByScreenIdAndStatusOrderByVersionNumberDesc(screenId, SeatMapStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No published seat map found for screen ID: " + screenId));

        return buildSeatMapResponse(version);
    }

    /**
     * Helper to group seats into sections and build SeatMapResponse.
     */
    private SeatMapResponse buildSeatMapResponse(SeatMapVersion version) {
        List<SeatSection> sections = seatSectionRepository
                .findAllBySeatMapVersionIdOrderByDisplayOrderAsc(version.getId());

        List<PhysicalSeat> allSeats = physicalSeatRepository
                .findAllBySeatMapVersionIdOrderByRowLabelAscSeatNumberAsc(version.getId());

        // Senior Pattern: 1-line stream grouping
        Map<UUID, List<PhysicalSeatResponse>> seatsBySection = allSeats.stream()
                .collect(Collectors.groupingBy(
                        PhysicalSeat::getSectionId,
                        Collectors.mapping(PhysicalSeatResponse::fromDomain, Collectors.toList())
                ));

        List<SeatSectionResponse> sectionResponses = sections.stream()
                .map(sec -> SeatSectionResponse.fromDomain(
                        sec,
                        seatsBySection.getOrDefault(sec.getId(), Collections.emptyList())
                ))
                .toList();

        return SeatMapResponse.fromDomain(version, sectionResponses);
    }
}
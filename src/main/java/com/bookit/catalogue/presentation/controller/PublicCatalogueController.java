package com.bookit.catalogue.presentation.controller;

import com.bookit.catalogue.application.dto.*;
import com.bookit.catalogue.application.service.CatalogueQueryService;
import com.bookit.catalogue.domain.MovieStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/catalogue")
@RequiredArgsConstructor
@Tag(name = "Catalogue Discovery", description = "Public APIs for browsing cities, movies, cinemas, and seat layouts")
public class PublicCatalogueController {

    private final CatalogueQueryService catalogueQueryService;

    @GetMapping("/cities")
    @Operation(summary = "Get all active cities", description = "Retrieves all supported active cities sorted alphabetically")
    public ResponseEntity<List<CityResponse>> getActiveCities() {
        return ResponseEntity.ok(catalogueQueryService.getActiveCities());
    }

    @GetMapping("/movies")
    @Operation(summary = "Browse movies by status", description = "Retrieves movies filtered by status (defaults to RELEASED if omitted)")
    public ResponseEntity<List<MovieSummaryResponse>> getMovies(
            @RequestParam(name = "status", required = false) MovieStatus status
    ) {
        return ResponseEntity.ok(catalogueQueryService.getMovies(status));
    }

    @GetMapping("/movies/{movieId}")
    @Operation(summary = "Get movie details", description = "Retrieves full movie metadata including supported cinema formats")
    public ResponseEntity<MovieDetailResponse> getMovie(
            @PathVariable UUID movieId
    ) {
        return ResponseEntity.ok(catalogueQueryService.getMovieById(movieId));
    }

    @GetMapping("/cinemas")
    @Operation(summary = "Get active cinemas in a city", description = "Retrieves all active cinemas operating in the specified city")
    public ResponseEntity<List<CinemaResponse>> getCinemas(
            @RequestParam(name = "cityId") UUID cityId
    ) {
        return ResponseEntity.ok(catalogueQueryService.getCinemasByCity(cityId));
    }

    @GetMapping("/cinemas/{cinemaId}")
    @Operation(summary = "Get cinema details", description = "Retrieves metadata and location for a specific cinema")
    public ResponseEntity<CinemaResponse> getCinema(
            @PathVariable UUID cinemaId
    ) {
        return ResponseEntity.ok(catalogueQueryService.getCinemaById(cinemaId));
    }

    @GetMapping("/cinemas/{cinemaId}/screens")
    @Operation(summary = "Get screens in a cinema", description = "Retrieves all screens (e.g. IMAX, 4DX) operating in a cinema")
    public ResponseEntity<List<ScreenResponse>> getScreens(
            @PathVariable UUID cinemaId
    ) {
        return ResponseEntity.ok(catalogueQueryService.getScreensByCinema(cinemaId));
    }

    @GetMapping("/screens/{screenId}/seat-map")
    @Operation(summary = "Get active seat map layout", description = "Retrieves the currently published physical seat map layout for a screen")
    public ResponseEntity<SeatMapResponse> getSeatMap(
            @PathVariable UUID screenId
    ) {
        return ResponseEntity.ok(catalogueQueryService.getActiveSeatMapForScreen(screenId));
    }
}
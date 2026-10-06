package com.bookit.catalogue.presentation.controller;

import com.bookit.catalogue.application.dto.*;
import com.bookit.catalogue.application.service.CatalogueQueryService;
import com.bookit.catalogue.domain.MovieCertificate;
import com.bookit.catalogue.domain.MovieStatus;
import com.bookit.catalogue.domain.SeatMapStatus;
import com.bookit.catalogue.domain.SeatType;
import com.bookit.catalogue.domain.exception.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class PublicCatalogueControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CatalogueQueryService catalogueQueryService;

    // =========================================================================
    // 1. GET /api/v1/catalogue/cities
    // =========================================================================

    @Test
    @DisplayName("GET /api/v1/catalogue/cities should return 200 with active cities list publicly")
    void getActiveCities_shouldReturnOkWithCitiesList() throws Exception {
        UUID cityId = UUID.randomUUID();
        CityResponse delhi = new CityResponse(cityId, "Delhi", "Delhi", "IND", "Asia/Kolkata", "ACTIVE");
        when(catalogueQueryService.getActiveCities()).thenReturn(List.of(delhi));

        mockMvc.perform(get("/api/v1/catalogue/cities"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value(cityId.toString()))
                .andExpect(jsonPath("$[0].name").value("Delhi"))
                .andExpect(jsonPath("$[0].state").value("Delhi"));

        verify(catalogueQueryService).getActiveCities();
    }

    // =========================================================================
    // 2. GET /api/v1/catalogue/movies
    // =========================================================================

    @Test
    @DisplayName("GET /api/v1/catalogue/movies should return 200 with movies filtered by status")
    void getMovies_withStatusQueryParam_shouldReturnOkWithMovies() throws Exception {
        UUID movieId = UUID.randomUUID();
        MovieSummaryResponse movie = new MovieSummaryResponse(
                movieId, "Jawan", 165, MovieCertificate.UA,
                LocalDate.of(2023, 9, 7), MovieStatus.RELEASED,
                Set.of("HINDI"), Set.of("ACTION")
        );
        when(catalogueQueryService.getMovies(MovieStatus.RELEASED)).thenReturn(List.of(movie));

        mockMvc.perform(get("/api/v1/catalogue/movies")
                        .param("status", "RELEASED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(movieId.toString()))
                .andExpect(jsonPath("$[0].title").value("Jawan"))
                .andExpect(jsonPath("$[0].status").value("RELEASED"));

        verify(catalogueQueryService).getMovies(MovieStatus.RELEASED);
    }

    // =========================================================================
    // 3. GET /api/v1/catalogue/movies/{movieId}
    // =========================================================================

    @Test
    @DisplayName("GET /api/v1/catalogue/movies/{id} should return 200 with movie details when found")
    void getMovieById_whenMovieExists_shouldReturnOkWithDetails() throws Exception {
        UUID movieId = UUID.randomUUID();
        UUID formatId = UUID.randomUUID();
        MovieFormatResponse imaxFormat = new MovieFormatResponse(formatId, "IMAX_2D", "IMAX 2D");
        MovieDetailResponse movieDetail = new MovieDetailResponse(
                movieId, "Stree 2", "Horror comedy blockbuster", 147,
                MovieCertificate.UA, LocalDate.of(2024, 8, 15), MovieStatus.RELEASED,
                Set.of("HINDI"), Set.of("COMEDY", "HORROR"), List.of(imaxFormat)
        );

        when(catalogueQueryService.getMovieById(movieId)).thenReturn(movieDetail);

        mockMvc.perform(get("/api/v1/catalogue/movies/{movieId}", movieId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(movieId.toString()))
                .andExpect(jsonPath("$.title").value("Stree 2"))
                .andExpect(jsonPath("$.supportedFormats[0].code").value("IMAX_2D"));

        verify(catalogueQueryService).getMovieById(movieId);
    }

    @Test
    @DisplayName("GET /api/v1/catalogue/movies/{id} should return 404 with standard error body when not found")
    void getMovieById_whenNotFound_shouldReturn404WithApiErrorResponse() throws Exception {
        UUID missingMovieId = UUID.randomUUID();
        when(catalogueQueryService.getMovieById(missingMovieId))
                .thenThrow(new ResourceNotFoundException("Movie not found with id: " + missingMovieId));

        mockMvc.perform(get("/api/v1/catalogue/movies/{movieId}", missingMovieId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Movie not found with id: " + missingMovieId));

        verify(catalogueQueryService).getMovieById(missingMovieId);
    }

    // =========================================================================
    // 4. GET /api/v1/catalogue/cinemas?cityId={cityId}
    // =========================================================================

    @Test
    @DisplayName("GET /api/v1/catalogue/cinemas should return 200 with cinemas for given city")
    void getCinemasByCity_shouldReturnOkWithCinemas() throws Exception {
        UUID cityId = UUID.randomUUID();
        UUID cinemaId = UUID.randomUUID();
        CinemaResponse cinema = new CinemaResponse(
                cinemaId, UUID.randomUUID(), cityId, "PVR Select Citywalk Saket",
                "Saket District Centre, New Delhi", BigDecimal.valueOf(28.5284),
                BigDecimal.valueOf(77.2185), "Asia/Kolkata", "ACTIVE"
        );

        when(catalogueQueryService.getCinemasByCity(cityId)).thenReturn(List.of(cinema));

        mockMvc.perform(get("/api/v1/catalogue/cinemas")
                        .param("cityId", cityId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(cinemaId.toString()))
                .andExpect(jsonPath("$[0].name").value("PVR Select Citywalk Saket"))
                .andExpect(jsonPath("$[0].cityId").value(cityId.toString()));

        verify(catalogueQueryService).getCinemasByCity(cityId);
    }

    // =========================================================================
    // 5. GET /api/v1/catalogue/cinemas/{cinemaId}
    // =========================================================================

    @Test
    @DisplayName("GET /api/v1/catalogue/cinemas/{cinemaId} should return 200 with cinema detail")
    void getCinemaById_shouldReturnOkWithCinemaDetail() throws Exception {
        UUID cinemaId = UUID.randomUUID();
        CinemaResponse cinema = new CinemaResponse(
                cinemaId, UUID.randomUUID(), UUID.randomUUID(), "PVR Priya Vasant Vihar",
                "Basant Lok, New Delhi", BigDecimal.valueOf(28.5562),
                BigDecimal.valueOf(77.1571), "Asia/Kolkata", "ACTIVE"
        );

        when(catalogueQueryService.getCinemaById(cinemaId)).thenReturn(cinema);

        mockMvc.perform(get("/api/v1/catalogue/cinemas/{cinemaId}", cinemaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(cinemaId.toString()))
                .andExpect(jsonPath("$.name").value("PVR Priya Vasant Vihar"));

        verify(catalogueQueryService).getCinemaById(cinemaId);
    }

    // =========================================================================
    // 6. GET /api/v1/catalogue/cinemas/{cinemaId}/screens
    // =========================================================================

    @Test
    @DisplayName("GET /api/v1/catalogue/cinemas/{cinemaId}/screens should return 200 with screens")
    void getScreensByCinema_shouldReturnOkWithScreens() throws Exception {
        UUID cinemaId = UUID.randomUUID();
        UUID screenId = UUID.randomUUID();
        ScreenResponse screen = new ScreenResponse(screenId, cinemaId, "Audi 1 - IMAX", "ACTIVE");

        when(catalogueQueryService.getScreensByCinema(cinemaId)).thenReturn(List.of(screen));

        mockMvc.perform(get("/api/v1/catalogue/cinemas/{cinemaId}/screens", cinemaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(screenId.toString()))
                .andExpect(jsonPath("$[0].name").value("Audi 1 - IMAX"));

        verify(catalogueQueryService).getScreensByCinema(cinemaId);
    }

    // =========================================================================
    // 7. GET /api/v1/catalogue/screens/{screenId}/seat-map
    // =========================================================================

    @Test
    @DisplayName("GET /api/v1/catalogue/screens/{screenId}/seat-map should return 200 with active layout")
    void getActiveSeatMapForScreen_shouldReturnOkWithSeatMap() throws Exception {
        UUID screenId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        UUID sectionId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();

        PhysicalSeatResponse seat = new PhysicalSeatResponse(seatId, "A", 1, SeatType.REGULAR, com.bookit.catalogue.domain.SeatStatus.ACTIVE);
        SeatSectionResponse section = new SeatSectionResponse(sectionId, "PRIME", "PRIME", 1, List.of(seat));
        SeatMapResponse seatMap = new SeatMapResponse(
                versionId, screenId, 1, SeatMapStatus.PUBLISHED, 1, Instant.now(), List.of(section)
        );

        when(catalogueQueryService.getActiveSeatMapForScreen(screenId)).thenReturn(seatMap);

        mockMvc.perform(get("/api/v1/catalogue/screens/{screenId}/seat-map", screenId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(versionId.toString()))
                .andExpect(jsonPath("$.screenId").value(screenId.toString()))
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.totalSeats").value(1))
                .andExpect(jsonPath("$.sections[0].name").value("PRIME"))
                .andExpect(jsonPath("$.sections[0].seats[0].rowLabel").value("A"));

        verify(catalogueQueryService).getActiveSeatMapForScreen(screenId);
    }
}

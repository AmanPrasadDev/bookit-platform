package com.bookit.catalogue.application.service;

import com.bookit.catalogue.application.dto.*;
import com.bookit.catalogue.domain.*;
import com.bookit.catalogue.domain.exception.ResourceNotFoundException;
import com.bookit.catalogue.infrastructure.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CatalogueQueryServiceTest {

    @Mock
    private CityRepository cityRepository;

    @Mock
    private CinemaRepository cinemaRepository;

    @Mock
    private ScreenRepository screenRepository;

    @Mock
    private SeatMapVersionRepository seatMapVersionRepository;

    @Mock
    private MovieRepository movieRepository;

    @Mock
    private MovieFormatRepository movieFormatRepository;

    @Mock
    private SeatMapAssembler seatMapAssembler;

    @InjectMocks
    private CatalogueQueryService catalogueQueryService;

    // ==========================================
    // 1. getActiveCities()
    // ==========================================

    @Test
    @DisplayName("getActiveCities should return active cities mapped to CityResponse")
    void getActiveCities_whenCitiesExist_shouldReturnMappedList() {
        City delhi = buildCity("Delhi", "Delhi");
        City gurgaon = buildCity("Gurgaon", "Haryana");
        when(cityRepository.findAllByStatusOrderByNameAsc("ACTIVE"))
                .thenReturn(List.of(delhi, gurgaon));

        List<CityResponse> responses = catalogueQueryService.getActiveCities();

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).name()).isEqualTo("Delhi");
        assertThat(responses.get(1).name()).isEqualTo("Gurgaon");
        verify(cityRepository).findAllByStatusOrderByNameAsc("ACTIVE");
    }

    @Test
    @DisplayName("getActiveCities should return empty list when no active cities exist")
    void getActiveCities_whenNoneExist_shouldReturnEmptyList() {
        when(cityRepository.findAllByStatusOrderByNameAsc("ACTIVE"))
                .thenReturn(Collections.emptyList());

        List<CityResponse> responses = catalogueQueryService.getActiveCities();

        assertThat(responses).isEmpty();
        verify(cityRepository).findAllByStatusOrderByNameAsc("ACTIVE");
    }

    // ==========================================
    // 2. getMovies(MovieStatus status)
    // ==========================================

    @Test
    @DisplayName("getMovies should query by specified status when status is provided")
    void getMovies_withSpecificStatus_shouldQueryGivenStatus() {
        Movie movie = buildMovie("Jawan", MovieStatus.UPCOMING);
        when(movieRepository.findAllByStatus(MovieStatus.UPCOMING))
                .thenReturn(List.of(movie));

        List<MovieSummaryResponse> responses = catalogueQueryService.getMovies(MovieStatus.UPCOMING);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().title()).isEqualTo("Jawan");
        assertThat(responses.getFirst().status()).isEqualTo(MovieStatus.UPCOMING);
        verify(movieRepository).findAllByStatus(MovieStatus.UPCOMING);
    }

    @Test
    @DisplayName("getMovies should default to RELEASED when status is null")
    void getMovies_withNullStatus_shouldDefaultToReleased() {
        Movie movie = buildMovie("Stree 2", MovieStatus.RELEASED);
        when(movieRepository.findAllByStatus(MovieStatus.RELEASED))
                .thenReturn(List.of(movie));

        List<MovieSummaryResponse> responses = catalogueQueryService.getMovies(null);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().title()).isEqualTo("Stree 2");
        verify(movieRepository).findAllByStatus(MovieStatus.RELEASED);
    }

    // ==========================================
    // 3. getMovieById(UUID movieId)
    // ==========================================

    @Test
    @DisplayName("getMovieById should return movie details with supported formats when movie exists")
    void getMovieById_whenMovieFoundWithFormats_shouldReturnDetailWithFormats() {
        UUID movieId = UUID.randomUUID();
        UUID formatId = UUID.randomUUID();

        Movie movie = buildMovie("Kalki 2898 AD", MovieStatus.RELEASED);
        movie.setId(movieId);
        movie.setSupportedFormatIds(Set.of(formatId));

        MovieFormat imaxFormat = MovieFormat.builder()
                .id(formatId)
                .code("IMAX_2D")
                .displayName("IMAX 2D")
                .status("ACTIVE")
                .build();

        when(movieRepository.findById(movieId)).thenReturn(Optional.of(movie));
        when(movieFormatRepository.findAllById(Set.of(formatId))).thenReturn(List.of(imaxFormat));

        MovieDetailResponse response = catalogueQueryService.getMovieById(movieId);

        assertThat(response.id()).isEqualTo(movieId);
        assertThat(response.title()).isEqualTo("Kalki 2898 AD");
        assertThat(response.supportedFormats()).hasSize(1);
        assertThat(response.supportedFormats().getFirst().code()).isEqualTo("IMAX_2D");
        verify(movieRepository).findById(movieId);
        verify(movieFormatRepository).findAllById(Set.of(formatId));
    }

    @Test
    @DisplayName("getMovieById should not query format repository when movie has no supported format IDs")
    void getMovieById_whenMovieHasNoFormats_shouldNotQueryFormatRepository() {
        UUID movieId = UUID.randomUUID();
        Movie movie = buildMovie("Indie Film", MovieStatus.RELEASED);
        movie.setId(movieId);
        movie.setSupportedFormatIds(Collections.emptySet());

        when(movieRepository.findById(movieId)).thenReturn(Optional.of(movie));

        MovieDetailResponse response = catalogueQueryService.getMovieById(movieId);

        assertThat(response.id()).isEqualTo(movieId);
        assertThat(response.supportedFormats()).isEmpty();
        verify(movieRepository).findById(movieId);
        verifyNoInteractions(movieFormatRepository);
    }

    @Test
    @DisplayName("getMovieById should throw ResourceNotFoundException when movie does not exist")
    void getMovieById_whenNotFound_shouldThrowResourceNotFoundException() {
        UUID movieId = UUID.randomUUID();
        when(movieRepository.findById(movieId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> catalogueQueryService.getMovieById(movieId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(movieId.toString());

        verify(movieRepository).findById(movieId);
        verifyNoInteractions(movieFormatRepository);
    }

    // ==========================================
    // 4. getCinemasByCity(UUID cityId)
    // ==========================================

    @Test
    @DisplayName("getCinemasByCity should return active cinemas in given city")
    void getCinemasByCity_whenCinemasExist_shouldReturnActiveCinemas() {
        UUID cityId = UUID.randomUUID();
        Cinema saket = buildCinema("PVR Select Citywalk Saket", cityId);
        when(cinemaRepository.findAllByCityIdAndStatus(cityId, "ACTIVE"))
                .thenReturn(List.of(saket));

        List<CinemaResponse> responses = catalogueQueryService.getCinemasByCity(cityId);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().name()).isEqualTo("PVR Select Citywalk Saket");
        verify(cinemaRepository).findAllByCityIdAndStatus(cityId, "ACTIVE");
    }

    @Test
    @DisplayName("getCinemasByCity should return empty list when no active cinemas exist in city")
    void getCinemasByCity_whenNoneExist_shouldReturnEmptyList() {
        UUID cityId = UUID.randomUUID();
        when(cinemaRepository.findAllByCityIdAndStatus(cityId, "ACTIVE"))
                .thenReturn(Collections.emptyList());

        List<CinemaResponse> responses = catalogueQueryService.getCinemasByCity(cityId);

        assertThat(responses).isEmpty();
        verify(cinemaRepository).findAllByCityIdAndStatus(cityId, "ACTIVE");
    }

    // ==========================================
    // 5. getCinemaById(UUID cinemaId)
    // ==========================================

    @Test
    @DisplayName("getCinemaById should return CinemaResponse when cinema exists")
    void getCinemaById_whenCinemaExists_shouldReturnCinemaResponse() {
        UUID cinemaId = UUID.randomUUID();
        Cinema cinema = buildCinema("PVR Priya Vasant Vihar", UUID.randomUUID());
        cinema.setId(cinemaId);

        when(cinemaRepository.findById(cinemaId)).thenReturn(Optional.of(cinema));

        CinemaResponse response = catalogueQueryService.getCinemaById(cinemaId);

        assertThat(response.id()).isEqualTo(cinemaId);
        assertThat(response.name()).isEqualTo("PVR Priya Vasant Vihar");
        verify(cinemaRepository).findById(cinemaId);
    }

    @Test
    @DisplayName("getCinemaById should throw ResourceNotFoundException when cinema does not exist")
    void getCinemaById_whenNotFound_shouldThrowResourceNotFoundException() {
        UUID cinemaId = UUID.randomUUID();
        when(cinemaRepository.findById(cinemaId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> catalogueQueryService.getCinemaById(cinemaId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(cinemaId.toString());

        verify(cinemaRepository).findById(cinemaId);
    }

    // ==========================================
    // 6. getScreensByCinema(UUID cinemaId)
    // ==========================================

    @Test
    @DisplayName("getScreensByCinema should return screens when cinema exists")
    void getScreensByCinema_whenCinemaExists_shouldReturnScreens() {
        UUID cinemaId = UUID.randomUUID();
        Screen audi1 = Screen.builder().id(UUID.randomUUID()).cinemaId(cinemaId).name("Audi 1 - IMAX").build();
        Screen audi2 = Screen.builder().id(UUID.randomUUID()).cinemaId(cinemaId).name("Audi 2 - 4DX").build();

        when(cinemaRepository.existsById(cinemaId)).thenReturn(true);
        when(screenRepository.findAllByCinemaId(cinemaId)).thenReturn(List.of(audi1, audi2));

        List<ScreenResponse> responses = catalogueQueryService.getScreensByCinema(cinemaId);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).name()).isEqualTo("Audi 1 - IMAX");
        assertThat(responses.get(1).name()).isEqualTo("Audi 2 - 4DX");
        verify(cinemaRepository).existsById(cinemaId);
        verify(screenRepository).findAllByCinemaId(cinemaId);
    }

    @Test
    @DisplayName("getScreensByCinema should throw ResourceNotFoundException when cinema does not exist")
    void getScreensByCinema_whenCinemaNotFound_shouldThrowResourceNotFoundException() {
        UUID cinemaId = UUID.randomUUID();
        when(cinemaRepository.existsById(cinemaId)).thenReturn(false);

        assertThatThrownBy(() -> catalogueQueryService.getScreensByCinema(cinemaId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(cinemaId.toString());

        verify(cinemaRepository).existsById(cinemaId);
        verifyNoInteractions(screenRepository);
    }

    // ==========================================
    // 7. getActiveSeatMapForScreen(UUID screenId)
    // ==========================================

    @Test
    @DisplayName("getActiveSeatMapForScreen should throw ResourceNotFoundException when screen does not exist")
    void getActiveSeatMapForScreen_whenScreenNotFound_shouldThrowResourceNotFoundException() {
        UUID screenId = UUID.randomUUID();
        when(screenRepository.existsById(screenId)).thenReturn(false);

        assertThatThrownBy(() -> catalogueQueryService.getActiveSeatMapForScreen(screenId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(screenId.toString());

        verify(screenRepository).existsById(screenId);
        verifyNoInteractions(seatMapVersionRepository, seatMapAssembler);
    }

    @Test
    @DisplayName("getActiveSeatMapForScreen should throw ResourceNotFoundException when screen has no published seat map")
    void getActiveSeatMapForScreen_whenNoPublishedSeatMap_shouldThrowResourceNotFoundException() {
        UUID screenId = UUID.randomUUID();
        when(screenRepository.existsById(screenId)).thenReturn(true);
        when(seatMapVersionRepository.findFirstByScreenIdAndStatusOrderByVersionNumberDesc(screenId, SeatMapStatus.PUBLISHED))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> catalogueQueryService.getActiveSeatMapForScreen(screenId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(screenId.toString());

        verify(screenRepository).existsById(screenId);
        verify(seatMapVersionRepository).findFirstByScreenIdAndStatusOrderByVersionNumberDesc(screenId, SeatMapStatus.PUBLISHED);
        verifyNoInteractions(seatMapAssembler);
    }

    @Test
    @DisplayName("getActiveSeatMapForScreen should delegate to SeatMapAssembler when published seat map exists")
    void getActiveSeatMapForScreen_whenPublishedMapExists_shouldDelegateToAssembler() {
        UUID screenId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();

        SeatMapVersion version = SeatMapVersion.builder()
                .id(versionId)
                .screenId(screenId)
                .versionNumber(1)
                .status(SeatMapStatus.PUBLISHED)
                .totalSeats(150)
                .publishedAt(Instant.now())
                .build();

        SeatMapResponse expectedResponse = new SeatMapResponse(
                versionId, screenId, 1, SeatMapStatus.PUBLISHED, 150, version.getPublishedAt(), Collections.emptyList()
        );

        when(screenRepository.existsById(screenId)).thenReturn(true);
        when(seatMapVersionRepository.findFirstByScreenIdAndStatusOrderByVersionNumberDesc(screenId, SeatMapStatus.PUBLISHED))
                .thenReturn(Optional.of(version));
        when(seatMapAssembler.toResponse(version)).thenReturn(expectedResponse);

        SeatMapResponse actualResponse = catalogueQueryService.getActiveSeatMapForScreen(screenId);

        assertThat(actualResponse).isSameAs(expectedResponse);
        verify(screenRepository).existsById(screenId);
        verify(seatMapVersionRepository).findFirstByScreenIdAndStatusOrderByVersionNumberDesc(screenId, SeatMapStatus.PUBLISHED);
        verify(seatMapAssembler).toResponse(version);
    }

    // ==========================================
    // Private Helpers (Delhi NCR Venues & Movies)
    // ==========================================

    private static City buildCity(String name, String state) {
        return City.builder()
                .id(UUID.randomUUID())
                .name(name)
                .state(state)
                .countryCode("IND")
                .timeZone("Asia/Kolkata")
                .status("ACTIVE")
                .build();
    }

    private static Movie buildMovie(String title, MovieStatus status) {
        return Movie.builder()
                .id(UUID.randomUUID())
                .title(title)
                .synopsis("High-stakes drama with gripping action.")
                .runtimeMinutes(160)
                .certificate(MovieCertificate.UA)
                .releaseDate(LocalDate.of(2024, 6, 27))
                .status(status)
                .languages(Set.of("HINDI", "TAMIL"))
                .genres(Set.of("ACTION", "SCI_FI"))
                .supportedFormatIds(new HashSet<>())
                .build();
    }

    private static Cinema buildCinema(String name, UUID cityId) {
        return Cinema.builder()
                .id(UUID.randomUUID())
                .organizationId(UUID.randomUUID())
                .cityId(cityId)
                .name(name)
                .address("Delhi NCR, India")
                .latitude(BigDecimal.valueOf(28.5284))
                .longitude(BigDecimal.valueOf(77.2185))
                .timeZone("Asia/Kolkata")
                .status("ACTIVE")
                .build();
    }
}

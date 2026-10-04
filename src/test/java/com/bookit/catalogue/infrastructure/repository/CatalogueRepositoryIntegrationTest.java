package com.bookit.catalogue.infrastructure.repository;

import com.bookit.catalogue.domain.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
class CatalogueRepositoryIntegrationTest {

    @Autowired
    private CityRepository cityRepository;

    @Autowired
    private OperatorOrganizationRepository organizationRepository;

    @Autowired
    private CinemaRepository cinemaRepository;

    @Autowired
    private ScreenRepository screenRepository;

    @Autowired
    private SeatMapVersionRepository seatMapVersionRepository;

    @Autowired
    private SeatSectionRepository seatSectionRepository;

    @Autowired
    private PhysicalSeatRepository physicalSeatRepository;

    @Autowired
    private MovieFormatRepository movieFormatRepository;

    @Autowired
    private MovieRepository movieRepository;

    // Helper method: persists Delhi venue hierarchy down to screen
    private Screen createStandardDelhiScreen() {
        City delhi = cityRepository.save(City.builder()
                .id(UUID.randomUUID())
                .name("Delhi")
                .state("Delhi")
                .countryCode("IND")
                .build());

        OperatorOrganization pvr = organizationRepository.save(OperatorOrganization.builder()
                .id(UUID.randomUUID())
                .legalName("PVR INOX Limited")
                .displayName("PVR INOX")
                .contactEmail("contact@pvr.com")
                .build());

        Cinema saket = cinemaRepository.save(Cinema.builder()
                .id(UUID.randomUUID())
                .organizationId(pvr.getId())
                .cityId(delhi.getId())
                .name("PVR Select Citywalk Saket")
                .address("A-3, District Centre, Saket, New Delhi")
                .build());

        return screenRepository.save(Screen.builder()
                .id(UUID.randomUUID())
                .cinemaId(saket.getId())
                .name("Audi 1 - IMAX")
                .build());
    }

    @Test
    @DisplayName("Should persist and query venue hierarchy: City -> Organization -> Cinema -> Screen")
    void shouldPersistAndQueryVenueHierarchy() {
        Screen screen = createStandardDelhiScreen();
        Cinema cinema = cinemaRepository.findById(screen.getCinemaId()).orElseThrow();
        City city = cityRepository.findById(cinema.getCityId()).orElseThrow();
        OperatorOrganization org = organizationRepository.findById(cinema.getOrganizationId()).orElseThrow();

        // City queries
        Optional<City> cityOpt = cityRepository.findByNameIgnoreCase("delhi");
        assertThat(cityOpt).isPresent();
        assertThat(cityOpt.get().getState()).isEqualTo("Delhi");

        List<City> activeCities = cityRepository.findAllByStatusOrderByNameAsc("ACTIVE");
        assertThat(activeCities).isNotEmpty();

        // Organization queries
        Optional<OperatorOrganization> orgOpt = organizationRepository.findByLegalName("PVR INOX Limited");
        assertThat(orgOpt).isPresent();
        assertThat(orgOpt.get().getDisplayName()).isEqualTo("PVR INOX");

        // Cinema queries
        List<Cinema> delhiCinemas = cinemaRepository.findAllByCityIdAndStatus(city.getId(), "ACTIVE");
        assertThat(delhiCinemas).hasSize(1);
        assertThat(delhiCinemas.getFirst().getName()).isEqualTo("PVR Select Citywalk Saket");

        List<Cinema> orgCinemas = cinemaRepository.findAllByOrganizationId(org.getId());
        assertThat(orgCinemas).hasSize(1);

        Optional<Cinema> cinemaOpt = cinemaRepository.findByOrganizationIdAndName(org.getId(), "PVR Select Citywalk Saket");
        assertThat(cinemaOpt).isPresent();

        // Screen queries
        List<Screen> screens = screenRepository.findAllByCinemaId(cinema.getId());
        assertThat(screens).hasSize(1);
        assertThat(screens.getFirst().getName()).isEqualTo("Audi 1 - IMAX");

        Optional<Screen> screenOpt = screenRepository.findByCinemaIdAndName(cinema.getId(), "Audi 1 - IMAX");
        assertThat(screenOpt).isPresent();
    }

    @Test
    @DisplayName("Should find latest published seat map version for a screen")
    void shouldFindLatestPublishedSeatMapVersion() {
        Screen screen = createStandardDelhiScreen();

        seatMapVersionRepository.save(SeatMapVersion.builder()
                .id(UUID.randomUUID())
                .screenId(screen.getId())
                .versionNumber(1)
                .status(SeatMapStatus.DRAFT)
                .totalSeats(150)
                .build());

        SeatMapVersion v2Published = seatMapVersionRepository.save(SeatMapVersion.builder()
                .id(UUID.randomUUID())
                .screenId(screen.getId())
                .versionNumber(2)
                .status(SeatMapStatus.PUBLISHED)
                .totalSeats(200)
                .build());

        // Query latest published version
        Optional<SeatMapVersion> latestPublished = seatMapVersionRepository
                .findFirstByScreenIdAndStatusOrderByVersionNumberDesc(screen.getId(), SeatMapStatus.PUBLISHED);

        assertThat(latestPublished).isPresent();
        assertThat(latestPublished.get().getId()).isEqualTo(v2Published.getId());
        assertThat(latestPublished.get().getVersionNumber()).isEqualTo(2);
        assertThat(latestPublished.get().getTotalSeats()).isEqualTo(200);

        // Query by exact screen and version number
        Optional<SeatMapVersion> exactV1 = seatMapVersionRepository.findByScreenIdAndVersionNumber(screen.getId(), 1);
        assertThat(exactV1).isPresent();
        assertThat(exactV1.get().getStatus()).isEqualTo(SeatMapStatus.DRAFT);
    }

    @Test
    @DisplayName("Should order seat sections by display order ascending")
    void shouldOrderSeatSectionsByDisplayOrderAscending() {
        Screen screen = createStandardDelhiScreen();

        SeatMapVersion version = seatMapVersionRepository.save(SeatMapVersion.builder()
                .id(UUID.randomUUID())
                .screenId(screen.getId())
                .versionNumber(1)
                .status(SeatMapStatus.PUBLISHED)
                .totalSeats(120)
                .build());

        // Save sections out of order
        seatSectionRepository.save(SeatSection.builder()
                .id(UUID.randomUUID())
                .seatMapVersionId(version.getId())
                .name("Classic")
                .categoryCode("CLASSIC")
                .displayOrder(3)
                .build());

        seatSectionRepository.save(SeatSection.builder()
                .id(UUID.randomUUID())
                .seatMapVersionId(version.getId())
                .name("Recliner")
                .categoryCode("RECLINER")
                .displayOrder(1)
                .build());

        seatSectionRepository.save(SeatSection.builder()
                .id(UUID.randomUUID())
                .seatMapVersionId(version.getId())
                .name("Prime")
                .categoryCode("PRIME")
                .displayOrder(2)
                .build());

        List<SeatSection> orderedSections = seatSectionRepository
                .findAllBySeatMapVersionIdOrderByDisplayOrderAsc(version.getId());

        assertThat(orderedSections).hasSize(3);
        assertThat(orderedSections.get(0).getCategoryCode()).isEqualTo("RECLINER");
        assertThat(orderedSections.get(1).getCategoryCode()).isEqualTo("PRIME");
        assertThat(orderedSections.get(2).getCategoryCode()).isEqualTo("CLASSIC");
    }

    @Test
    @DisplayName("Should persist and sort physical seats by row label and seat number ascending")
    void shouldPersistAndSortPhysicalSeatsByRowAndNumber() {
        Screen screen = createStandardDelhiScreen();

        SeatMapVersion version = seatMapVersionRepository.save(SeatMapVersion.builder()
                .id(UUID.randomUUID())
                .screenId(screen.getId())
                .versionNumber(1)
                .status(SeatMapStatus.PUBLISHED)
                .totalSeats(4)
                .build());

        SeatSection section = seatSectionRepository.save(SeatSection.builder()
                .id(UUID.randomUUID())
                .seatMapVersionId(version.getId())
                .name("Prime")
                .categoryCode("PRIME")
                .displayOrder(1)
                .build());

        // Save seats out of order (B1, A2, A1, B2)
        physicalSeatRepository.save(PhysicalSeat.builder()
                .id(UUID.randomUUID())
                .seatMapVersionId(version.getId())
                .sectionId(section.getId())
                .rowLabel("B")
                .seatNumber(1)
                .build());

        physicalSeatRepository.save(PhysicalSeat.builder()
                .id(UUID.randomUUID())
                .seatMapVersionId(version.getId())
                .sectionId(section.getId())
                .rowLabel("A")
                .seatNumber(2)
                .build());

        physicalSeatRepository.save(PhysicalSeat.builder()
                .id(UUID.randomUUID())
                .seatMapVersionId(version.getId())
                .sectionId(section.getId())
                .rowLabel("A")
                .seatNumber(1)
                .build());

        physicalSeatRepository.save(PhysicalSeat.builder()
                .id(UUID.randomUUID())
                .seatMapVersionId(version.getId())
                .sectionId(section.getId())
                .rowLabel("B")
                .seatNumber(2)
                .build());

        List<PhysicalSeat> sortedSeats = physicalSeatRepository
                .findAllBySeatMapVersionIdOrderByRowLabelAscSeatNumberAsc(version.getId());

        assertThat(sortedSeats).hasSize(4);
        assertThat(sortedSeats.get(0).getRowLabel()).isEqualTo("A");
        assertThat(sortedSeats.get(0).getSeatNumber()).isEqualTo(1);

        assertThat(sortedSeats.get(1).getRowLabel()).isEqualTo("A");
        assertThat(sortedSeats.get(1).getSeatNumber()).isEqualTo(2);

        assertThat(sortedSeats.get(2).getRowLabel()).isEqualTo("B");
        assertThat(sortedSeats.get(2).getSeatNumber()).isEqualTo(1);

        assertThat(sortedSeats.get(3).getRowLabel()).isEqualTo("B");
        assertThat(sortedSeats.get(3).getSeatNumber()).isEqualTo(2);

        long count = physicalSeatRepository.countBySeatMapVersionId(version.getId());
        assertThat(count).isEqualTo(4);
    }

    @Test
    @DisplayName("Should reject duplicate physical seat in same seat map version with DataIntegrityViolationException")
    void shouldThrowExceptionWhenDuplicatePhysicalSeatInSameVersion() {
        Screen screen = createStandardDelhiScreen();

        SeatMapVersion version = seatMapVersionRepository.save(SeatMapVersion.builder()
                .id(UUID.randomUUID())
                .screenId(screen.getId())
                .versionNumber(1)
                .status(SeatMapStatus.DRAFT)
                .totalSeats(10)
                .build());

        SeatSection section = seatSectionRepository.save(SeatSection.builder()
                .id(UUID.randomUUID())
                .seatMapVersionId(version.getId())
                .name("Prime")
                .categoryCode("PRIME")
                .displayOrder(1)
                .build());

        PhysicalSeat seat1 = PhysicalSeat.builder()
                .id(UUID.randomUUID())
                .seatMapVersionId(version.getId())
                .sectionId(section.getId())
                .rowLabel("A")
                .seatNumber(1)
                .build();
        physicalSeatRepository.saveAndFlush(seat1);

        PhysicalSeat duplicateSeat = PhysicalSeat.builder()
                .id(UUID.randomUUID())
                .seatMapVersionId(version.getId())
                .sectionId(section.getId())
                .rowLabel("A")
                .seatNumber(1) // Duplicate (version, row, number)
                .build();

        assertThatThrownBy(() -> physicalSeatRepository.saveAndFlush(duplicateSeat))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Should persist and query movie formats")
    void shouldPersistAndQueryMovieFormats() {
        movieFormatRepository.save(MovieFormat.builder()
                .id(UUID.randomUUID())
                .code("IMAX_2D")
                .displayName("IMAX 2D")
                .status("ACTIVE")
                .build());

        movieFormatRepository.save(MovieFormat.builder()
                .id(UUID.randomUUID())
                .code("4DX")
                .displayName("4DX Experience")
                .status("ACTIVE")
                .build());

        Optional<MovieFormat> foundByCode = movieFormatRepository.findByCode("IMAX_2D");
        assertThat(foundByCode).isPresent();
        assertThat(foundByCode.get().getDisplayName()).isEqualTo("IMAX 2D");

        List<MovieFormat> activeFormats = movieFormatRepository.findAllByStatus("ACTIVE");
        assertThat(activeFormats).extracting(MovieFormat::getCode).contains("IMAX_2D", "4DX");
    }

    @Test
    @DisplayName("Should persist and query movies with collections (languages, genres, supported formats)")
    void shouldPersistAndQueryMoviesWithCollections() {
        MovieFormat imax = movieFormatRepository.save(MovieFormat.builder()
                .id(UUID.randomUUID())
                .code("IMAX_3D")
                .displayName("IMAX 3D")
                .status("ACTIVE")
                .build());

        Movie movie = Movie.builder()
                .id(UUID.randomUUID())
                .title("Jawan")
                .synopsis("A high-octane action thriller highlighting social justice.")
                .runtimeMinutes(169)
                .certificate(MovieCertificate.UA)
                .releaseDate(LocalDate.of(2023, 9, 7))
                .status(MovieStatus.RELEASED)
                .languages(Set.of("HINDI", "TAMIL", "TELUGU"))
                .genres(Set.of("ACTION", "THRILLER"))
                .supportedFormatIds(Set.of(imax.getId()))
                .build();

        movieRepository.save(movie);

        // Query by status
        List<Movie> releasedMovies = movieRepository.findAllByStatus(MovieStatus.RELEASED);
        assertThat(releasedMovies).isNotEmpty();

        // Query by title case-insensitively
        Optional<Movie> foundMovie = movieRepository.findByTitleIgnoreCase("jawan");
        assertThat(foundMovie).isPresent();
        Movie persisted = foundMovie.get();

        assertThat(persisted.getTitle()).isEqualTo("Jawan");
        assertThat(persisted.getRuntimeMinutes()).isEqualTo(169);
        assertThat(persisted.getCertificate()).isEqualTo(MovieCertificate.UA);
        assertThat(persisted.getReleaseDate()).isEqualTo(LocalDate.of(2023, 9, 7));
        assertThat(persisted.getLanguages()).containsExactlyInAnyOrder("HINDI", "TAMIL", "TELUGU");
        assertThat(persisted.getGenres()).containsExactlyInAnyOrder("ACTION", "THRILLER");
        assertThat(persisted.getSupportedFormatIds()).containsExactly(imax.getId());
    }

    @Test
    @DisplayName("Should return Optional.empty when querying non-existent entities")
    void shouldReturnEmptyOptionalWhenQueryingNonExistentEntities() {
        Screen screen = createStandardDelhiScreen();

        // 1. Non-existent city
        assertThat(cityRepository.findByNameIgnoreCase("Atlantis")).isEmpty();

        // 2. Non-existent organization
        assertThat(organizationRepository.findByLegalName("NonExistent Org")).isEmpty();

        // 3. Non-existent cinema
        assertThat(cinemaRepository.findByOrganizationIdAndName(UUID.randomUUID(), "Ghost Cinema")).isEmpty();

        // 4. Non-existent screen
        assertThat(screenRepository.findByCinemaIdAndName(screen.getCinemaId(), "Audi 999")).isEmpty();

        // 5. Non-existent seat map version
        assertThat(seatMapVersionRepository.findByScreenIdAndVersionNumber(screen.getId(), 999)).isEmpty();

        // 6. Non-existent published layout (when screen only has DRAFT)
        seatMapVersionRepository.save(SeatMapVersion.builder()
                .id(UUID.randomUUID())
                .screenId(screen.getId())
                .versionNumber(1)
                .status(SeatMapStatus.DRAFT)
                .totalSeats(50)
                .build());

        assertThat(seatMapVersionRepository.findFirstByScreenIdAndStatusOrderByVersionNumberDesc(
                screen.getId(), SeatMapStatus.PUBLISHED)).isEmpty();

        // 7. Non-existent format
        assertThat(movieFormatRepository.findByCode("NON_EXISTENT_FORMAT")).isEmpty();

        // 8. Non-existent movie title
        assertThat(movieRepository.findByTitleIgnoreCase("Ghost Movie Not In Catalog")).isEmpty();
    }

    @Test
    @DisplayName("Should filter out inactive records and isolate data across venues")
    void shouldFilterOutInactiveAndIsolateDataAcrossVenues() {
        City delhi = cityRepository.save(City.builder()
                .id(UUID.randomUUID())
                .name("Delhi")
                .state("Delhi")
                .countryCode("IND")
                .build());

        OperatorOrganization pvr = organizationRepository.save(OperatorOrganization.builder()
                .id(UUID.randomUUID())
                .legalName("PVR INOX Limited")
                .displayName("PVR INOX")
                .contactEmail("contact@pvr.com")
                .build());

        // Cinema 1: ACTIVE (Saket)
        Cinema saket = cinemaRepository.save(Cinema.builder()
                .id(UUID.randomUUID())
                .organizationId(pvr.getId())
                .cityId(delhi.getId())
                .name("PVR Select Citywalk Saket")
                .address("Saket, New Delhi")
                .status("ACTIVE")
                .build());

        // Cinema 2: INACTIVE (Priya - temporarily closed for renovation)
        Cinema priya = cinemaRepository.save(Cinema.builder()
                .id(UUID.randomUUID())
                .organizationId(pvr.getId())
                .cityId(delhi.getId())
                .name("PVR Priya Vasant Vihar")
                .address("Vasant Vihar, New Delhi")
                .status("INACTIVE")
                .build());

        // Screen 1 in Saket, Screen 2 in Priya
        Screen saketAudi1 = screenRepository.save(Screen.builder()
                .id(UUID.randomUUID())
                .cinemaId(saket.getId())
                .name("Audi 1")
                .build());

        Screen priyaAudi1 = screenRepository.save(Screen.builder()
                .id(UUID.randomUUID())
                .cinemaId(priya.getId())
                .name("Audi 1")
                .build());

        // Assert status filtering: only ACTIVE cinemas in Delhi returned
        List<Cinema> activeDelhiCinemas = cinemaRepository.findAllByCityIdAndStatus(delhi.getId(), "ACTIVE");
        assertThat(activeDelhiCinemas)
                .extracting(Cinema::getId)
                .contains(saket.getId())
                .doesNotContain(priya.getId());

        // Assert venue isolation: screens of Saket should NOT include screens of Priya
        List<Screen> saketScreens = screenRepository.findAllByCinemaId(saket.getId());
        assertThat(saketScreens)
                .extracting(Screen::getId)
                .contains(saketAudi1.getId())
                .doesNotContain(priyaAudi1.getId());

        // Movie format status filtering
        MovieFormat activeFormat = movieFormatRepository.save(MovieFormat.builder()
                .id(UUID.randomUUID())
                .code("IMAX_LASER")
                .displayName("IMAX with Laser")
                .status("ACTIVE")
                .build());

        MovieFormat inactiveFormat = movieFormatRepository.save(MovieFormat.builder()
                .id(UUID.randomUUID())
                .code("ANALOG_35MM")
                .displayName("Analog 35mm")
                .status("INACTIVE")
                .build());

        List<MovieFormat> activeFormats = movieFormatRepository.findAllByStatus("ACTIVE");
        assertThat(activeFormats)
                .extracting(MovieFormat::getCode)
                .contains(activeFormat.getCode())
                .doesNotContain(inactiveFormat.getCode());

        // Movie status segregation
        Movie releasedMovie = movieRepository.save(Movie.builder()
                .id(UUID.randomUUID())
                .title("Stree 2")
                .runtimeMinutes(147)
                .releaseDate(LocalDate.of(2024, 8, 15))
                .status(MovieStatus.RELEASED)
                .build());

        Movie upcomingMovie = movieRepository.save(Movie.builder()
                .id(UUID.randomUUID())
                .title("War 2")
                .runtimeMinutes(160)
                .releaseDate(LocalDate.of(2025, 8, 14))
                .status(MovieStatus.UPCOMING)
                .build());

        List<Movie> released = movieRepository.findAllByStatus(MovieStatus.RELEASED);
        assertThat(released)
                .extracting(Movie::getTitle)
                .contains(releasedMovie.getTitle())
                .doesNotContain(upcomingMovie.getTitle());

        List<Movie> upcoming = movieRepository.findAllByStatus(MovieStatus.UPCOMING);
        assertThat(upcoming)
                .extracting(Movie::getTitle)
                .contains(upcomingMovie.getTitle())
                .doesNotContain(releasedMovie.getTitle());
    }

    @Test
    @DisplayName("Should return zero count and empty list for a layout with no physical seats")
    void shouldReturnZeroCountAndEmptyListForLayoutWithNoSeats() {
        Screen screen = createStandardDelhiScreen();

        SeatMapVersion emptyVersion = seatMapVersionRepository.save(SeatMapVersion.builder()
                .id(UUID.randomUUID())
                .screenId(screen.getId())
                .versionNumber(1)
                .status(SeatMapStatus.DRAFT)
                .totalSeats(0)
                .build());

        List<PhysicalSeat> seats = physicalSeatRepository
                .findAllBySeatMapVersionIdOrderByRowLabelAscSeatNumberAsc(emptyVersion.getId());
        assertThat(seats).isEmpty();

        long count = physicalSeatRepository.countBySeatMapVersionId(emptyVersion.getId());
        assertThat(count).isEqualTo(0L);
    }

    @Test
    @DisplayName("Should reject duplicate screen name in same cinema with DataIntegrityViolationException")
    void shouldRejectDuplicateScreenNameInSameCinema() {
        Screen screen1 = createStandardDelhiScreen();

        // Attempt to create another screen with the same name in the same cinema
        Screen duplicateScreen = Screen.builder()
                .id(UUID.randomUUID())
                .cinemaId(screen1.getCinemaId())
                .name(screen1.getName()) // Duplicate (cinema_id, name)
                .build();

        assertThatThrownBy(() -> screenRepository.saveAndFlush(duplicateScreen))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Should reject duplicate city name and state with DataIntegrityViolationException")
    void shouldRejectDuplicateCityNameAndState() {
        cityRepository.saveAndFlush(City.builder()
                .id(UUID.randomUUID())
                .name("New Delhi")
                .state("Delhi")
                .countryCode("IND")
                .build());

        City duplicateCity = City.builder()
                .id(UUID.randomUUID())
                .name("New Delhi")
                .state("Delhi") // Duplicate (name, state)
                .countryCode("IND")
                .build();

        assertThatThrownBy(() -> cityRepository.saveAndFlush(duplicateCity))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
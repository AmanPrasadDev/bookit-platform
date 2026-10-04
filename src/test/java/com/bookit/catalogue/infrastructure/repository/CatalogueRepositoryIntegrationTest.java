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

        SeatMapVersion v1Draft = seatMapVersionRepository.save(SeatMapVersion.builder()
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

        Optional<SeatMapVersion> latestPublished = seatMapVersionRepository
                .findFirstByScreenIdAndStatusOrderByVersionNumberDesc(screen.getId(), SeatMapStatus.PUBLISHED);

        assertThat(latestPublished).isPresent();
        assertThat(latestPublished.get().getId()).isEqualTo(v2Published.getId());
        assertThat(latestPublished.get().getVersionNumber()).isEqualTo(2);
        assertThat(latestPublished.get().getTotalSeats()).isEqualTo(200);

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

        // Save sections intentionally out of order
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
}
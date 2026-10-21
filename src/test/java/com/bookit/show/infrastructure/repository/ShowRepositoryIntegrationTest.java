package com.bookit.show.infrastructure.repository;

import com.bookit.catalogue.domain.*;
import com.bookit.catalogue.infrastructure.repository.*;
import com.bookit.show.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
class ShowRepositoryIntegrationTest {

    @Autowired
    private ShowRepository showRepository;

    @Autowired
    private ShowPriceConfigurationRepository priceConfigRepository;

    @Autowired
    private ShowSeatPriceRepository seatPriceRepository;

    @Autowired
    private ShowChargeRuleRepository chargeRuleRepository;

    @Autowired
    private ShowSeatRepository showSeatRepository;

    // Repositories needed to set up foreign key prerequisites
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
    private MovieRepository movieRepository;

    @Autowired
    private MovieFormatRepository movieFormatRepository;

    private Screen primaryScreen;
    private Screen secondaryScreen;
    private Movie sampleMovie;
    private MovieFormat imaxFormat;
    private SeatMapVersion publishedSeatMap;
    private SeatSection reclinerSection;
    private SeatSection primeSection;
    private PhysicalSeat physicalSeat1;
    private PhysicalSeat physicalSeat2;

    @BeforeEach
    void setUp() {
        // 1. Setup City & Org
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

        // 2. Setup Cinema & Screens
        Cinema saket = cinemaRepository.save(Cinema.builder()
                .id(UUID.randomUUID())
                .organizationId(pvr.getId())
                .cityId(delhi.getId())
                .name("PVR Select Citywalk Saket")
                .address("A-3, District Centre, Saket, New Delhi")
                .build());

        primaryScreen = screenRepository.save(Screen.builder()
                .id(UUID.randomUUID())
                .cinemaId(saket.getId())
                .name("Audi 1 - IMAX")
                .build());

        secondaryScreen = screenRepository.save(Screen.builder()
                .id(UUID.randomUUID())
                .cinemaId(saket.getId())
                .name("Audi 2 - Dolby Atmos")
                .build());

        // 3. Setup SeatMapVersion, Sections & Physical Seats
        publishedSeatMap = seatMapVersionRepository.save(SeatMapVersion.builder()
                .id(UUID.randomUUID())
                .screenId(primaryScreen.getId())
                .versionNumber(1)
                .status(SeatMapStatus.PUBLISHED)
                .totalSeats(2)
                .build());

        reclinerSection = seatSectionRepository.save(SeatSection.builder()
                .id(UUID.randomUUID())
                .seatMapVersionId(publishedSeatMap.getId())
                .name("Recliner")
                .categoryCode("RECLINER")
                .displayOrder(1)
                .build());

        primeSection = seatSectionRepository.save(SeatSection.builder()
                .id(UUID.randomUUID())
                .seatMapVersionId(publishedSeatMap.getId())
                .name("Prime")
                .categoryCode("PRIME")
                .displayOrder(2)
                .build());

        physicalSeat1 = physicalSeatRepository.save(PhysicalSeat.builder()
                .id(UUID.randomUUID())
                .seatMapVersionId(publishedSeatMap.getId())
                .sectionId(reclinerSection.getId())
                .rowLabel("A")
                .seatNumber(1)
                .seatType(SeatType.RECLINER)
                .build());

        physicalSeat2 = physicalSeatRepository.save(PhysicalSeat.builder()
                .id(UUID.randomUUID())
                .seatMapVersionId(publishedSeatMap.getId())
                .sectionId(primeSection.getId())
                .rowLabel("B")
                .seatNumber(1)
                .seatType(SeatType.REGULAR)
                .build());

        // 4. Setup Format & Movie
        imaxFormat = movieFormatRepository.save(MovieFormat.builder()
                .id(UUID.randomUUID())
                .code("IMAX_2D")
                .displayName("IMAX 2D")
                .status("ACTIVE")
                .build());

        sampleMovie = movieRepository.save(Movie.builder()
                .id(UUID.randomUUID())
                .title("Interstellar")
                .synopsis("Mankind was born on Earth. It was never meant to die here.")
                .runtimeMinutes(169)
                .certificate(MovieCertificate.UA)
                .releaseDate(LocalDate.of(2014, 11, 7))
                .status(MovieStatus.RELEASED)
                .languages(Set.of("ENGLISH", "HINDI"))
                .genres(Set.of("SCI_FI", "ADVENTURE"))
                .supportedFormatIds(Set.of(imaxFormat.getId()))
                .build());
    }

    private Show buildSampleShow(Screen screen, Instant startsAt, Instant endsAt, ShowStatus status) {
        return Show.builder()
                .id(UUID.randomUUID())
                .movieId(sampleMovie.getId())
                .screenId(screen.getId())
                .seatMapVersionId(publishedSeatMap.getId())
                .formatId(imaxFormat.getId())
                .startsAt(startsAt)
                .endsAt(endsAt)
                .bookingOpensAt(startsAt.minus(3, ChronoUnit.DAYS))
                .bookingClosesAt(startsAt.plus(15, ChronoUnit.MINUTES))
                .status(status)
                .build();
    }

    // =========================================================================
    // SECTION 1: SHOW REPOSITORY QUERIES & CRUD
    // =========================================================================

    @Test
    @DisplayName("Should persist and query Show by ID, Screen, Movie, and Status")
    void shouldPersistAndQueryShow() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant startsAt = now.plus(1, ChronoUnit.DAYS);
        Instant endsAt = startsAt.plus(3, ChronoUnit.HOURS);

        Show show = buildSampleShow(primaryScreen, startsAt, endsAt, ShowStatus.DRAFT);
        Show saved = showRepository.saveAndFlush(show);

        // Find by ID
        Optional<Show> foundOpt = showRepository.findById(saved.getId());
        assertThat(foundOpt).isPresent();
        assertThat(foundOpt.get().getStatus()).isEqualTo(ShowStatus.DRAFT);
        assertThat(foundOpt.get().getStartsAt()).isEqualTo(startsAt);
        assertThat(foundOpt.get().getEndsAt()).isEqualTo(endsAt);

        // Find by Screen ID
        List<Show> screenShows = showRepository.findByScreenId(primaryScreen.getId());
        assertThat(screenShows).hasSize(1);
        assertThat(screenShows.getFirst().getId()).isEqualTo(saved.getId());

        // Find by Movie ID
        List<Show> movieShows = showRepository.findByMovieId(sampleMovie.getId());
        assertThat(movieShows).hasSize(1);
        assertThat(movieShows.getFirst().getId()).isEqualTo(saved.getId());

        // Find by Screen ID and Status
        List<Show> draftShows = showRepository.findByScreenIdAndStatus(primaryScreen.getId(), ShowStatus.DRAFT);
        assertThat(draftShows).hasSize(1);
        List<Show> publishedShows = showRepository.findByScreenIdAndStatus(primaryScreen.getId(), ShowStatus.PUBLISHED);
        assertThat(publishedShows).isEmpty();

        // Find by Movie ID and Status
        List<Show> movieDrafts = showRepository.findByMovieIdAndStatus(sampleMovie.getId(), ShowStatus.DRAFT);
        assertThat(movieDrafts).hasSize(1);

        // Find by Screen ID and starts_at between
        List<Show> rangeShows = showRepository.findByScreenIdAndStartsAtBetween(
                primaryScreen.getId(),
                startsAt.minus(1, ChronoUnit.HOURS),
                startsAt.plus(1, ChronoUnit.HOURS)
        );
        assertThat(rangeShows).hasSize(1);
    }

    // =========================================================================
    // SECTION 2: POSTGRESQL GiST EXCLUSION CONSTRAINT (no_overlapping_screen_shows)
    // =========================================================================

    @Test
    @DisplayName("Should reject overlapping show on the same screen via PostgreSQL GiST exclusion constraint")
    void shouldThrowExceptionWhenShowsOverlapOnSameScreen() {
        Instant baseTime = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant show1Start = baseTime;
        Instant show1End = baseTime.plus(3, ChronoUnit.HOURS); // 18:00 - 21:00

        Show show1 = buildSampleShow(primaryScreen, show1Start, show1End, ShowStatus.PUBLISHED);
        showRepository.saveAndFlush(show1);

        // Overlapping show: 19:00 - 22:00 on the EXACT same screen
        Instant show2Start = baseTime.plus(1, ChronoUnit.HOURS);
        Instant show2End = baseTime.plus(4, ChronoUnit.HOURS);
        Show overlappingShow = buildSampleShow(primaryScreen, show2Start, show2End, ShowStatus.PUBLISHED);

        assertThatThrownBy(() -> showRepository.saveAndFlush(overlappingShow))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Should reject show completely enclosed inside an existing show's time range on the same screen")
    void shouldThrowExceptionWhenShowIsCompletelyEnclosed() {
        Instant baseTime = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant outerStart = baseTime;
        Instant outerEnd = baseTime.plus(4, ChronoUnit.HOURS); // 12:00 - 16:00

        Show outerShow = buildSampleShow(primaryScreen, outerStart, outerEnd, ShowStatus.DRAFT);
        showRepository.saveAndFlush(outerShow);

        // Enclosed show: 13:00 - 15:00 on the same screen
        Instant innerStart = baseTime.plus(1, ChronoUnit.HOURS);
        Instant innerEnd = baseTime.plus(3, ChronoUnit.HOURS);
        Show innerShow = buildSampleShow(primaryScreen, innerStart, innerEnd, ShowStatus.DRAFT);

        assertThatThrownBy(() -> showRepository.saveAndFlush(innerShow))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Should permit back-to-back adjacent shows because interval is half-open [)")
    void shouldPermitBackToBackShowsOnSameScreen() {
        Instant baseTime = Instant.now().plus(4, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant show1Start = baseTime;
        Instant show1End = baseTime.plus(2, ChronoUnit.HOURS); // 10:00 - 12:00

        Show show1 = buildSampleShow(primaryScreen, show1Start, show1End, ShowStatus.PUBLISHED);
        showRepository.saveAndFlush(show1);

        // Back-to-back show starts EXACTLY when Show 1 ends: 12:00 - 14:30
        Instant show2Start = show1End;
        Instant show2End = show1End.plus(2, ChronoUnit.HOURS).plus(30, ChronoUnit.MINUTES);
        Show show2 = buildSampleShow(primaryScreen, show2Start, show2End, ShowStatus.PUBLISHED);

        // Should succeed without throwing DataIntegrityViolationException
        Show saved2 = showRepository.saveAndFlush(show2);
        assertThat(saved2.getId()).isNotNull();

        List<Show> screenShows = showRepository.findByScreenId(primaryScreen.getId());
        assertThat(screenShows).hasSize(2);
    }

    @Test
    @DisplayName("Should permit simultaneous shows if scheduled on DIFFERENT screens")
    void shouldPermitSimultaneousShowsOnDifferentScreens() {
        Instant baseTime = Instant.now().plus(5, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant startsAt = baseTime;
        Instant endsAt = baseTime.plus(3, ChronoUnit.HOURS);

        Show showScreen1 = buildSampleShow(primaryScreen, startsAt, endsAt, ShowStatus.PUBLISHED);
        Show showScreen2 = buildSampleShow(secondaryScreen, startsAt, endsAt, ShowStatus.PUBLISHED);

        showRepository.saveAndFlush(showScreen1);
        Show saved2 = showRepository.saveAndFlush(showScreen2);

        assertThat(saved2.getId()).isNotNull();
        assertThat(showRepository.findByScreenId(primaryScreen.getId())).hasSize(1);
        assertThat(showRepository.findByScreenId(secondaryScreen.getId())).hasSize(1);
    }

    @Test
    @DisplayName("Should permit overlapping show if previous show was CANCELLED")
    void shouldPermitOverlappingShowWhenPreviousShowIsCancelled() {
        Instant baseTime = Instant.now().plus(6, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant startsAt = baseTime;
        Instant endsAt = baseTime.plus(3, ChronoUnit.HOURS);

        // Cancelled show on primary screen
        Show cancelledShow = buildSampleShow(primaryScreen, startsAt, endsAt, ShowStatus.CANCELLED);
        showRepository.saveAndFlush(cancelledShow);

        // Replacement show at the EXACT SAME time slot on the same screen
        Show replacementShow = buildSampleShow(primaryScreen, startsAt, endsAt, ShowStatus.PUBLISHED);
        Show saved = showRepository.saveAndFlush(replacementShow);

        assertThat(saved.getId()).isNotNull();
        List<Show> screenShows = showRepository.findByScreenId(primaryScreen.getId());
        assertThat(screenShows).hasSize(2);
    }

    // =========================================================================
    // SECTION 3: PRICING CONFIGURATION, SEAT PRICES & CHARGE RULES
    // =========================================================================

    @Test
    @DisplayName("Should persist and query pricing configuration, seat prices, and charge rules")
    void shouldPersistAndQueryPricingConfiguration() {
        Instant now = Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Show show = showRepository.saveAndFlush(buildSampleShow(primaryScreen, now, now.plus(2, ChronoUnit.HOURS), ShowStatus.DRAFT));

        // 1. Create Price Configuration
        ShowPriceConfiguration config = ShowPriceConfiguration.builder()
                .id(UUID.randomUUID())
                .showId(show.getId())
                .versionNumber(1)
                .status(PriceConfigurationStatus.ACTIVE)
                .build();
        priceConfigRepository.saveAndFlush(config);

        Optional<ShowPriceConfiguration> activeConfig = priceConfigRepository.findByShowIdAndStatus(show.getId(), PriceConfigurationStatus.ACTIVE);
        assertThat(activeConfig).isPresent();
        assertThat(activeConfig.get().getVersionNumber()).isEqualTo(1);

        // 2. Add Seat Prices (Recliner = 450, Prime = 300)
        ShowSeatPrice reclinerPrice = ShowSeatPrice.builder()
                .id(UUID.randomUUID())
                .priceConfigurationId(config.getId())
                .sectionCategoryCode("RECLINER")
                .priceAmount(new BigDecimal("450.0000"))
                .currency("INR")
                .build();

        ShowSeatPrice primePrice = ShowSeatPrice.builder()
                .id(UUID.randomUUID())
                .priceConfigurationId(config.getId())
                .sectionCategoryCode("PRIME")
                .priceAmount(new BigDecimal("300.0000"))
                .currency("INR")
                .build();

        seatPriceRepository.saveAllAndFlush(List.of(reclinerPrice, primePrice));

        List<ShowSeatPrice> prices = seatPriceRepository.findByPriceConfigurationId(config.getId());
        assertThat(prices).hasSize(2);

        Optional<ShowSeatPrice> reclinerOpt = seatPriceRepository.findByPriceConfigurationIdAndSectionCategoryCode(config.getId(), "RECLINER");
        assertThat(reclinerOpt).isPresent();
        assertThat(reclinerOpt.get().getPriceAmount()).isEqualByComparingTo(new BigDecimal("450.0000"));

        // 3. Add Charge Rules (Convenience fee = 30, GST = 18%)
        ShowChargeRule convenienceFee = ShowChargeRule.builder()
                .id(UUID.randomUUID())
                .priceConfigurationId(config.getId())
                .chargeType("CONVENIENCE_FEE")
                .amount(new BigDecimal("30.0000"))
                .currency("INR")
                .build();

        ShowChargeRule gst = ShowChargeRule.builder()
                .id(UUID.randomUUID())
                .priceConfigurationId(config.getId())
                .chargeType("GST")
                .amount(BigDecimal.ZERO)
                .ratePercentage(new BigDecimal("18.00"))
                .currency("INR")
                .build();

        chargeRuleRepository.saveAllAndFlush(List.of(convenienceFee, gst));

        List<ShowChargeRule> rules = chargeRuleRepository.findByPriceConfigurationId(config.getId());
        assertThat(rules).hasSize(2);
    }

    // =========================================================================
    // SECTION 4: MATERIALIZED INVENTORY (SHOW_SEAT) & STATE MACHINE
    // =========================================================================

    @Test
    @DisplayName("Should persist and query materialized ShowSeats, count by state, and update state transitions")
    void shouldPersistAndManageShowSeatsInventory() {
        Instant now = Instant.now().plus(8, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Show show = showRepository.saveAndFlush(buildSampleShow(primaryScreen, now, now.plus(2, ChronoUnit.HOURS), ShowStatus.PUBLISHED));

        // Materialize two seats for this show
        ShowSeat seat1 = ShowSeat.builder()
                .id(UUID.randomUUID())
                .showId(show.getId())
                .physicalSeatId(physicalSeat1.getId())
                .seatSectionId(reclinerSection.getId())
                .rowLabel("A")
                .seatNumber(1)
                .seatType("VIP")
                .state(ShowSeatState.AVAILABLE)
                .build();

        ShowSeat seat2 = ShowSeat.builder()
                .id(UUID.randomUUID())
                .showId(show.getId())
                .physicalSeatId(physicalSeat2.getId())
                .seatSectionId(primeSection.getId())
                .rowLabel("B")
                .seatNumber(1)
                .seatType("REGULAR")
                .state(ShowSeatState.AVAILABLE)
                .build();

        showSeatRepository.saveAllAndFlush(List.of(seat1, seat2));

        // Query by Show ID
        List<ShowSeat> allSeats = showSeatRepository.findByShowId(show.getId());
        assertThat(allSeats).hasSize(2);
        assertThat(showSeatRepository.countByShowId(show.getId())).isEqualTo(2);

        // Count by state AVAILABLE
        assertThat(showSeatRepository.countByShowIdAndState(show.getId(), ShowSeatState.AVAILABLE)).isEqualTo(2);
        assertThat(showSeatRepository.countByShowIdAndState(show.getId(), ShowSeatState.HELD)).isZero();

        // Query by physical seat ID
        Optional<ShowSeat> seat1Opt = showSeatRepository.findByShowIdAndPhysicalSeatId(show.getId(), physicalSeat1.getId());
        assertThat(seat1Opt).isPresent();
        assertThat(seat1Opt.get().getRowLabel()).isEqualTo("A");

        // Query by collection of physical seat IDs
        List<ShowSeat> seatList = showSeatRepository.findByShowIdAndPhysicalSeatIdIn(
                show.getId(), List.of(physicalSeat1.getId(), physicalSeat2.getId()));
        assertThat(seatList).hasSize(2);

        // State Transition: AVAILABLE -> HELD
        UUID holdId = UUID.randomUUID();
        ShowSeat seatToHold = seat1Opt.get();
        seatToHold.hold(holdId);
        showSeatRepository.saveAndFlush(seatToHold);

        // Verify count updated
        assertThat(showSeatRepository.countByShowIdAndState(show.getId(), ShowSeatState.AVAILABLE)).isEqualTo(1);
        assertThat(showSeatRepository.countByShowIdAndState(show.getId(), ShowSeatState.HELD)).isEqualTo(1);

        List<ShowSeat> heldSeats = showSeatRepository.findByShowIdAndState(show.getId(), ShowSeatState.HELD);
        assertThat(heldSeats).hasSize(1);
        assertThat(heldSeats.getFirst().getActiveHoldId()).isEqualTo(holdId);

        // State Transition: HELD -> SOLD
        UUID bookingId = UUID.randomUUID();
        seatToHold.sell(bookingId);
        showSeatRepository.saveAndFlush(seatToHold);

        assertThat(showSeatRepository.countByShowIdAndState(show.getId(), ShowSeatState.HELD)).isZero();
        assertThat(showSeatRepository.countByShowIdAndState(show.getId(), ShowSeatState.SOLD)).isEqualTo(1);
        assertThat(seatToHold.getSoldBookingId()).isEqualTo(bookingId);
    }
}

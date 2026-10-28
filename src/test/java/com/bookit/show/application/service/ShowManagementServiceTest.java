package com.bookit.show.application.service;

import com.bookit.catalogue.domain.*;
import com.bookit.catalogue.domain.exception.ResourceNotFoundException;
import com.bookit.catalogue.infrastructure.repository.*;
import com.bookit.show.application.dto.ConfigureShowPricesRequest;
import com.bookit.show.application.dto.CreateShowRequest;
import com.bookit.show.application.dto.ShowResponse;
import com.bookit.show.domain.*;
import com.bookit.show.domain.exception.ScheduleConflictException;
import com.bookit.show.infrastructure.repository.ShowPriceConfigurationRepository;
import com.bookit.show.infrastructure.repository.ShowRepository;
import com.bookit.show.infrastructure.repository.ShowSeatPriceRepository;
import com.bookit.show.infrastructure.repository.ShowSeatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShowManagementServiceTest {

    @Mock
    private ShowRepository showRepository;
    @Mock
    private ShowPriceConfigurationRepository priceConfigurationRepository;
    @Mock
    private ShowSeatPriceRepository seatPriceRepository;
    @Mock
    private ShowSeatRepository showSeatRepository;
    @Mock
    private MovieRepository movieRepository;
    @Mock
    private MovieFormatRepository movieFormatRepository;
    @Mock
    private ScreenRepository screenRepository;
    @Mock
    private SeatMapVersionRepository seatMapVersionRepository;
    @Mock
    private SeatSectionRepository seatSectionRepository;
    @Mock
    private PhysicalSeatRepository physicalSeatRepository;

    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-10-27T10:00:00Z"), ZoneId.of("UTC"));

    @InjectMocks
    private ShowManagementService showManagementService;

    private UUID movieId;
    private UUID screenId;
    private UUID seatMapVersionId;
    private UUID formatId;
    private Movie movie;
    private SeatMapVersion seatMapVersion;

    @BeforeEach
    void setUp() {
        movieId = UUID.randomUUID();
        screenId = UUID.randomUUID();
        seatMapVersionId = UUID.randomUUID();
        formatId = UUID.randomUUID();

        movie = Movie.builder()
                .id(movieId)
                .title("Interstellar")
                .runtimeMinutes(169)
                .supportedFormatIds(Set.of(formatId))
                .releaseDate(LocalDate.of(2014, 11, 7))
                .build();

        seatMapVersion = SeatMapVersion.builder()
                .id(seatMapVersionId)
                .screenId(screenId)
                .versionNumber(1)
                .status(SeatMapStatus.PUBLISHED)
                .build();
    }

    private CreateShowRequest createValidRequest() {
        Instant startsAt = Instant.now(clock).plus(1, ChronoUnit.DAYS);
        return new CreateShowRequest(
                movieId, screenId, seatMapVersionId, formatId,
                startsAt,
                startsAt.minus(2, ChronoUnit.DAYS),
                startsAt.plus(15, ChronoUnit.MINUTES)
        );
    }

    // =========================================================================
    // 1. CREATE DRAFT SHOW
    // =========================================================================
    @Nested
    @DisplayName("createDraftShow")
    class CreateDraftShowTests {

        @Test
        @DisplayName("Positive: Should schedule draft show with endsAt = startsAt + runtime + 15m buffer")
        void shouldCreateDraftShowSuccessfully() {
            CreateShowRequest request = createValidRequest();

            when(movieRepository.findById(movieId)).thenReturn(Optional.of(movie));
            when(screenRepository.existsById(screenId)).thenReturn(true);
            when(seatMapVersionRepository.findById(seatMapVersionId)).thenReturn(Optional.of(seatMapVersion));
            when(movieFormatRepository.existsById(formatId)).thenReturn(true);
            when(showRepository.saveAndFlush(any(Show.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ShowResponse response = showManagementService.createDraftShow(request);

            assertThat(response).isNotNull();
            assertThat(response.status()).isEqualTo(ShowStatus.DRAFT);
            assertThat(response.movieId()).isEqualTo(movieId);
            assertThat(response.screenId()).isEqualTo(screenId);
            assertThat(response.endsAt()).isEqualTo(request.startsAt().plus(169 + 15, ChronoUnit.MINUTES));
        }

        @Test
        @DisplayName("Negative: Should throw ResourceNotFoundException when movie does not exist")
        void shouldThrowWhenMovieNotFound() {
            CreateShowRequest request = createValidRequest();
            when(movieRepository.findById(movieId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> showManagementService.createDraftShow(request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Movie not found");
        }

        @Test
        @DisplayName("Negative: Should throw ResourceNotFoundException when screen does not exist")
        void shouldThrowWhenScreenNotFound() {
            CreateShowRequest request = createValidRequest();
            when(movieRepository.findById(movieId)).thenReturn(Optional.of(movie));
            when(screenRepository.existsById(screenId)).thenReturn(false);

            assertThatThrownBy(() -> showManagementService.createDraftShow(request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Screen not found");
        }

        @Test
        @DisplayName("Negative: Should throw ResourceNotFoundException when seat map version does not exist")
        void shouldThrowWhenSeatMapVersionNotFound() {
            CreateShowRequest request = createValidRequest();
            when(movieRepository.findById(movieId)).thenReturn(Optional.of(movie));
            when(screenRepository.existsById(screenId)).thenReturn(true);
            when(seatMapVersionRepository.findById(seatMapVersionId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> showManagementService.createDraftShow(request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Seat map version not found");
        }

        @Test
        @DisplayName("Negative: Should throw IllegalStateException when seat map version belongs to a different screen")
        void shouldThrowWhenSeatMapBelongsToDifferentScreen() {
            CreateShowRequest request = createValidRequest();
            SeatMapVersion differentScreenMap = SeatMapVersion.builder()
                    .id(seatMapVersionId)
                    .screenId(UUID.randomUUID()) // Different screen
                    .status(SeatMapStatus.PUBLISHED)
                    .build();

            when(movieRepository.findById(movieId)).thenReturn(Optional.of(movie));
            when(screenRepository.existsById(screenId)).thenReturn(true);
            when(seatMapVersionRepository.findById(seatMapVersionId)).thenReturn(Optional.of(differentScreenMap));

            assertThatThrownBy(() -> showManagementService.createDraftShow(request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("does not belong to the specified screen");
        }

        @Test
        @DisplayName("Negative: Should throw IllegalStateException when seat map version is not PUBLISHED")
        void shouldThrowWhenSeatMapNotPublished() {
            CreateShowRequest request = createValidRequest();
            SeatMapVersion draftMap = SeatMapVersion.builder()
                    .id(seatMapVersionId)
                    .screenId(screenId)
                    .status(SeatMapStatus.DRAFT) // Draft, not published!
                    .build();

            when(movieRepository.findById(movieId)).thenReturn(Optional.of(movie));
            when(screenRepository.existsById(screenId)).thenReturn(true);
            when(seatMapVersionRepository.findById(seatMapVersionId)).thenReturn(Optional.of(draftMap));

            assertThatThrownBy(() -> showManagementService.createDraftShow(request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("unsealed/draft seat map version");
        }

        @Test
        @DisplayName("Negative: Should throw ResourceNotFoundException when movie format does not exist")
        void shouldThrowWhenMovieFormatNotFound() {
            CreateShowRequest request = createValidRequest();
            when(movieRepository.findById(movieId)).thenReturn(Optional.of(movie));
            when(screenRepository.existsById(screenId)).thenReturn(true);
            when(seatMapVersionRepository.findById(seatMapVersionId)).thenReturn(Optional.of(seatMapVersion));
            when(movieFormatRepository.existsById(formatId)).thenReturn(false);

            assertThatThrownBy(() -> showManagementService.createDraftShow(request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Movie format not found");
        }

        @Test
        @DisplayName("Negative: Should throw IllegalArgumentException when movie does not support requested format")
        void shouldThrowWhenMovieDoesNotSupportFormat() {
            CreateShowRequest request = createValidRequest();
            Movie movieWithoutImax = Movie.builder()
                    .id(movieId)
                    .title("Interstellar")
                    .runtimeMinutes(169)
                    .supportedFormatIds(Set.of(UUID.randomUUID())) // Does NOT contain formatId
                    .build();

            when(movieRepository.findById(movieId)).thenReturn(Optional.of(movieWithoutImax));
            when(screenRepository.existsById(screenId)).thenReturn(true);
            when(seatMapVersionRepository.findById(seatMapVersionId)).thenReturn(Optional.of(seatMapVersion));
            when(movieFormatRepository.existsById(formatId)).thenReturn(true);

            assertThatThrownBy(() -> showManagementService.createDraftShow(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("is not supported by movie");
        }

        @Test
        @DisplayName("Negative: Should throw ScheduleConflictException when PostgreSQL exclusion constraint is violated")
        void shouldThrowScheduleConflictOnDatabaseExclusionViolation() {
            CreateShowRequest request = createValidRequest();

            when(movieRepository.findById(movieId)).thenReturn(Optional.of(movie));
            when(screenRepository.existsById(screenId)).thenReturn(true);
            when(seatMapVersionRepository.findById(seatMapVersionId)).thenReturn(Optional.of(seatMapVersion));
            when(movieFormatRepository.existsById(formatId)).thenReturn(true);
            when(showRepository.saveAndFlush(any(Show.class)))
                    .thenThrow(new DataIntegrityViolationException("conflicting key value violates exclusion constraint"));

            assertThatThrownBy(() -> showManagementService.createDraftShow(request))
                    .isInstanceOf(ScheduleConflictException.class)
                    .hasMessageContaining("overlapping show");
        }
    }

    // =========================================================================
    // 2. CONFIGURE PRICES
    // =========================================================================
    @Nested
    @DisplayName("configurePrices")
    class ConfigurePricesTests {

        private UUID showId;
        private Show draftShow;
        private SeatSection recliner;
        private SeatSection prime;

        @BeforeEach
        void init() {
            showId = UUID.randomUUID();
            draftShow = Show.builder()
                    .id(showId)
                    .seatMapVersionId(seatMapVersionId)
                    .status(ShowStatus.DRAFT)
                    .build();

            recliner = SeatSection.builder()
                    .id(UUID.randomUUID())
                    .categoryCode("RECLINER")
                    .seatMapVersionId(seatMapVersionId)
                    .build();

            prime = SeatSection.builder()
                    .id(UUID.randomUUID())
                    .categoryCode("PRIME")
                    .seatMapVersionId(seatMapVersionId)
                    .build();
        }

        @Test
        @DisplayName("Positive: Should configure initial prices (version 1)")
        void shouldConfigureInitialPricesSuccessfully() {
            ConfigureShowPricesRequest request = new ConfigureShowPricesRequest(List.of(
                    new ConfigureShowPricesRequest.CategoryPrice("RECLINER", new BigDecimal("450.0000")),
                    new ConfigureShowPricesRequest.CategoryPrice("PRIME", new BigDecimal("300.0000"))
            ));

            when(showRepository.findById(showId)).thenReturn(Optional.of(draftShow));
            when(seatSectionRepository.findAllBySeatMapVersionIdOrderByDisplayOrderAsc(seatMapVersionId))
                    .thenReturn(List.of(recliner, prime));
            when(priceConfigurationRepository.findByShowIdAndStatus(showId, PriceConfigurationStatus.ACTIVE))
                    .thenReturn(Optional.empty());

            ShowResponse response = showManagementService.configurePrices(showId, request);

            assertThat(response).isNotNull();
            ArgumentCaptor<ShowPriceConfiguration> configCaptor = ArgumentCaptor.forClass(ShowPriceConfiguration.class);
            verify(priceConfigurationRepository).save(configCaptor.capture());
            assertThat(configCaptor.getValue().getVersionNumber()).isEqualTo(1);
            assertThat(configCaptor.getValue().getStatus()).isEqualTo(PriceConfigurationStatus.ACTIVE);

            verify(seatPriceRepository).saveAll(anyList());
        }

        @Test
        @DisplayName("Positive: Should archive previous price configuration and create version 2 on price update")
        void shouldArchivePreviousConfigOnPriceUpdate() {
            ShowPriceConfiguration existingActiveConfig = ShowPriceConfiguration.builder()
                    .id(UUID.randomUUID())
                    .showId(showId)
                    .versionNumber(1)
                    .status(PriceConfigurationStatus.ACTIVE)
                    .build();

            ConfigureShowPricesRequest request = new ConfigureShowPricesRequest(List.of(
                    new ConfigureShowPricesRequest.CategoryPrice("RECLINER", new BigDecimal("500.0000")),
                    new ConfigureShowPricesRequest.CategoryPrice("PRIME", new BigDecimal("350.0000"))
            ));

            when(showRepository.findById(showId)).thenReturn(Optional.of(draftShow));
            when(seatSectionRepository.findAllBySeatMapVersionIdOrderByDisplayOrderAsc(seatMapVersionId))
                    .thenReturn(List.of(recliner, prime));
            when(priceConfigurationRepository.findByShowIdAndStatus(showId, PriceConfigurationStatus.ACTIVE))
                    .thenReturn(Optional.of(existingActiveConfig));

            showManagementService.configurePrices(showId, request);

            assertThat(existingActiveConfig.getStatus()).isEqualTo(PriceConfigurationStatus.ARCHIVED);
            verify(priceConfigurationRepository).save(existingActiveConfig);

            ArgumentCaptor<ShowPriceConfiguration> newConfigCaptor = ArgumentCaptor.forClass(ShowPriceConfiguration.class);
            verify(priceConfigurationRepository, times(2)).save(newConfigCaptor.capture());
            ShowPriceConfiguration createdConfig = newConfigCaptor.getAllValues().get(1);
            assertThat(createdConfig.getVersionNumber()).isEqualTo(2);
        }

        @Test
        @DisplayName("Negative: Should throw ResourceNotFoundException when show does not exist")
        void shouldThrowWhenShowNotFound() {
            ConfigureShowPricesRequest request = new ConfigureShowPricesRequest(List.of(
                    new ConfigureShowPricesRequest.CategoryPrice("RECLINER", new BigDecimal("450.0000"))
            ));
            when(showRepository.findById(showId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> showManagementService.configurePrices(showId, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Show not found");
        }

        @Test
        @DisplayName("Negative: Should throw IllegalStateException when show is not in DRAFT status")
        void shouldThrowWhenShowNotDraft() {
            Show publishedShow = Show.builder()
                    .id(showId)
                    .status(ShowStatus.PUBLISHED)
                    .build();

            ConfigureShowPricesRequest request = new ConfigureShowPricesRequest(List.of(
                    new ConfigureShowPricesRequest.CategoryPrice("RECLINER", new BigDecimal("450.0000"))
            ));
            when(showRepository.findById(showId)).thenReturn(Optional.of(publishedShow));

            assertThatThrownBy(() -> showManagementService.configurePrices(showId, request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Pricing can only be configured for shows in DRAFT status");
        }

        @Test
        @DisplayName("Negative: Should throw IllegalArgumentException when price request has unknown category code")
        void shouldThrowWhenCategoryCodeUnknown() {
            ConfigureShowPricesRequest request = new ConfigureShowPricesRequest(List.of(
                    new ConfigureShowPricesRequest.CategoryPrice("RECLINER", new BigDecimal("450.0000")),
                    new ConfigureShowPricesRequest.CategoryPrice("PRIME", new BigDecimal("300.0000")),
                    new ConfigureShowPricesRequest.CategoryPrice("GOLD_BALCONY", new BigDecimal("600.0000")) // Not in layout!
            ));

            when(showRepository.findById(showId)).thenReturn(Optional.of(draftShow));
            when(seatSectionRepository.findAllBySeatMapVersionIdOrderByDisplayOrderAsc(seatMapVersionId))
                    .thenReturn(List.of(recliner, prime));

            assertThatThrownBy(() -> showManagementService.configurePrices(showId, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not present in this show's seat map layout");
        }

        @Test
        @DisplayName("Negative: Should throw IllegalArgumentException when a required section category price is missing")
        void shouldThrowWhenRequiredCategoryPriceMissing() {
            // Missing 'PRIME' price!
            ConfigureShowPricesRequest request = new ConfigureShowPricesRequest(List.of(
                    new ConfigureShowPricesRequest.CategoryPrice("RECLINER", new BigDecimal("450.0000"))
            ));

            when(showRepository.findById(showId)).thenReturn(Optional.of(draftShow));
            when(seatSectionRepository.findAllBySeatMapVersionIdOrderByDisplayOrderAsc(seatMapVersionId))
                    .thenReturn(List.of(recliner, prime));

            assertThatThrownBy(() -> showManagementService.configurePrices(showId, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Missing required price for seat section category: PRIME");
        }
    }

    // =========================================================================
    // 3. PUBLISH SHOW & MATERIALIZE INVENTORY
    // =========================================================================
    @Nested
    @DisplayName("publishShow")
    class PublishShowTests {

        private UUID showId;
        private Show draftShow;
        private ShowPriceConfiguration activeConfig;

        @BeforeEach
        void init() {
            showId = UUID.randomUUID();
            Instant startsAt = Instant.now(clock).plus(1, ChronoUnit.DAYS);
            draftShow = Show.builder()
                    .id(showId)
                    .seatMapVersionId(seatMapVersionId)
                    .status(ShowStatus.DRAFT)
                    .startsAt(startsAt)
                    .endsAt(startsAt.plus(3, ChronoUnit.HOURS))
                    .bookingOpensAt(startsAt.minus(2, ChronoUnit.DAYS))
                    .bookingClosesAt(startsAt.plus(15, ChronoUnit.MINUTES))
                    .build();

            activeConfig = ShowPriceConfiguration.builder()
                    .id(UUID.randomUUID())
                    .showId(showId)
                    .status(PriceConfigurationStatus.ACTIVE)
                    .build();
        }

        @Test
        @DisplayName("Positive: Should publish show and materialize physical seats into show_seat inventory")
        void shouldPublishAndMaterializeSeats() {
            PhysicalSeat regularSeat = PhysicalSeat.builder()
                    .id(UUID.randomUUID())
                    .sectionId(UUID.randomUUID())
                    .rowLabel("A")
                    .seatNumber(1)
                    .seatType(SeatType.REGULAR)
                    .status(SeatStatus.ACTIVE)
                    .build();

            PhysicalSeat brokenSeat = PhysicalSeat.builder()
                    .id(UUID.randomUUID())
                    .sectionId(UUID.randomUUID())
                    .rowLabel("A")
                    .seatNumber(2)
                    .seatType(SeatType.REGULAR)
                    .status(SeatStatus.INACTIVE) // Inactive physical seat should materialize as BLOCKED
                    .build();

            when(showRepository.findById(showId)).thenReturn(Optional.of(draftShow));
            when(priceConfigurationRepository.findByShowIdAndStatus(showId, PriceConfigurationStatus.ACTIVE))
                    .thenReturn(Optional.of(activeConfig));
            when(physicalSeatRepository.findAllBySeatMapVersionIdOrderByRowLabelAscSeatNumberAsc(seatMapVersionId))
                    .thenReturn(List.of(regularSeat, brokenSeat));

            ShowResponse response = showManagementService.publishShow(showId);

            assertThat(response.status()).isEqualTo(ShowStatus.PUBLISHED);
            verify(showRepository).save(draftShow);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<ShowSeat>> captor = ArgumentCaptor.forClass(List.class);
            verify(showSeatRepository).saveAll(captor.capture());

            List<ShowSeat> materialized = captor.getValue();
            assertThat(materialized).hasSize(2);
            assertThat(materialized.get(0).getState()).isEqualTo(ShowSeatState.AVAILABLE);
            assertThat(materialized.get(1).getState()).isEqualTo(ShowSeatState.BLOCKED);
        }

        @Test
        @DisplayName("Negative: Should throw ResourceNotFoundException when show does not exist")
        void shouldThrowWhenShowNotFound() {
            when(showRepository.findById(showId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> showManagementService.publishShow(showId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Show not found");
        }

        @Test
        @DisplayName("Negative: Should throw IllegalStateException when show is already published")
        void shouldThrowWhenShowAlreadyPublished() {
            draftShow.setStatus(ShowStatus.PUBLISHED);
            when(showRepository.findById(showId)).thenReturn(Optional.of(draftShow));

            assertThatThrownBy(() -> showManagementService.publishShow(showId))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Only DRAFT shows can be published");
        }

        @Test
        @DisplayName("Negative: Should throw IllegalStateException when show has no active price configuration")
        void shouldThrowWhenNoPriceConfigurationExists() {
            when(showRepository.findById(showId)).thenReturn(Optional.of(draftShow));
            when(priceConfigurationRepository.findByShowIdAndStatus(showId, PriceConfigurationStatus.ACTIVE))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> showManagementService.publishShow(showId))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot publish show without an ACTIVE price configuration");
        }

        @Test
        @DisplayName("Negative: Should throw IllegalStateException when seat layout has zero physical seats")
        void shouldThrowWhenSeatLayoutIsEmpty() {
            when(showRepository.findById(showId)).thenReturn(Optional.of(draftShow));
            when(priceConfigurationRepository.findByShowIdAndStatus(showId, PriceConfigurationStatus.ACTIVE))
                    .thenReturn(Optional.of(activeConfig));
            when(physicalSeatRepository.findAllBySeatMapVersionIdOrderByRowLabelAscSeatNumberAsc(seatMapVersionId))
                    .thenReturn(Collections.emptyList());

            assertThatThrownBy(() -> showManagementService.publishShow(showId))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot publish show with empty seat layout");
        }
    }

    // =========================================================================
    // 4. CLOSE BOOKING
    // =========================================================================
    @Nested
    @DisplayName("closeBooking")
    class CloseBookingTests {

        private UUID showId;

        @BeforeEach
        void init() {
            showId = UUID.randomUUID();
        }

        @Test
        @DisplayName("Positive: Should close booking for published show")
        void shouldCloseBookingSuccessfully() {
            Show publishedShow = Show.builder()
                    .id(showId)
                    .status(ShowStatus.PUBLISHED)
                    .build();

            when(showRepository.findById(showId)).thenReturn(Optional.of(publishedShow));
            when(showRepository.save(any(Show.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ShowResponse response = showManagementService.closeBooking(showId);

            assertThat(response.status()).isEqualTo(ShowStatus.BOOKING_CLOSED);
            verify(showRepository).save(publishedShow);
        }

        @Test
        @DisplayName("Negative: Should throw ResourceNotFoundException when show does not exist")
        void shouldThrowWhenShowNotFound() {
            when(showRepository.findById(showId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> showManagementService.closeBooking(showId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Show not found");
        }

        @Test
        @DisplayName("Negative: Should throw IllegalStateException when closing booking on a DRAFT show")
        void shouldThrowWhenShowNotPublished() {
            Show draftShow = Show.builder()
                    .id(showId)
                    .status(ShowStatus.DRAFT)
                    .build();

            when(showRepository.findById(showId)).thenReturn(Optional.of(draftShow));

            assertThatThrownBy(() -> showManagementService.closeBooking(showId))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Only PUBLISHED shows can be closed for booking");
        }
    }

    // =========================================================================
    // 5. CANCEL SHOW
    // =========================================================================
    @Nested
    @DisplayName("cancelShow")
    class CancelShowTests {

        private UUID showId;

        @BeforeEach
        void init() {
            showId = UUID.randomUUID();
        }

        @Test
        @DisplayName("Positive: Should cancel show and update status to CANCELLED")
        void shouldCancelShowSuccessfully() {
            Show show = Show.builder()
                    .id(showId)
                    .status(ShowStatus.PUBLISHED)
                    .build();

            when(showRepository.findById(showId)).thenReturn(Optional.of(show));
            when(showRepository.save(any(Show.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ShowResponse response = showManagementService.cancelShow(showId);

            assertThat(response.status()).isEqualTo(ShowStatus.CANCELLED);
            verify(showRepository).save(show);
        }

        @Test
        @DisplayName("Negative: Should throw ResourceNotFoundException when show does not exist")
        void shouldThrowWhenShowNotFound() {
            when(showRepository.findById(showId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> showManagementService.cancelShow(showId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Show not found");
        }

        @Test
        @DisplayName("Negative: Should throw IllegalStateException when cancelling an already cancelled show")
        void shouldThrowWhenShowAlreadyCancelled() {
            Show cancelledShow = Show.builder()
                    .id(showId)
                    .status(ShowStatus.CANCELLED)
                    .build();

            when(showRepository.findById(showId)).thenReturn(Optional.of(cancelledShow));

            assertThatThrownBy(() -> showManagementService.cancelShow(showId))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Show is already cancelled");
        }
    }
}

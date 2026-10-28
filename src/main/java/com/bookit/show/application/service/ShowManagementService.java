package com.bookit.show.application.service;

import com.bookit.catalogue.domain.Movie;
import com.bookit.catalogue.domain.PhysicalSeat;
import com.bookit.catalogue.domain.SeatMapStatus;
import com.bookit.catalogue.domain.SeatMapVersion;
import com.bookit.catalogue.domain.SeatSection;
import com.bookit.catalogue.domain.SeatStatus;
import com.bookit.catalogue.domain.exception.ResourceNotFoundException;
import com.bookit.catalogue.infrastructure.repository.MovieFormatRepository;
import com.bookit.catalogue.infrastructure.repository.MovieRepository;
import com.bookit.catalogue.infrastructure.repository.PhysicalSeatRepository;
import com.bookit.catalogue.infrastructure.repository.ScreenRepository;
import com.bookit.catalogue.infrastructure.repository.SeatMapVersionRepository;
import com.bookit.catalogue.infrastructure.repository.SeatSectionRepository;
import com.bookit.show.application.dto.ConfigureShowPricesRequest;
import com.bookit.show.application.dto.CreateShowRequest;
import com.bookit.show.application.dto.ShowResponse;
import com.bookit.show.domain.PriceConfigurationStatus;
import com.bookit.show.domain.Show;
import com.bookit.show.domain.ShowPriceConfiguration;
import com.bookit.show.domain.ShowSeat;
import com.bookit.show.domain.ShowSeatPrice;
import com.bookit.show.domain.ShowSeatState;
import com.bookit.show.domain.ShowStatus;
import com.bookit.show.domain.exception.ScheduleConflictException;
import com.bookit.show.infrastructure.repository.ShowPriceConfigurationRepository;
import com.bookit.show.infrastructure.repository.ShowRepository;
import com.bookit.show.infrastructure.repository.ShowSeatPriceRepository;
import com.bookit.show.infrastructure.repository.ShowSeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service managing the operational show lifecycle: creation, price configuration, publication,
 * and status transitions.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ShowManagementService {

    private static final int CLEANING_BUFFER_MINUTES = 15;

    private final ShowRepository showRepository;
    private final ShowPriceConfigurationRepository priceConfigurationRepository;
    private final ShowSeatPriceRepository seatPriceRepository;
    private final ShowSeatRepository showSeatRepository;

    private final MovieRepository movieRepository;
    private final MovieFormatRepository movieFormatRepository;
    private final ScreenRepository screenRepository;
    private final SeatMapVersionRepository seatMapVersionRepository;
    private final SeatSectionRepository seatSectionRepository;
    private final PhysicalSeatRepository physicalSeatRepository;

    private final Clock clock;

    /**
     * Schedules a new screening in DRAFT state.
     * Computes endsAt based on movie runtime and cleaning buffer.
     * Overlapping schedule collisions on the screen are caught by PostgreSQL GiST exclusion constraint.
     */
    public ShowResponse createDraftShow(CreateShowRequest request) {
        Movie movie = movieRepository.findById(request.movieId())
                .orElseThrow(() -> new ResourceNotFoundException("Movie not found with id: " + request.movieId()));

        if (!screenRepository.existsById(request.screenId())) {
            throw new ResourceNotFoundException("Screen not found with id: " + request.screenId());
        }

        SeatMapVersion seatMapVersion = seatMapVersionRepository.findById(request.seatMapVersionId())
                .orElseThrow(() -> new ResourceNotFoundException("Seat map version not found with id: " + request.seatMapVersionId()));

        if (!seatMapVersion.getScreenId().equals(request.screenId())) {
            throw new IllegalStateException("Seat map version does not belong to the specified screen");
        }

        if (seatMapVersion.getStatus() != SeatMapStatus.PUBLISHED) {
            throw new IllegalStateException("Cannot schedule show with an unsealed/draft seat map version");
        }

        if (!movieFormatRepository.existsById(request.formatId())) {
            throw new ResourceNotFoundException("Movie format not found with id: " + request.formatId());
        }

        if (!movie.getSupportedFormatIds().contains(request.formatId())) {
            throw new IllegalArgumentException("Format " + request.formatId() + " is not supported by movie " + movie.getTitle());
        }

        Instant endsAt = request.startsAt().plus(movie.getRuntimeMinutes() + CLEANING_BUFFER_MINUTES, ChronoUnit.MINUTES);

        Show show = Show.builder()
                .id(UUID.randomUUID())
                .movieId(movie.getId())
                .screenId(request.screenId())
                .seatMapVersionId(seatMapVersion.getId())
                .formatId(request.formatId())
                .startsAt(request.startsAt())
                .endsAt(endsAt)
                .bookingOpensAt(request.bookingOpensAt())
                .bookingClosesAt(request.bookingClosesAt())
                .status(ShowStatus.DRAFT)
                .build();

        show.validateTimeWindows();

        try {
            Show saved = showRepository.saveAndFlush(show);
            return ShowResponse.fromDomain(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new ScheduleConflictException("Screen already has an overlapping show scheduled for this time range", ex);
        }
    }

    /**
     * Sets pricing tiers per seat section category for a DRAFT show.
     * Replaces previous active pricing configuration if any.
     */
    public ShowResponse configurePrices(UUID showId, ConfigureShowPricesRequest request) {
        Show show = showRepository.findById(showId)
                .orElseThrow(() -> new ResourceNotFoundException("Show not found with id: " + showId));

        if (show.getStatus() != ShowStatus.DRAFT) {
            throw new IllegalStateException("Pricing can only be configured for shows in DRAFT status. Current: " + show.getStatus());
        }

        List<SeatSection> sections = seatSectionRepository.findAllBySeatMapVersionIdOrderByDisplayOrderAsc(show.getSeatMapVersionId());
        Set<String> requiredCategories = sections.stream()
                .map(SeatSection::getCategoryCode)
                .collect(Collectors.toSet());

        Map<String, ConfigureShowPricesRequest.CategoryPrice> incomingPrices = request.prices().stream()
                .collect(Collectors.toMap(
                        ConfigureShowPricesRequest.CategoryPrice::categoryCode,
                        p -> p,
                        (existing, replacement) -> replacement
                ));

        for (String incomingCategory : incomingPrices.keySet()) {
            if (!requiredCategories.contains(incomingCategory)) {
                throw new IllegalArgumentException("Category code '" + incomingCategory + "' is not present in this show's seat map layout");
            }
        }

        for (String requiredCategory : requiredCategories) {
            if (!incomingPrices.containsKey(requiredCategory)) {
                throw new IllegalArgumentException("Missing required price for seat section category: " + requiredCategory);
            }
        }

        int nextVersion = 1;
        var existingConfigOpt = priceConfigurationRepository.findByShowIdAndStatus(show.getId(), PriceConfigurationStatus.ACTIVE);
        if (existingConfigOpt.isPresent()) {
            ShowPriceConfiguration existingConfig = existingConfigOpt.get();
            existingConfig.setStatus(PriceConfigurationStatus.ARCHIVED);
            priceConfigurationRepository.save(existingConfig);
            nextVersion = existingConfig.getVersionNumber() + 1;
        }

        ShowPriceConfiguration newConfig = ShowPriceConfiguration.builder()
                .id(UUID.randomUUID())
                .showId(show.getId())
                .versionNumber(nextVersion)
                .status(PriceConfigurationStatus.ACTIVE)
                .build();
        priceConfigurationRepository.save(newConfig);

        List<ShowSeatPrice> seatPrices = incomingPrices.values().stream()
                .map(cp -> ShowSeatPrice.builder()
                        .id(UUID.randomUUID())
                        .priceConfigurationId(newConfig.getId())
                        .sectionCategoryCode(cp.categoryCode())
                        .priceAmount(cp.amount())
                        .currency("INR")
                        .build())
                .toList();

        seatPriceRepository.saveAll(seatPrices);
        return ShowResponse.fromDomain(show);
    }

    /**
     * Publishes a show and atomically materializes physical seats into sellable show_seat inventory.
     */
    public ShowResponse publishShow(UUID showId) {
        Show show = showRepository.findById(showId)
                .orElseThrow(() -> new ResourceNotFoundException("Show not found with id: " + showId));

        if (show.getStatus() != ShowStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT shows can be published. Current: " + show.getStatus());
        }

        priceConfigurationRepository.findByShowIdAndStatus(show.getId(), PriceConfigurationStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("Cannot publish show without an ACTIVE price configuration"));

        show.publish();
        showRepository.save(show);

        List<PhysicalSeat> physicalSeats = physicalSeatRepository
                .findAllBySeatMapVersionIdOrderByRowLabelAscSeatNumberAsc(show.getSeatMapVersionId());

        if (physicalSeats.isEmpty()) {
            throw new IllegalStateException("Cannot publish show with empty seat layout");
        }

        List<ShowSeat> showSeats = physicalSeats.stream()
                .map(ps -> ShowSeat.builder()
                        .id(UUID.randomUUID())
                        .showId(show.getId())
                        .physicalSeatId(ps.getId())
                        .seatSectionId(ps.getSectionId())
                        .rowLabel(ps.getRowLabel())
                        .seatNumber(ps.getSeatNumber())
                        .seatType(ps.getSeatType().name())
                        .state(ps.getStatus() == SeatStatus.ACTIVE ? ShowSeatState.AVAILABLE : ShowSeatState.BLOCKED)
                        .version(0L)
                        .build())
                .toList();

        showSeatRepository.saveAll(showSeats);
        return ShowResponse.fromDomain(show);
    }

    /**
     * Closes booking when cutoff is reached or cinema staff closes sales.
     */
    public ShowResponse closeBooking(UUID showId) {
        Show show = showRepository.findById(showId)
                .orElseThrow(() -> new ResourceNotFoundException("Show not found with id: " + showId));

        show.closeBooking();
        Show saved = showRepository.save(show);
        return ShowResponse.fromDomain(saved);
    }

    /**
     * Cancels screening and frees up the screen time slot in the PostgreSQL exclusion constraint.
     */
    public ShowResponse cancelShow(UUID showId) {
        Show show = showRepository.findById(showId)
                .orElseThrow(() -> new ResourceNotFoundException("Show not found with id: " + showId));

        show.cancel();
        Show saved = showRepository.save(show);
        return ShowResponse.fromDomain(saved);
    }
}

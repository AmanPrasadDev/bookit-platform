package com.bookit.catalogue.application.service;

import com.bookit.catalogue.application.dto.*;
import com.bookit.catalogue.domain.Cinema;
import com.bookit.catalogue.domain.PhysicalSeat;
import com.bookit.catalogue.domain.Screen;
import com.bookit.catalogue.domain.SeatMapStatus;
import com.bookit.catalogue.domain.SeatMapVersion;
import com.bookit.catalogue.domain.SeatSection;
import com.bookit.catalogue.domain.exception.DuplicateEntityException;
import com.bookit.catalogue.domain.exception.ResourceNotFoundException;
import com.bookit.catalogue.domain.exception.SeatMapImmutableException;
import com.bookit.catalogue.infrastructure.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class VenueManagementService {

    private final OperatorOrganizationRepository organizationRepository;
    private final CityRepository cityRepository;
    private final CinemaRepository cinemaRepository;
    private final ScreenRepository screenRepository;
    private final SeatMapVersionRepository seatMapVersionRepository;
    private final SeatSectionRepository seatSectionRepository;
    private final PhysicalSeatRepository physicalSeatRepository;
    private final SeatMapAssembler seatMapAssembler;

    /**
     * Create a new Cinema under an organization and city.
     * Must validate that Organization and City exist (ResourceNotFoundException).
     * Must reject duplicate cinema name under the same organization (DuplicateEntityException).
     */
    public CinemaResponse createCinema(CreateCinemaRequest request) {
        if (!organizationRepository.existsById(request.organizationId())) {
            throw new ResourceNotFoundException("Organization not found");
        }
        if (!cityRepository.existsById(request.cityId())) {
            throw new  ResourceNotFoundException("City not found");
        }
        if (cinemaRepository.findByOrganizationIdAndName(request.organizationId(), request.name()).isPresent()) {
            throw new DuplicateEntityException("Cinema already exists");
        }
        Cinema cinema = Cinema.builder()
                .id(UUID.randomUUID()) // 👈 Don't forget this!
                .organizationId(request.organizationId())
                .cityId(request.cityId())
                .name(request.name())
                .address(request.address())
                .longitude(request.longitude())
                .latitude(request.latitude())
                .timeZone(request.timeZone() != null ? request.timeZone() : "Asia/Kolkata")
                .build();
        Cinema saved = cinemaRepository.save(cinema);
        return CinemaResponse.fromDomain(saved);
    }

    /**
     * Create a new Screen in a cinema.
     * Must validate that Cinema exists (ResourceNotFoundException).
     * Must reject duplicate screen name in the same cinema (DuplicateEntityException).
     */
    public ScreenResponse createScreen(CreateScreenRequest request) {
        if (!cinemaRepository.existsById(request.cinemaId())) {
            throw new  ResourceNotFoundException("Cinema not found");
        }
        if (screenRepository.findByCinemaIdAndName(request.cinemaId(), request.name()).isPresent()) {
            throw new  DuplicateEntityException("Screen already exists");
        }
        Screen screen = Screen.builder()
                .id(UUID.randomUUID())
                .cinemaId(request.cinemaId())
                .name(request.name())
                .build();
        Screen savedScreen = screenRepository.save(screen);
        return  ScreenResponse.fromDomain(savedScreen);
    }

    /**
     * Create a new DRAFT seat map for a screen.
     * Auto-increments version number (find latest published version number + 1, or default to 1).
     */
    public SeatMapResponse createSeatMapDraft(CreateSeatMapDraftRequest request) {
        Screen screen = screenRepository.findById(request.screenId()).orElse(null);
        if (screen == null) {
            throw new  ResourceNotFoundException("Screen not found");
        }
        int nextVersion = seatMapVersionRepository
                .findFirstByScreenIdAndStatusOrderByVersionNumberDesc(screen.getId(), SeatMapStatus.PUBLISHED)
                .map(v -> v.getVersionNumber() + 1)
                .orElse(1);
        SeatMapVersion seatMapVersion = SeatMapVersion.builder()
                .id(UUID.randomUUID())
                .screenId(screen.getId())
                .versionNumber(nextVersion)
                .status(SeatMapStatus.DRAFT)
                .build();
        return SeatMapResponse.fromDomain(seatMapVersionRepository.save(seatMapVersion), Collections.emptyList());
    }

    /**
     * Populate/replace sections and physical seats in a DRAFT seat map.
     * CORE INVARIANT: If status != DRAFT, throw SeatMapImmutableException!
     * Calculates totalSeats and saves all sections and seats.
     */
    public SeatMapResponse defineSeatLayout(UUID versionId, DefineSeatLayoutRequest request) {
        SeatMapVersion seatMapVersion = seatMapVersionRepository.findById(versionId).orElse(null);
        if (seatMapVersion == null) {
            throw new  ResourceNotFoundException("Version not found");
        }
        if (seatMapVersion.getStatus() != SeatMapStatus.DRAFT) {
            throw new SeatMapImmutableException(
                    "Cannot modify seat layout because status is " + 
                    seatMapVersion.getStatus() +
                    ". Published layouts are strictly immutable."
            );
        }
        physicalSeatRepository.deleteAll(physicalSeatRepository.findAllBySeatMapVersionIdOrderByRowLabelAscSeatNumberAsc(seatMapVersion.getId()));
        seatSectionRepository.deleteAll(seatSectionRepository.findAllBySeatMapVersionIdOrderByDisplayOrderAsc(seatMapVersion.getId()));
        int totalSeats = 0;
        List<SeatSectionResponse> seatSectionResponses = new ArrayList<>();
        for (DefineSeatLayoutRequest.SectionDefinition sectionDefinition : request.sections()) {
            SeatSection seatSection = SeatSection.builder()
                    .id(UUID.randomUUID())
                    .seatMapVersionId(versionId)
                    .name(sectionDefinition.name())
                    .categoryCode(sectionDefinition.categoryCode())
                    .displayOrder(sectionDefinition.displayOrder())
                    .build();
            seatSectionRepository.save(seatSection);
            List<PhysicalSeatResponse> physicalSeatResponses = new ArrayList<>();
            for (DefineSeatLayoutRequest.SeatDefinition seat : sectionDefinition.seats()) {
                totalSeats++;
                PhysicalSeat physicalSeat = PhysicalSeat.builder()
                        .id(UUID.randomUUID())
                        .seatMapVersionId(versionId)
                        .sectionId(seatSection.getId())
                        .rowLabel(seat.rowLabel())
                        .seatNumber(seat.seatNumber())
                        .seatType(seat.seatType())
                        .build();
                physicalSeatRepository.save(physicalSeat);
                physicalSeatResponses.add(PhysicalSeatResponse.fromDomain(physicalSeat));
            }
            seatSectionResponses.add(SeatSectionResponse.fromDomain(seatSection, physicalSeatResponses));
        }
        seatMapVersion.setTotalSeats(totalSeats);
        return SeatMapResponse.fromDomain(seatMapVersionRepository.save(seatMapVersion), seatSectionResponses);
    }

    /**
     * Publish a seat map version.
     * Validates that version exists and status != PUBLISHED (SeatMapImmutableException).
     * Validates that totalSeats > 0 (cannot publish empty layout).
     * Transitions status to PUBLISHED and sets publishedAt to current timestamp.
     */
    public SeatMapResponse publishSeatMapVersion(UUID versionId) {
        SeatMapVersion seatMapVersion = seatMapVersionRepository.findById(versionId).orElse(null);
        if (seatMapVersion == null) {
            throw new  ResourceNotFoundException("Version not found");
        }
        if (seatMapVersion.getStatus() == SeatMapStatus.PUBLISHED) {
            throw new SeatMapImmutableException("Seat map version " + versionId + " is already published");
        }
        if (seatMapVersion.getTotalSeats() <= 0) {
            throw new IllegalStateException("Cannot publish layout with zero seats");
        }
        seatMapVersion.setStatus(SeatMapStatus.PUBLISHED);
        seatMapVersion.setPublishedAt(Instant.now());
        SeatMapVersion saved = seatMapVersionRepository.save(seatMapVersion);
        return seatMapAssembler.toResponse(saved);
    }
}

package com.bookit.catalogue.application.service;

import com.bookit.catalogue.application.dto.*;
import com.bookit.catalogue.domain.*;
import com.bookit.catalogue.domain.exception.DuplicateEntityException;
import com.bookit.catalogue.domain.exception.ResourceNotFoundException;
import com.bookit.catalogue.domain.exception.SeatMapImmutableException;
import com.bookit.catalogue.infrastructure.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for VenueManagementService.
 *
 * All external dependencies (Repositories, Assemblers) are mocked using Mockito.
 * Tests verify business logic invariants, exception throwing, and correct persistence mapping
 * without needing a live PostgreSQL database connection.
 */
@ExtendWith(MockitoExtension.class)
class VenueManagementServiceTest {

    @Mock
    private OperatorOrganizationRepository organizationRepository;

    @Mock
    private CityRepository cityRepository;

    @Mock
    private CinemaRepository cinemaRepository;

    @Mock
    private ScreenRepository screenRepository;

    @Mock
    private SeatMapVersionRepository seatMapVersionRepository;

    @Mock
    private SeatSectionRepository seatSectionRepository;

    @Mock
    private PhysicalSeatRepository physicalSeatRepository;

    @Mock
    private SeatMapAssembler seatMapAssembler;

    @InjectMocks
    private VenueManagementService venueManagementService;

    // =========================================================================
    // 1. createCinema(CreateCinemaRequest request)
    // =========================================================================

    /**
     * PURPOSE: Verify that when all validation checks pass (Org exists, City exists,
     * Cinema name is unique in this Org), the cinema is saved and mapped to CinemaResponse.
     */
    @Test
    @DisplayName("createCinema should save and return CinemaResponse when request is valid")
    void createCinema_whenValidRequest_shouldSaveAndReturnCinemaResponse() {
        UUID orgId = UUID.randomUUID();
        UUID cityId = UUID.randomUUID();
        CreateCinemaRequest request = new CreateCinemaRequest(
                orgId, cityId, "PVR Select Citywalk Saket", "A-3, District Centre, Saket, New Delhi",
                BigDecimal.valueOf(28.5284), BigDecimal.valueOf(77.2185), "Asia/Kolkata"
        );

        when(organizationRepository.existsById(orgId)).thenReturn(true);
        when(cityRepository.existsById(cityId)).thenReturn(true);
        when(cinemaRepository.findByOrganizationIdAndName(orgId, request.name())).thenReturn(Optional.empty());
        when(cinemaRepository.save(any(Cinema.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CinemaResponse response = venueManagementService.createCinema(request);

        assertThat(response).isNotNull();
        assertThat(response.organizationId()).isEqualTo(orgId);
        assertThat(response.cityId()).isEqualTo(cityId);
        assertThat(response.name()).isEqualTo("PVR Select Citywalk Saket");
        assertThat(response.timeZone()).isEqualTo("Asia/Kolkata");
        verify(cinemaRepository).save(any(Cinema.class));
    }

    /**
     * PURPOSE: Verify that createCinema fails fast with ResourceNotFoundException
     * if the specified Operator Organization does not exist.
     */
    @Test
    @DisplayName("createCinema should throw ResourceNotFoundException when organization does not exist")
    void createCinema_whenOrganizationNotFound_shouldThrowResourceNotFoundException() {
        UUID orgId = UUID.randomUUID();
        UUID cityId = UUID.randomUUID();
        CreateCinemaRequest request = new CreateCinemaRequest(
                orgId, cityId, "PVR Priya Vasant Vihar", "Basant Lok, Vasant Vihar, New Delhi",
                BigDecimal.valueOf(28.5562), BigDecimal.valueOf(77.1571), "Asia/Kolkata"
        );

        when(organizationRepository.existsById(orgId)).thenReturn(false);

        assertThatThrownBy(() -> venueManagementService.createCinema(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Organization not found");

        verify(organizationRepository).existsById(orgId);
        verifyNoInteractions(cityRepository, cinemaRepository);
    }

    /**
     * PURPOSE: Verify that createCinema fails fast with ResourceNotFoundException
     * if the specified City does not exist.
     */
    @Test
    @DisplayName("createCinema should throw ResourceNotFoundException when city does not exist")
    void createCinema_whenCityNotFound_shouldThrowResourceNotFoundException() {
        UUID orgId = UUID.randomUUID();
        UUID cityId = UUID.randomUUID();
        CreateCinemaRequest request = new CreateCinemaRequest(
                orgId, cityId, "PVR Priya Vasant Vihar", "Basant Lok, Vasant Vihar, New Delhi",
                BigDecimal.valueOf(28.5562), BigDecimal.valueOf(77.1571), "Asia/Kolkata"
        );

        when(organizationRepository.existsById(orgId)).thenReturn(true);
        when(cityRepository.existsById(cityId)).thenReturn(false);

        assertThatThrownBy(() -> venueManagementService.createCinema(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("City not found");

        verify(cityRepository).existsById(cityId);
        verify(cinemaRepository, never()).save(any(Cinema.class));
    }

    /**
     * PURPOSE: Verify that createCinema enforces unique cinema naming under the same
     * organization by throwing DuplicateEntityException.
     */
    @Test
    @DisplayName("createCinema should throw DuplicateEntityException when cinema name exists under org")
    void createCinema_whenDuplicateNameInOrg_shouldThrowDuplicateEntityException() {
        UUID orgId = UUID.randomUUID();
        UUID cityId = UUID.randomUUID();
        CreateCinemaRequest request = new CreateCinemaRequest(
                orgId, cityId, "PVR Select Citywalk Saket", "Saket, New Delhi",
                BigDecimal.valueOf(28.5284), BigDecimal.valueOf(77.2185), "Asia/Kolkata"
        );

        Cinema existingCinema = Cinema.builder()
                .id(UUID.randomUUID())
                .organizationId(orgId)
                .name(request.name())
                .build();

        when(organizationRepository.existsById(orgId)).thenReturn(true);
        when(cityRepository.existsById(cityId)).thenReturn(true);
        when(cinemaRepository.findByOrganizationIdAndName(orgId, request.name())).thenReturn(Optional.of(existingCinema));

        assertThatThrownBy(() -> venueManagementService.createCinema(request))
                .isInstanceOf(DuplicateEntityException.class)
                .hasMessageContaining("Cinema already exists");

        verify(cinemaRepository, never()).save(any(Cinema.class));
    }

    // =========================================================================
    // 2. createScreen(CreateScreenRequest request)
    // =========================================================================

    /**
     * PURPOSE: Verify that when the cinema exists and screen name is unique in that cinema,
     * the screen is persisted and mapped to ScreenResponse.
     */
    @Test
    @DisplayName("createScreen should save and return ScreenResponse when request is valid")
    void createScreen_whenValidRequest_shouldSaveAndReturnScreenResponse() {
        UUID cinemaId = UUID.randomUUID();
        CreateScreenRequest request = new CreateScreenRequest(cinemaId, "Audi 1 - IMAX");

        when(cinemaRepository.existsById(cinemaId)).thenReturn(true);
        when(screenRepository.findByCinemaIdAndName(cinemaId, "Audi 1 - IMAX")).thenReturn(Optional.empty());
        when(screenRepository.save(any(Screen.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ScreenResponse response = venueManagementService.createScreen(request);

        assertThat(response).isNotNull();
        assertThat(response.cinemaId()).isEqualTo(cinemaId);
        assertThat(response.name()).isEqualTo("Audi 1 - IMAX");
        verify(screenRepository).save(any(Screen.class));
    }

    /**
     * PURPOSE: Verify that createScreen throws ResourceNotFoundException
     * if the parent cinema does not exist.
     */
    @Test
    @DisplayName("createScreen should throw ResourceNotFoundException when cinema does not exist")
    void createScreen_whenCinemaNotFound_shouldThrowResourceNotFoundException() {
        UUID cinemaId = UUID.randomUUID();
        CreateScreenRequest request = new CreateScreenRequest(cinemaId, "Audi 1 - IMAX");

        when(cinemaRepository.existsById(cinemaId)).thenReturn(false);

        assertThatThrownBy(() -> venueManagementService.createScreen(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Cinema not found");

        verify(screenRepository, never()).save(any(Screen.class));
    }

    /**
     * PURPOSE: Verify that createScreen enforces unique screen naming inside the same cinema
     * by throwing DuplicateEntityException.
     */
    @Test
    @DisplayName("createScreen should throw DuplicateEntityException when screen name already exists in cinema")
    void createScreen_whenDuplicateNameInCinema_shouldThrowDuplicateEntityException() {
        UUID cinemaId = UUID.randomUUID();
        CreateScreenRequest request = new CreateScreenRequest(cinemaId, "Audi 1 - IMAX");

        Screen existingScreen = Screen.builder()
                .id(UUID.randomUUID())
                .cinemaId(cinemaId)
                .name("Audi 1 - IMAX")
                .build();

        when(cinemaRepository.existsById(cinemaId)).thenReturn(true);
        when(screenRepository.findByCinemaIdAndName(cinemaId, "Audi 1 - IMAX")).thenReturn(Optional.of(existingScreen));

        assertThatThrownBy(() -> venueManagementService.createScreen(request))
                .isInstanceOf(DuplicateEntityException.class)
                .hasMessageContaining("Screen already exists");

        verify(screenRepository, never()).save(any(Screen.class));
    }

    // =========================================================================
    // 3. createSeatMapDraft(CreateSeatMapDraftRequest request)
    // =========================================================================

    /**
     * PURPOSE: Verify that createSeatMapDraft throws ResourceNotFoundException
     * if the screen does not exist.
     */
    @Test
    @DisplayName("createSeatMapDraft should throw ResourceNotFoundException when screen does not exist")
    void createSeatMapDraft_whenScreenNotFound_shouldThrowResourceNotFoundException() {
        UUID screenId = UUID.randomUUID();
        CreateSeatMapDraftRequest request = new CreateSeatMapDraftRequest(screenId);

        when(screenRepository.findById(screenId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> venueManagementService.createSeatMapDraft(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Screen not found");

        verify(seatMapVersionRepository, never()).save(any(SeatMapVersion.class));
    }

    /**
     * PURPOSE: Verify that when creating the first seat map draft for a screen with NO published versions,
     * the version number is initialized to 1 and status is set to DRAFT.
     */
    @Test
    @DisplayName("createSeatMapDraft should initialize version number to 1 when no published version exists")
    void createSeatMapDraft_whenNoPublishedVersionExists_shouldCreateVersionOne() {
        UUID screenId = UUID.randomUUID();
        Screen screen = Screen.builder().id(screenId).name("Audi 1 - IMAX").build();
        CreateSeatMapDraftRequest request = new CreateSeatMapDraftRequest(screenId);

        when(screenRepository.findById(screenId)).thenReturn(Optional.of(screen));
        when(seatMapVersionRepository.findFirstByScreenIdAndStatusOrderByVersionNumberDesc(screenId, SeatMapStatus.PUBLISHED))
                .thenReturn(Optional.empty());
        when(seatMapVersionRepository.save(any(SeatMapVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SeatMapResponse response = venueManagementService.createSeatMapDraft(request);

        assertThat(response).isNotNull();
        assertThat(response.screenId()).isEqualTo(screenId);
        assertThat(response.versionNumber()).isEqualTo(1);
        assertThat(response.status()).isEqualTo(SeatMapStatus.DRAFT);
        assertThat(response.sections()).isEmpty();
    }

    /**
     * PURPOSE: Verify that when creating a new draft for a screen that already has a published version
     * (e.g. Version 2), the new draft version number is auto-incremented to 3 (published version + 1).
     */
    @Test
    @DisplayName("createSeatMapDraft should increment version number based on latest published version")
    void createSeatMapDraft_whenPublishedVersionExists_shouldIncrementVersionNumber() {
        UUID screenId = UUID.randomUUID();
        Screen screen = Screen.builder().id(screenId).name("Audi 1 - IMAX").build();
        CreateSeatMapDraftRequest request = new CreateSeatMapDraftRequest(screenId);

        SeatMapVersion publishedVersion2 = SeatMapVersion.builder()
                .id(UUID.randomUUID())
                .screenId(screenId)
                .versionNumber(2)
                .status(SeatMapStatus.PUBLISHED)
                .totalSeats(100)
                .build();

        when(screenRepository.findById(screenId)).thenReturn(Optional.of(screen));
        when(seatMapVersionRepository.findFirstByScreenIdAndStatusOrderByVersionNumberDesc(screenId, SeatMapStatus.PUBLISHED))
                .thenReturn(Optional.of(publishedVersion2));
        when(seatMapVersionRepository.save(any(SeatMapVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SeatMapResponse response = venueManagementService.createSeatMapDraft(request);

        assertThat(response.versionNumber()).isEqualTo(3);
        assertThat(response.status()).isEqualTo(SeatMapStatus.DRAFT);
    }

    // =========================================================================
    // 4. defineSeatLayout(UUID versionId, DefineSeatLayoutRequest request)
    // =========================================================================

    /**
     * PURPOSE: Verify that defineSeatLayout throws ResourceNotFoundException
     * if the target seat map version does not exist.
     */
    @Test
    @DisplayName("defineSeatLayout should throw ResourceNotFoundException when version does not exist")
    void defineSeatLayout_whenVersionNotFound_shouldThrowResourceNotFoundException() {
        UUID versionId = UUID.randomUUID();
        DefineSeatLayoutRequest request = new DefineSeatLayoutRequest(Collections.emptyList());

        when(seatMapVersionRepository.findById(versionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> venueManagementService.defineSeatLayout(versionId, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Version not found");

        verifyNoInteractions(physicalSeatRepository, seatSectionRepository);
    }

    /**
     * PURPOSE: Verify CORE DOMAIN INVARIANT: Once a seat map version is PUBLISHED,
     * its layout is strictly immutable. Attempting to define or modify seats must throw
     * SeatMapImmutableException.
     */
    @Test
    @DisplayName("defineSeatLayout should throw SeatMapImmutableException when version is not in DRAFT status")
    void defineSeatLayout_whenVersionIsPublished_shouldThrowSeatMapImmutableException() {
        UUID versionId = UUID.randomUUID();
        SeatMapVersion publishedVersion = SeatMapVersion.builder()
                .id(versionId)
                .status(SeatMapStatus.PUBLISHED)
                .totalSeats(100)
                .build();
        DefineSeatLayoutRequest request = new DefineSeatLayoutRequest(Collections.emptyList());

        when(seatMapVersionRepository.findById(versionId)).thenReturn(Optional.of(publishedVersion));

        assertThatThrownBy(() -> venueManagementService.defineSeatLayout(versionId, request))
                .isInstanceOf(SeatMapImmutableException.class)
                .hasMessageContaining("Published layouts are strictly immutable");

        verifyNoInteractions(physicalSeatRepository, seatSectionRepository);
    }

    /**
     * PURPOSE: Verify that defineSeatLayout clears previous draft seats/sections,
     * persists new sections and physical seats, calculates totalSeats correctly,
     * updates the version, and returns the populated SeatMapResponse.
     */
    @Test
    @DisplayName("defineSeatLayout should populate sections, physical seats, and update totalSeats")
    void defineSeatLayout_whenDraftVersion_shouldCalculateTotalSeatsAndSave() {
        UUID versionId = UUID.randomUUID();
        SeatMapVersion draftVersion = SeatMapVersion.builder()
                .id(versionId)
                .screenId(UUID.randomUUID())
                .versionNumber(1)
                .status(SeatMapStatus.DRAFT)
                .totalSeats(0)
                .build();

        DefineSeatLayoutRequest.SeatDefinition seatA1 = new DefineSeatLayoutRequest.SeatDefinition("A", 1, SeatType.REGULAR);
        DefineSeatLayoutRequest.SeatDefinition seatA2 = new DefineSeatLayoutRequest.SeatDefinition("A", 2, SeatType.REGULAR);
        DefineSeatLayoutRequest.SectionDefinition primeSection = new DefineSeatLayoutRequest.SectionDefinition(
                "PRIME", "PRIME", 1, List.of(seatA1, seatA2)
        );
        DefineSeatLayoutRequest request = new DefineSeatLayoutRequest(List.of(primeSection));

        when(seatMapVersionRepository.findById(versionId)).thenReturn(Optional.of(draftVersion));
        when(physicalSeatRepository.findAllBySeatMapVersionIdOrderByRowLabelAscSeatNumberAsc(versionId)).thenReturn(Collections.emptyList());
        when(seatSectionRepository.findAllBySeatMapVersionIdOrderByDisplayOrderAsc(versionId)).thenReturn(Collections.emptyList());
        when(seatSectionRepository.save(any(SeatSection.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(physicalSeatRepository.save(any(PhysicalSeat.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(seatMapVersionRepository.save(any(SeatMapVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SeatMapResponse response = venueManagementService.defineSeatLayout(versionId, request);

        assertThat(response).isNotNull();
        assertThat(response.totalSeats()).isEqualTo(2);
        assertThat(response.sections()).hasSize(1);
        assertThat(response.sections().getFirst().name()).isEqualTo("PRIME");
        assertThat(response.sections().getFirst().seats()).hasSize(2);

        verify(physicalSeatRepository, times(2)).save(any(PhysicalSeat.class));
        verify(seatSectionRepository, times(1)).save(any(SeatSection.class));
        verify(seatMapVersionRepository).save(argThat(v -> v.getTotalSeats() == 2));
    }

    // =========================================================================
    // 5. publishSeatMapVersion(UUID versionId)
    // =========================================================================

    /**
     * PURPOSE: Verify that publishSeatMapVersion throws ResourceNotFoundException
     * if the version ID does not exist.
     */
    @Test
    @DisplayName("publishSeatMapVersion should throw ResourceNotFoundException when version does not exist")
    void publishSeatMapVersion_whenVersionNotFound_shouldThrowResourceNotFoundException() {
        UUID versionId = UUID.randomUUID();

        when(seatMapVersionRepository.findById(versionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> venueManagementService.publishSeatMapVersion(versionId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Version not found");

        verifyNoInteractions(seatMapAssembler);
    }

    /**
     * PURPOSE: Verify that publishSeatMapVersion throws SeatMapImmutableException
     * if the version has ALREADY been published.
     */
    @Test
    @DisplayName("publishSeatMapVersion should throw SeatMapImmutableException when version is already published")
    void publishSeatMapVersion_whenAlreadyPublished_shouldThrowSeatMapImmutableException() {
        UUID versionId = UUID.randomUUID();
        SeatMapVersion publishedVersion = SeatMapVersion.builder()
                .id(versionId)
                .status(SeatMapStatus.PUBLISHED)
                .totalSeats(100)
                .build();

        when(seatMapVersionRepository.findById(versionId)).thenReturn(Optional.of(publishedVersion));

        assertThatThrownBy(() -> venueManagementService.publishSeatMapVersion(versionId))
                .isInstanceOf(SeatMapImmutableException.class)
                .hasMessageContaining("already published");

        verifyNoInteractions(seatMapAssembler);
    }

    /**
     * PURPOSE: Verify that an operator cannot publish an empty seat map (totalSeats <= 0).
     * Must throw IllegalStateException.
     */
    @Test
    @DisplayName("publishSeatMapVersion should throw IllegalStateException when totalSeats is zero")
    void publishSeatMapVersion_whenTotalSeatsZero_shouldThrowIllegalStateException() {
        UUID versionId = UUID.randomUUID();
        SeatMapVersion emptyDraft = SeatMapVersion.builder()
                .id(versionId)
                .status(SeatMapStatus.DRAFT)
                .totalSeats(0)
                .build();

        when(seatMapVersionRepository.findById(versionId)).thenReturn(Optional.of(emptyDraft));

        assertThatThrownBy(() -> venueManagementService.publishSeatMapVersion(versionId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot publish layout with zero seats");

        verifyNoInteractions(seatMapAssembler);
    }

    /**
     * PURPOSE: Verify that publishSeatMapVersion transitions the draft to PUBLISHED status,
     * assigns publishedAt timestamp, saves the version, and delegates to SeatMapAssembler.
     */
    @Test
    @DisplayName("publishSeatMapVersion should transition to PUBLISHED and delegate to SeatMapAssembler")
    void publishSeatMapVersion_whenValidDraft_shouldTransitionToPublishedAndDelegate() {
        UUID versionId = UUID.randomUUID();
        UUID screenId = UUID.randomUUID();
        SeatMapVersion draftVersion = SeatMapVersion.builder()
                .id(versionId)
                .screenId(screenId)
                .versionNumber(1)
                .status(SeatMapStatus.DRAFT)
                .totalSeats(150)
                .build();

        SeatMapResponse expectedResponse = new SeatMapResponse(
                versionId, screenId, 1, SeatMapStatus.PUBLISHED, 150, Instant.now(), Collections.emptyList()
        );

        when(seatMapVersionRepository.findById(versionId)).thenReturn(Optional.of(draftVersion));
        when(seatMapVersionRepository.save(any(SeatMapVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(seatMapAssembler.toResponse(any(SeatMapVersion.class))).thenReturn(expectedResponse);

        SeatMapResponse actualResponse = venueManagementService.publishSeatMapVersion(versionId);

        assertThat(actualResponse).isSameAs(expectedResponse);

        ArgumentCaptor<SeatMapVersion> captor = ArgumentCaptor.forClass(SeatMapVersion.class);
        verify(seatMapVersionRepository).save(captor.capture());

        SeatMapVersion savedVersion = captor.getValue();
        assertThat(savedVersion.getStatus()).isEqualTo(SeatMapStatus.PUBLISHED);
        assertThat(savedVersion.getPublishedAt()).isNotNull();

        verify(seatMapAssembler).toResponse(savedVersion);
    }
}

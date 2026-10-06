package com.bookit.catalogue.presentation.controller;

import com.bookit.catalogue.application.dto.*;
import com.bookit.catalogue.application.service.VenueManagementService;
import com.bookit.catalogue.domain.SeatMapStatus;
import com.bookit.catalogue.domain.SeatType;
import com.bookit.catalogue.domain.exception.DuplicateEntityException;
import com.bookit.catalogue.domain.exception.SeatMapImmutableException;
import com.bookit.identity.domain.UserRole;
import com.bookit.identity.infrastructure.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class OperatorVenueControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @MockitoBean
    private VenueManagementService venueManagementService;

    private String staffToken;
    private String customerToken;

    @BeforeEach
    void setUp() {
        staffToken = jwtService.generateAccessToken(
                UUID.randomUUID(),
                "operator@bookit.com",
                List.of(UserRole.CINEMA_STAFF),
                UUID.randomUUID()
        );

        customerToken = jwtService.generateAccessToken(
                UUID.randomUUID(),
                "customer@bookit.com",
                List.of(UserRole.CUSTOMER),
                UUID.randomUUID()
        );
    }

    // =========================================================================
    // 1. Security & RBAC Enforcement (401/403)
    // =========================================================================

    @Test
    @DisplayName("POST /api/v1/operator/cinemas without token should be rejected (401/403)")
    void createCinema_whenUnauthenticated_shouldReject() throws Exception {
        CreateCinemaRequest request = new CreateCinemaRequest(
                UUID.randomUUID(), UUID.randomUUID(), "PVR Priya Vasant Vihar", "New Delhi",
                BigDecimal.valueOf(28.5562), BigDecimal.valueOf(77.1571), "Asia/Kolkata"
        );

        mockMvc.perform(post("/api/v1/operator/cinemas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/v1/operator/cinemas with CUSTOMER role should return 403 Forbidden")
    void createCinema_whenCustomerRole_shouldReturn403Forbidden() throws Exception {
        CreateCinemaRequest request = new CreateCinemaRequest(
                UUID.randomUUID(), UUID.randomUUID(), "PVR Priya Vasant Vihar", "New Delhi",
                BigDecimal.valueOf(28.5562), BigDecimal.valueOf(77.1571), "Asia/Kolkata"
        );

        mockMvc.perform(post("/api/v1/operator/cinemas")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.status").value(403));
    }

    // =========================================================================
    // 2. POST /api/v1/operator/cinemas
    // =========================================================================

    @Test
    @DisplayName("POST /api/v1/operator/cinemas with CINEMA_STAFF should return 201 Created")
    void createCinema_whenStaffRoleAndValidRequest_shouldReturn201Created() throws Exception {
        UUID cinemaId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID cityId = UUID.randomUUID();
        CreateCinemaRequest request = new CreateCinemaRequest(
                orgId, cityId, "PVR Select Citywalk Saket", "Saket District Centre, New Delhi",
                BigDecimal.valueOf(28.5284), BigDecimal.valueOf(77.2185), "Asia/Kolkata"
        );

        CinemaResponse expectedResponse = new CinemaResponse(
                cinemaId, orgId, cityId, request.name(), request.address(),
                request.latitude(), request.longitude(), "Asia/Kolkata", "ACTIVE"
        );

        when(venueManagementService.createCinema(any(CreateCinemaRequest.class))).thenReturn(expectedResponse);

        mockMvc.perform(post("/api/v1/operator/cinemas")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(cinemaId.toString()))
                .andExpect(jsonPath("$.name").value("PVR Select Citywalk Saket"));

        verify(venueManagementService).createCinema(any(CreateCinemaRequest.class));
    }

    @Test
    @DisplayName("POST /api/v1/operator/cinemas with blank name should return 400 Bad Request with field errors")
    void createCinema_whenBlankName_shouldReturn400BadRequestWithValidationErrors() throws Exception {
        CreateCinemaRequest invalidRequest = new CreateCinemaRequest(
                UUID.randomUUID(), UUID.randomUUID(), "   ", "New Delhi",
                BigDecimal.valueOf(28.5284), BigDecimal.valueOf(77.2185), "Asia/Kolkata"
        );

        mockMvc.perform(post("/api/v1/operator/cinemas")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.invalidFields[0].field").value("name"));
    }

    @Test
    @DisplayName("POST /api/v1/operator/cinemas with duplicate name should return 409 Conflict")
    void createCinema_whenDuplicateEntity_shouldReturn409Conflict() throws Exception {
        CreateCinemaRequest request = new CreateCinemaRequest(
                UUID.randomUUID(), UUID.randomUUID(), "PVR Select Citywalk Saket", "New Delhi",
                BigDecimal.valueOf(28.5284), BigDecimal.valueOf(77.2185), "Asia/Kolkata"
        );

        when(venueManagementService.createCinema(any(CreateCinemaRequest.class)))
                .thenThrow(new DuplicateEntityException("Cinema already exists with name: " + request.name()));

        mockMvc.perform(post("/api/v1/operator/cinemas")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_ENTITY"))
                .andExpect(jsonPath("$.status").value(409));
    }

    // =========================================================================
    // 3. POST /api/v1/operator/screens
    // =========================================================================

    @Test
    @DisplayName("POST /api/v1/operator/screens with CINEMA_STAFF should return 201 Created")
    void createScreen_whenStaffRole_shouldReturn201Created() throws Exception {
        UUID cinemaId = UUID.randomUUID();
        UUID screenId = UUID.randomUUID();
        CreateScreenRequest request = new CreateScreenRequest(cinemaId, "Audi 1 - IMAX");
        ScreenResponse expectedResponse = new ScreenResponse(screenId, cinemaId, "Audi 1 - IMAX", "ACTIVE");

        when(venueManagementService.createScreen(any(CreateScreenRequest.class))).thenReturn(expectedResponse);

        mockMvc.perform(post("/api/v1/operator/screens")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(screenId.toString()))
                .andExpect(jsonPath("$.name").value("Audi 1 - IMAX"));

        verify(venueManagementService).createScreen(any(CreateScreenRequest.class));
    }

    // =========================================================================
    // 4. POST /api/v1/operator/seat-map-drafts
    // =========================================================================

    @Test
    @DisplayName("POST /api/v1/operator/seat-map-drafts should return 201 Created with version 1")
    void createSeatMapDraft_whenStaffRole_shouldReturn201Created() throws Exception {
        UUID screenId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        CreateSeatMapDraftRequest request = new CreateSeatMapDraftRequest(screenId);
        SeatMapResponse expectedResponse = new SeatMapResponse(
                versionId, screenId, 1, SeatMapStatus.DRAFT, 0, null, Collections.emptyList()
        );

        when(venueManagementService.createSeatMapDraft(any(CreateSeatMapDraftRequest.class))).thenReturn(expectedResponse);

        mockMvc.perform(post("/api/v1/operator/seat-map-drafts")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(versionId.toString()))
                .andExpect(jsonPath("$.versionNumber").value(1))
                .andExpect(jsonPath("$.status").value("DRAFT"));

        verify(venueManagementService).createSeatMapDraft(any(CreateSeatMapDraftRequest.class));
    }

    // =========================================================================
    // 5. PUT /api/v1/operator/seat-maps/{versionId}/layout
    // =========================================================================

    @Test
    @DisplayName("PUT /api/v1/operator/seat-maps/{versionId}/layout should return 200 OK")
    void defineSeatLayout_whenStaffRole_shouldReturn200Ok() throws Exception {
        UUID versionId = UUID.randomUUID();
        UUID screenId = UUID.randomUUID();

        DefineSeatLayoutRequest.SeatDefinition seatA1 = new DefineSeatLayoutRequest.SeatDefinition("A", 1, SeatType.REGULAR);
        DefineSeatLayoutRequest.SectionDefinition primeSection = new DefineSeatLayoutRequest.SectionDefinition(
                "PRIME", "PRIME", 1, List.of(seatA1)
        );
        DefineSeatLayoutRequest request = new DefineSeatLayoutRequest(List.of(primeSection));

        SeatMapResponse expectedResponse = new SeatMapResponse(
                versionId, screenId, 1, SeatMapStatus.DRAFT, 1, null, Collections.emptyList()
        );

        when(venueManagementService.defineSeatLayout(eq(versionId), any(DefineSeatLayoutRequest.class)))
                .thenReturn(expectedResponse);

        mockMvc.perform(put("/api/v1/operator/seat-maps/{versionId}/layout", versionId)
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(versionId.toString()))
                .andExpect(jsonPath("$.totalSeats").value(1));

        verify(venueManagementService).defineSeatLayout(eq(versionId), any(DefineSeatLayoutRequest.class));
    }

    @Test
    @DisplayName("PUT /api/v1/operator/seat-maps/{versionId}/layout when published should return 409 Conflict")
    void defineSeatLayout_whenPublishedVersion_shouldReturn409Conflict() throws Exception {
        UUID versionId = UUID.randomUUID();
        DefineSeatLayoutRequest.SeatDefinition seatA1 = new DefineSeatLayoutRequest.SeatDefinition("A", 1, SeatType.REGULAR);
        DefineSeatLayoutRequest.SectionDefinition primeSection = new DefineSeatLayoutRequest.SectionDefinition(
                "PRIME", "PRIME", 1, List.of(seatA1)
        );
        DefineSeatLayoutRequest request = new DefineSeatLayoutRequest(List.of(primeSection));

        when(venueManagementService.defineSeatLayout(eq(versionId), any(DefineSeatLayoutRequest.class)))
                .thenThrow(new SeatMapImmutableException("Cannot modify published layout"));

        mockMvc.perform(put("/api/v1/operator/seat-maps/{versionId}/layout", versionId)
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEAT_MAP_IMMUTABLE"))
                .andExpect(jsonPath("$.status").value(409));
    }

    // =========================================================================
    // 6. POST /api/v1/operator/seat-maps/{versionId}/publish
    // =========================================================================

    @Test
    @DisplayName("POST /api/v1/operator/seat-maps/{versionId}/publish should return 200 OK")
    void publishSeatLayout_whenStaffRole_shouldReturn200Ok() throws Exception {
        UUID versionId = UUID.randomUUID();
        UUID screenId = UUID.randomUUID();
        SeatMapResponse publishedResponse = new SeatMapResponse(
                versionId, screenId, 1, SeatMapStatus.PUBLISHED, 150, Instant.now(), Collections.emptyList()
        );

        when(venueManagementService.publishSeatMapVersion(versionId)).thenReturn(publishedResponse);

        mockMvc.perform(post("/api/v1/operator/seat-maps/{versionId}/publish", versionId)
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(versionId.toString()))
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.totalSeats").value(150));

        verify(venueManagementService).publishSeatMapVersion(versionId);
    }

    @Test
    @DisplayName("POST /api/v1/operator/seat-maps/{versionId}/publish when zero seats should return 400 Bad Request")
    void publishSeatLayout_whenZeroSeats_shouldReturn400BadRequest() throws Exception {
        UUID versionId = UUID.randomUUID();

        when(venueManagementService.publishSeatMapVersion(versionId))
                .thenThrow(new IllegalStateException("Cannot publish layout with zero seats"));

        mockMvc.perform(post("/api/v1/operator/seat-maps/{versionId}/publish", versionId)
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ILLEGAL_STATE"))
                .andExpect(jsonPath("$.status").value(400));
    }
}

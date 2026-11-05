package com.bookit.hold.presentation.controller;

import com.bookit.catalogue.domain.exception.ResourceNotFoundException;
import com.bookit.hold.application.dto.CreateSeatHoldRequest;
import com.bookit.hold.application.dto.HoldSeatsRequest;
import com.bookit.hold.application.dto.SeatHoldResponse;
import com.bookit.hold.application.service.SeatHoldService;
import com.bookit.hold.domain.SeatHoldStatus;
import com.bookit.hold.domain.exception.SeatUnavailableException;
import com.bookit.identity.domain.UserRole;
import com.bookit.identity.infrastructure.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class SeatHoldControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @MockitoBean
    private SeatHoldService seatHoldService;

    private UUID customerId;
    private String customerToken;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        customerToken = jwtService.generateAccessToken(
                customerId,
                "customer@bookit.com",
                List.of(UserRole.CUSTOMER),
                UUID.randomUUID()
        );
    }

    // =========================================================================
    // 1. Security & Authentication (403 Forbidden)
    // =========================================================================

    @Nested
    @DisplayName("1. Security & Authentication")
    class SecurityTests {

        @Test
        @DisplayName("POST /api/v1/shows/{showId}/holds without token should be rejected (403)")
        void holdSeats_unauthenticated_shouldReturn403() throws Exception {
            UUID showId = UUID.randomUUID();
            HoldSeatsRequest request = new HoldSeatsRequest(List.of(UUID.randomUUID()));

            mockMvc.perform(post("/api/v1/shows/{showId}/holds", showId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("POST /api/v1/holds/{holdId}/release without token should be rejected (403)")
        void releaseHold_unauthenticated_shouldReturn403() throws Exception {
            UUID holdId = UUID.randomUUID();

            mockMvc.perform(post("/api/v1/holds/{holdId}/release", holdId))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("GET /api/v1/holds/{holdId} without token should be rejected (403)")
        void getHold_unauthenticated_shouldReturn403() throws Exception {
            UUID holdId = UUID.randomUUID();

            mockMvc.perform(get("/api/v1/holds/{holdId}", holdId))
                    .andExpect(status().isForbidden());
        }
    }

    // =========================================================================
    // 2. POST /api/v1/shows/{showId}/holds - Hold Seats
    // =========================================================================

    @Nested
    @DisplayName("2. POST /api/v1/shows/{showId}/holds - Hold Seats")
    class HoldSeatsTests {

        @Test
        @DisplayName("Valid hold request should return 201 Created with hold details")
        void holdSeats_validRequest_shouldReturn201Created() throws Exception {
            UUID showId = UUID.randomUUID();
            UUID seat1 = UUID.randomUUID();
            UUID seat2 = UUID.randomUUID();
            List<UUID> seatIds = List.of(seat1, seat2);
            HoldSeatsRequest request = new HoldSeatsRequest(seatIds);

            UUID holdId = UUID.randomUUID();
            Instant now = Instant.now();
            Instant expiresAt = now.plus(10, ChronoUnit.MINUTES);

            SeatHoldResponse response = new SeatHoldResponse(
                    holdId,
                    customerId,
                    showId,
                    seatIds,
                    SeatHoldStatus.HELD,
                    expiresAt,
                    now
            );

            when(seatHoldService.holdSeats(eq(new CreateSeatHoldRequest(showId, seatIds)), eq(customerId)))
                    .thenReturn(response);

            mockMvc.perform(post("/api/v1/shows/{showId}/holds", showId)
                            .header("Authorization", "Bearer " + customerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(holdId.toString()))
                    .andExpect(jsonPath("$.customerId").value(customerId.toString()))
                    .andExpect(jsonPath("$.showId").value(showId.toString()))
                    .andExpect(jsonPath("$.seatIds.length()").value(2))
                    .andExpect(jsonPath("$.status").value("HELD"))
                    .andExpect(jsonPath("$.expiresAt").isNotEmpty());

            verify(seatHoldService).holdSeats(eq(new CreateSeatHoldRequest(showId, seatIds)), eq(customerId));
        }

        @Test
        @DisplayName("Empty seatIds list should fail validation (400 Bad Request)")
        void holdSeats_emptySeats_shouldReturn400BadRequest() throws Exception {
            UUID showId = UUID.randomUUID();
            HoldSeatsRequest request = new HoldSeatsRequest(List.of());

            mockMvc.perform(post("/api/v1/shows/{showId}/holds", showId)
                            .header("Authorization", "Bearer " + customerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.status").value(400));
        }

        @Test
        @DisplayName("Exceeding 10 seats limit should fail validation (400 Bad Request)")
        void holdSeats_exceeding10Seats_shouldReturn400BadRequest() throws Exception {
            UUID showId = UUID.randomUUID();
            List<UUID> elevenSeats = new ArrayList<>();
            for (int i = 0; i < 11; i++) {
                elevenSeats.add(UUID.randomUUID());
            }
            HoldSeatsRequest request = new HoldSeatsRequest(elevenSeats);

            mockMvc.perform(post("/api/v1/shows/{showId}/holds", showId)
                            .header("Authorization", "Bearer " + customerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.status").value(400));
        }

        @Test
        @DisplayName("When seats are unavailable should return 409 Conflict")
        void holdSeats_whenSeatsUnavailable_shouldReturn409Conflict() throws Exception {
            UUID showId = UUID.randomUUID();
            List<UUID> seatIds = List.of(UUID.randomUUID());
            HoldSeatsRequest request = new HoldSeatsRequest(seatIds);

            when(seatHoldService.holdSeats(any(CreateSeatHoldRequest.class), eq(customerId)))
                    .thenThrow(new SeatUnavailableException("Seat A1 is no longer available"));

            mockMvc.perform(post("/api/v1/shows/{showId}/holds", showId)
                            .header("Authorization", "Bearer " + customerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("SEAT_UNAVAILABLE"))
                    .andExpect(jsonPath("$.status").value(409));
        }

        @Test
        @DisplayName("When show not found should return 404 Not Found")
        void holdSeats_whenShowNotFound_shouldReturn404NotFound() throws Exception {
            UUID showId = UUID.randomUUID();
            List<UUID> seatIds = List.of(UUID.randomUUID());
            HoldSeatsRequest request = new HoldSeatsRequest(seatIds);

            when(seatHoldService.holdSeats(any(CreateSeatHoldRequest.class), eq(customerId)))
                    .thenThrow(new ResourceNotFoundException("Show not found with id: " + showId));

            mockMvc.perform(post("/api/v1/shows/{showId}/holds", showId)
                            .header("Authorization", "Bearer " + customerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                    .andExpect(jsonPath("$.status").value(404));
        }

        @Test
        @DisplayName("When customer already has active hold should return 400 Bad Request")
        void holdSeats_whenCustomerAlreadyHasActiveHold_shouldReturn400BadRequest() throws Exception {
            UUID showId = UUID.randomUUID();
            List<UUID> seatIds = List.of(UUID.randomUUID());
            HoldSeatsRequest request = new HoldSeatsRequest(seatIds);

            when(seatHoldService.holdSeats(any(CreateSeatHoldRequest.class), eq(customerId)))
                    .thenThrow(new IllegalStateException("Customer already has an active hold in progress"));

            mockMvc.perform(post("/api/v1/shows/{showId}/holds", showId)
                            .header("Authorization", "Bearer " + customerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("ILLEGAL_STATE"))
                    .andExpect(jsonPath("$.status").value(400));
        }
    }

    // =========================================================================
    // 3. POST /api/v1/holds/{holdId}/release - Release Hold
    // =========================================================================

    @Nested
    @DisplayName("3. POST /api/v1/holds/{holdId}/release - Release Hold")
    class ReleaseHoldTests {

        @Test
        @DisplayName("Valid release request should return 200 OK with RELEASED status")
        void releaseHold_validRequest_shouldReturn200Ok() throws Exception {
            UUID holdId = UUID.randomUUID();
            UUID showId = UUID.randomUUID();
            List<UUID> seatIds = List.of(UUID.randomUUID());

            SeatHoldResponse response = new SeatHoldResponse(
                    holdId,
                    customerId,
                    showId,
                    seatIds,
                    SeatHoldStatus.RELEASED,
                    Instant.now().plus(5, ChronoUnit.MINUTES),
                    Instant.now().minus(5, ChronoUnit.MINUTES)
            );

            when(seatHoldService.releaseHold(eq(holdId), eq(customerId)))
                    .thenReturn(response);

            mockMvc.perform(post("/api/v1/holds/{holdId}/release", holdId)
                            .header("Authorization", "Bearer " + customerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(holdId.toString()))
                    .andExpect(jsonPath("$.status").value("RELEASED"));

            verify(seatHoldService).releaseHold(eq(holdId), eq(customerId));
        }

        @Test
        @DisplayName("Release non-existent hold should return 404 Not Found")
        void releaseHold_whenHoldNotFound_shouldReturn404NotFound() throws Exception {
            UUID holdId = UUID.randomUUID();

            when(seatHoldService.releaseHold(eq(holdId), eq(customerId)))
                    .thenThrow(new ResourceNotFoundException("Hold not found with id: " + holdId));

            mockMvc.perform(post("/api/v1/holds/{holdId}/release", holdId)
                            .header("Authorization", "Bearer " + customerToken))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                    .andExpect(jsonPath("$.status").value(404));
        }
    }

    // =========================================================================
    // 4. GET /api/v1/holds/{holdId} - Get Hold
    // =========================================================================

    @Nested
    @DisplayName("4. GET /api/v1/holds/{holdId} - Get Hold")
    class GetHoldTests {

        @Test
        @DisplayName("Valid get request should return 200 OK with hold details")
        void getHold_validRequest_shouldReturn200Ok() throws Exception {
            UUID holdId = UUID.randomUUID();
            UUID showId = UUID.randomUUID();
            List<UUID> seatIds = List.of(UUID.randomUUID(), UUID.randomUUID());

            SeatHoldResponse response = new SeatHoldResponse(
                    holdId,
                    customerId,
                    showId,
                    seatIds,
                    SeatHoldStatus.HELD,
                    Instant.now().plus(8, ChronoUnit.MINUTES),
                    Instant.now().minus(2, ChronoUnit.MINUTES)
            );

            when(seatHoldService.getHold(eq(holdId), eq(customerId)))
                    .thenReturn(response);

            mockMvc.perform(get("/api/v1/holds/{holdId}", holdId)
                            .header("Authorization", "Bearer " + customerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(holdId.toString()))
                    .andExpect(jsonPath("$.status").value("HELD"))
                    .andExpect(jsonPath("$.seatIds.length()").value(2));

            verify(seatHoldService).getHold(eq(holdId), eq(customerId));
        }

        @Test
        @DisplayName("Get non-existent hold should return 404 Not Found")
        void getHold_whenHoldNotFound_shouldReturn404NotFound() throws Exception {
            UUID holdId = UUID.randomUUID();

            when(seatHoldService.getHold(eq(holdId), eq(customerId)))
                    .thenThrow(new ResourceNotFoundException("Hold not found with id: " + holdId));

            mockMvc.perform(get("/api/v1/holds/{holdId}", holdId)
                            .header("Authorization", "Bearer " + customerToken))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                    .andExpect(jsonPath("$.status").value(404));
        }
    }
}

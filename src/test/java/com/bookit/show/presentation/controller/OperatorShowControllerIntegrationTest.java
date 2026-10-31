package com.bookit.show.presentation.controller;

import com.bookit.catalogue.domain.exception.ResourceNotFoundException;
import com.bookit.identity.domain.UserRole;
import com.bookit.identity.infrastructure.security.JwtService;
import com.bookit.show.application.dto.ConfigureShowPricesRequest;
import com.bookit.show.application.dto.CreateShowRequest;
import com.bookit.show.application.dto.ShowResponse;
import com.bookit.show.application.service.ShowManagementService;
import com.bookit.show.domain.ShowStatus;
import com.bookit.show.domain.exception.ScheduleConflictException;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class OperatorShowControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @MockitoBean
    private ShowManagementService showManagementService;

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

    @Nested
    @DisplayName("Security & RBAC Enforcement")
    class SecurityTests {

        @Test
        @DisplayName("POST /api/v1/operator/shows without token should be rejected (403 Forbidden)")
        void createDraftShow_whenUnauthenticated_shouldReject() throws Exception {
            CreateShowRequest request = buildValidCreateShowRequest(UUID.randomUUID(), UUID.randomUUID());

            mockMvc.perform(post("/api/v1/operator/shows")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("POST /api/v1/operator/shows with CUSTOMER token should be forbidden (403)")
        void createDraftShow_whenCustomerRole_shouldBeForbidden() throws Exception {
            CreateShowRequest request = buildValidCreateShowRequest(UUID.randomUUID(), UUID.randomUUID());

            mockMvc.perform(post("/api/v1/operator/shows")
                            .header("Authorization", "Bearer " + customerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                    .andExpect(jsonPath("$.status").value(403));
        }
    }

    // =========================================================================
    // 2. Schedule Draft Show (POST /api/v1/operator/shows)
    // =========================================================================

    @Nested
    @DisplayName("POST /api/v1/operator/shows")
    class CreateDraftShowTests {

        @Test
        @DisplayName("As CINEMA_STAFF should create draft show (201 Created)")
        void createDraftShow_asStaff_shouldReturn201() throws Exception {
            UUID movieId = UUID.randomUUID();
            UUID screenId = UUID.randomUUID();
            UUID showId = UUID.randomUUID();
            CreateShowRequest request = buildValidCreateShowRequest(movieId, screenId);

            when(showManagementService.createDraftShow(any(CreateShowRequest.class)))
                    .thenReturn(buildSampleShowResponse(showId, movieId, screenId, ShowStatus.DRAFT));

            mockMvc.perform(post("/api/v1/operator/shows")
                            .header("Authorization", "Bearer " + staffToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(showId.toString()))
                    .andExpect(jsonPath("$.movieId").value(movieId.toString()))
                    .andExpect(jsonPath("$.screenId").value(screenId.toString()))
                    .andExpect(jsonPath("$.status").value("DRAFT"));

            verify(showManagementService).createDraftShow(any(CreateShowRequest.class));
        }

        @Test
        @DisplayName("With missing required fields should return 400 Bad Request")
        void createDraftShow_whenValidationFails_shouldReturn400() throws Exception {
            CreateShowRequest invalidRequest = new CreateShowRequest(
                    null, null, null, null, null, null, null
            );

            mockMvc.perform(post("/api/v1/operator/shows")
                            .header("Authorization", "Bearer " + staffToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.invalidFields").isArray());
        }

        @Test
        @DisplayName("When schedule conflict occurs should return 409 Conflict")
        void createDraftShow_whenScheduleConflict_shouldReturn409() throws Exception {
            UUID movieId = UUID.randomUUID();
            UUID screenId = UUID.randomUUID();
            CreateShowRequest request = buildValidCreateShowRequest(movieId, screenId);

            when(showManagementService.createDraftShow(any(CreateShowRequest.class)))
                    .thenThrow(new ScheduleConflictException("Screen already has an overlapping show scheduled for this time range"));

            mockMvc.perform(post("/api/v1/operator/shows")
                            .header("Authorization", "Bearer " + staffToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("SCHEDULE_CONFLICT"))
                    .andExpect(jsonPath("$.status").value(409));
        }

        @Test
        @DisplayName("When referenced movie/screen not found should return 404 Not Found")
        void createDraftShow_whenResourceNotFound_shouldReturn404() throws Exception {
            CreateShowRequest request = buildValidCreateShowRequest(UUID.randomUUID(), UUID.randomUUID());

            when(showManagementService.createDraftShow(any(CreateShowRequest.class)))
                    .thenThrow(new ResourceNotFoundException("Movie not found with id: " + request.movieId()));

            mockMvc.perform(post("/api/v1/operator/shows")
                            .header("Authorization", "Bearer " + staffToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                    .andExpect(jsonPath("$.status").value(404));
        }

        @Test
        @DisplayName("When seat map version is unsealed should return 400 Bad Request")
        void createDraftShow_whenSeatMapUnsealed_shouldReturn400() throws Exception {
            CreateShowRequest request = buildValidCreateShowRequest(UUID.randomUUID(), UUID.randomUUID());

            when(showManagementService.createDraftShow(any(CreateShowRequest.class)))
                    .thenThrow(new IllegalStateException("Cannot schedule show with an unsealed/draft seat map version"));

            mockMvc.perform(post("/api/v1/operator/shows")
                            .header("Authorization", "Bearer " + staffToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("ILLEGAL_STATE"))
                    .andExpect(jsonPath("$.status").value(400));
        }
    }

    // =========================================================================
    // 3. Configure Prices (PUT /api/v1/operator/shows/{showId}/prices)
    // =========================================================================

    @Nested
    @DisplayName("PUT /api/v1/operator/shows/{showId}/prices")
    class ConfigurePricesTests {

        @Test
        @DisplayName("As CINEMA_STAFF with valid prices should return 200 OK")
        void configurePrices_asStaff_shouldReturn200() throws Exception {
            UUID showId = UUID.randomUUID();
            ConfigureShowPricesRequest request = new ConfigureShowPricesRequest(List.of(
                    new ConfigureShowPricesRequest.CategoryPrice("RECLINER", BigDecimal.valueOf(550.00)),
                    new ConfigureShowPricesRequest.CategoryPrice("PRIME", BigDecimal.valueOf(350.00)),
                    new ConfigureShowPricesRequest.CategoryPrice("CLASSIC", BigDecimal.valueOf(200.00))
            ));

            when(showManagementService.configurePrices(eq(showId), any(ConfigureShowPricesRequest.class)))
                    .thenReturn(buildSampleShowResponse(showId, UUID.randomUUID(), UUID.randomUUID(), ShowStatus.DRAFT));

            mockMvc.perform(put("/api/v1/operator/shows/{showId}/prices", showId)
                            .header("Authorization", "Bearer " + staffToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(showId.toString()))
                    .andExpect(jsonPath("$.status").value("DRAFT"));

            verify(showManagementService).configurePrices(eq(showId), any(ConfigureShowPricesRequest.class));
        }

        @Test
        @DisplayName("With empty prices array should return 400 Bad Request")
        void configurePrices_whenEmptyPrices_shouldReturn400() throws Exception {
            UUID showId = UUID.randomUUID();
            ConfigureShowPricesRequest request = new ConfigureShowPricesRequest(List.of());

            mockMvc.perform(put("/api/v1/operator/shows/{showId}/prices", showId)
                            .header("Authorization", "Bearer " + staffToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.status").value(400));
        }

        @Test
        @DisplayName("When show not found should return 404 Not Found")
        void configurePrices_whenShowNotFound_shouldReturn404() throws Exception {
            UUID showId = UUID.randomUUID();
            ConfigureShowPricesRequest request = new ConfigureShowPricesRequest(List.of(
                    new ConfigureShowPricesRequest.CategoryPrice("RECLINER", BigDecimal.valueOf(500.00))
            ));

            when(showManagementService.configurePrices(eq(showId), any(ConfigureShowPricesRequest.class)))
                    .thenThrow(new ResourceNotFoundException("Show not found with id: " + showId));

            mockMvc.perform(put("/api/v1/operator/shows/{showId}/prices", showId)
                            .header("Authorization", "Bearer " + staffToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                    .andExpect(jsonPath("$.status").value(404));
        }

        @Test
        @DisplayName("When show is not in DRAFT status should return 400 Bad Request")
        void configurePrices_whenShowNotDraft_shouldReturn400() throws Exception {
            UUID showId = UUID.randomUUID();
            ConfigureShowPricesRequest request = new ConfigureShowPricesRequest(List.of(
                    new ConfigureShowPricesRequest.CategoryPrice("RECLINER", BigDecimal.valueOf(500.00))
            ));

            when(showManagementService.configurePrices(eq(showId), any(ConfigureShowPricesRequest.class)))
                    .thenThrow(new IllegalStateException("Pricing can only be configured for shows in DRAFT status. Current: PUBLISHED"));

            mockMvc.perform(put("/api/v1/operator/shows/{showId}/prices", showId)
                            .header("Authorization", "Bearer " + staffToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("ILLEGAL_STATE"))
                    .andExpect(jsonPath("$.status").value(400));
        }

        @Test
        @DisplayName("When category code is invalid should return 400 Bad Request")
        void configurePrices_whenCategoryMismatch_shouldReturn400() throws Exception {
            UUID showId = UUID.randomUUID();
            ConfigureShowPricesRequest request = new ConfigureShowPricesRequest(List.of(
                    new ConfigureShowPricesRequest.CategoryPrice("UNKNOWN_TIER", BigDecimal.valueOf(500.00))
            ));

            when(showManagementService.configurePrices(eq(showId), any(ConfigureShowPricesRequest.class)))
                    .thenThrow(new IllegalArgumentException("Category code 'UNKNOWN_TIER' is not present in this show's seat map layout"));

            mockMvc.perform(put("/api/v1/operator/shows/{showId}/prices", showId)
                            .header("Authorization", "Bearer " + staffToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"))
                    .andExpect(jsonPath("$.status").value(400));
        }
    }

    // =========================================================================
    // 4. Publish Show (POST /api/v1/operator/shows/{showId}/publish)
    // =========================================================================

    @Nested
    @DisplayName("POST /api/v1/operator/shows/{showId}/publish")
    class PublishShowTests {

        @Test
        @DisplayName("As CINEMA_STAFF should publish show and materialize inventory (200 OK)")
        void publishShow_asStaff_shouldReturn200() throws Exception {
            UUID showId = UUID.randomUUID();

            when(showManagementService.publishShow(eq(showId)))
                    .thenReturn(buildSampleShowResponse(showId, UUID.randomUUID(), UUID.randomUUID(), ShowStatus.PUBLISHED));

            mockMvc.perform(post("/api/v1/operator/shows/{showId}/publish", showId)
                            .header("Authorization", "Bearer " + staffToken)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(showId.toString()))
                    .andExpect(jsonPath("$.status").value("PUBLISHED"));

            verify(showManagementService).publishShow(eq(showId));
        }

        @Test
        @DisplayName("When show not found should return 404 Not Found")
        void publishShow_whenShowNotFound_shouldReturn404() throws Exception {
            UUID showId = UUID.randomUUID();

            when(showManagementService.publishShow(eq(showId)))
                    .thenThrow(new ResourceNotFoundException("Show not found with id: " + showId));

            mockMvc.perform(post("/api/v1/operator/shows/{showId}/publish", showId)
                            .header("Authorization", "Bearer " + staffToken)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                    .andExpect(jsonPath("$.status").value(404));
        }

        @Test
        @DisplayName("When show lacks active pricing should return 400 Bad Request")
        void publishShow_whenMissingPricing_shouldReturn400() throws Exception {
            UUID showId = UUID.randomUUID();

            when(showManagementService.publishShow(eq(showId)))
                    .thenThrow(new IllegalStateException("Cannot publish show without an ACTIVE price configuration"));

            mockMvc.perform(post("/api/v1/operator/shows/{showId}/publish", showId)
                            .header("Authorization", "Bearer " + staffToken)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("ILLEGAL_STATE"))
                    .andExpect(jsonPath("$.status").value(400));
        }
    }

    // =========================================================================
    // 5. Close Booking (POST /api/v1/operator/shows/{showId}/booking/close)
    // =========================================================================

    @Nested
    @DisplayName("POST /api/v1/operator/shows/{showId}/booking/close")
    class CloseBookingTests {

        @Test
        @DisplayName("As CINEMA_STAFF should close booking (200 OK)")
        void closeBooking_asStaff_shouldReturn200() throws Exception {
            UUID showId = UUID.randomUUID();

            when(showManagementService.closeBooking(eq(showId)))
                    .thenReturn(buildSampleShowResponse(showId, UUID.randomUUID(), UUID.randomUUID(), ShowStatus.BOOKING_CLOSED));

            mockMvc.perform(post("/api/v1/operator/shows/{showId}/booking/close", showId)
                            .header("Authorization", "Bearer " + staffToken)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(showId.toString()))
                    .andExpect(jsonPath("$.status").value("BOOKING_CLOSED"));

            verify(showManagementService).closeBooking(eq(showId));
        }

        @Test
        @DisplayName("When show not found should return 404 Not Found")
        void closeBooking_whenShowNotFound_shouldReturn404() throws Exception {
            UUID showId = UUID.randomUUID();

            when(showManagementService.closeBooking(eq(showId)))
                    .thenThrow(new ResourceNotFoundException("Show not found with id: " + showId));

            mockMvc.perform(post("/api/v1/operator/shows/{showId}/booking/close", showId)
                            .header("Authorization", "Bearer " + staffToken)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                    .andExpect(jsonPath("$.status").value(404));
        }

        @Test
        @DisplayName("When show is not in PUBLISHED status should return 400 Bad Request")
        void closeBooking_whenShowNotPublished_shouldReturn400() throws Exception {
            UUID showId = UUID.randomUUID();

            when(showManagementService.closeBooking(eq(showId)))
                    .thenThrow(new IllegalStateException("Only PUBLISHED shows can be closed for booking. Current status: DRAFT"));

            mockMvc.perform(post("/api/v1/operator/shows/{showId}/booking/close", showId)
                            .header("Authorization", "Bearer " + staffToken)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("ILLEGAL_STATE"))
                    .andExpect(jsonPath("$.status").value(400));
        }
    }

    // =========================================================================
    // 6. Cancel Show (POST /api/v1/operator/shows/{showId}/cancel)
    // =========================================================================

    @Nested
    @DisplayName("POST /api/v1/operator/shows/{showId}/cancel")
    class CancelShowTests {

        @Test
        @DisplayName("As CINEMA_STAFF should cancel show (200 OK)")
        void cancelShow_asStaff_shouldReturn200() throws Exception {
            UUID showId = UUID.randomUUID();

            when(showManagementService.cancelShow(eq(showId)))
                    .thenReturn(buildSampleShowResponse(showId, UUID.randomUUID(), UUID.randomUUID(), ShowStatus.CANCELLED));

            mockMvc.perform(post("/api/v1/operator/shows/{showId}/cancel", showId)
                            .header("Authorization", "Bearer " + staffToken)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(showId.toString()))
                    .andExpect(jsonPath("$.status").value("CANCELLED"));

            verify(showManagementService).cancelShow(eq(showId));
        }

        @Test
        @DisplayName("When show not found should return 404 Not Found")
        void cancelShow_whenShowNotFound_shouldReturn404() throws Exception {
            UUID showId = UUID.randomUUID();

            when(showManagementService.cancelShow(eq(showId)))
                    .thenThrow(new ResourceNotFoundException("Show not found with id: " + showId));

            mockMvc.perform(post("/api/v1/operator/shows/{showId}/cancel", showId)
                            .header("Authorization", "Bearer " + staffToken)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                    .andExpect(jsonPath("$.status").value(404));
        }

        @Test
        @DisplayName("When show is already cancelled should return 400 Bad Request")
        void cancelShow_whenAlreadyCancelled_shouldReturn400() throws Exception {
            UUID showId = UUID.randomUUID();

            when(showManagementService.cancelShow(eq(showId)))
                    .thenThrow(new IllegalStateException("Show is already cancelled"));

            mockMvc.perform(post("/api/v1/operator/shows/{showId}/cancel", showId)
                            .header("Authorization", "Bearer " + staffToken)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("ILLEGAL_STATE"))
                    .andExpect(jsonPath("$.status").value(400));
        }
    }

    // =========================================================================
    // Helper Test Fixtures
    // =========================================================================

    private CreateShowRequest buildValidCreateShowRequest(UUID movieId, UUID screenId) {
        Instant now = Instant.parse("2026-10-31T12:00:00Z");
        return new CreateShowRequest(
                movieId,
                screenId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                now.plus(2, ChronoUnit.HOURS),
                now.minus(1, ChronoUnit.DAYS),
                now.plus(1, ChronoUnit.HOURS)
        );
    }

    private ShowResponse buildSampleShowResponse(UUID showId, UUID movieId, UUID screenId, ShowStatus status) {
        Instant now = Instant.parse("2026-10-31T12:00:00Z");
        return new ShowResponse(
                showId,
                movieId,
                screenId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                now.plus(2, ChronoUnit.HOURS),
                now.plus(4, ChronoUnit.HOURS).plus(45, ChronoUnit.MINUTES),
                now.minus(1, ChronoUnit.DAYS),
                now.plus(1, ChronoUnit.HOURS),
                status
        );
    }
}

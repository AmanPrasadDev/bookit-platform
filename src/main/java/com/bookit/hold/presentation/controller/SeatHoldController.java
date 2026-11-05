package com.bookit.hold.presentation.controller;

import com.bookit.hold.application.dto.CreateSeatHoldRequest;
import com.bookit.hold.application.dto.HoldSeatsRequest;
import com.bookit.hold.application.dto.SeatHoldResponse;
import com.bookit.hold.application.service.SeatHoldService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for customer seat hold operations.
 * Allows authenticated customers to reserve, release, and inspect temporary seat holds.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Tag(name = "Seat Holds", description = "Endpoints for reserving, releasing, and inspecting temporary seat holds")
public class SeatHoldController {

    private final SeatHoldService seatHoldService;

    @PostMapping("/shows/{showId}/holds")
    @Operation(summary = "Hold seats for a show",
            description = "Reserves a batch of seats (1-10) for 10 minutes using Redis distributed locking and PostgreSQL pessimistic row locks.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Seats held successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload or violation of anti-scalping limits"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Authentication required"),
            @ApiResponse(responseCode = "404", description = "Show or requested seats not found"),
            @ApiResponse(responseCode = "409", description = "Conflict - Seats already held or booked by another customer")
    })
    public ResponseEntity<SeatHoldResponse> holdSeats(
            @PathVariable UUID showId,
            @Valid @RequestBody HoldSeatsRequest request,
            @AuthenticationPrincipal String principal
    ) {
        UUID customerId = UUID.fromString(principal);
        log.info("Received request to hold {} seats for show {} by customer {}", request.seatIds().size(), showId, customerId);

        CreateSeatHoldRequest createRequest = new CreateSeatHoldRequest(showId, request.seatIds());
        SeatHoldResponse response = seatHoldService.holdSeats(createRequest, customerId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/holds/{holdId}/release")
    @Operation(summary = "Release an active seat hold",
            description = "Customer explicitly cancels their seat hold session, releasing Redis locks and resetting seat state to AVAILABLE.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Hold released successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Authentication required"),
            @ApiResponse(responseCode = "404", description = "Seat hold not found for the customer")
    })
    public ResponseEntity<SeatHoldResponse> releaseHold(
            @PathVariable UUID holdId,
            @AuthenticationPrincipal String principal
    ) {
        UUID customerId = UUID.fromString(principal);
        log.info("Received request to release hold {} by customer {}", holdId, customerId);

        SeatHoldResponse response = seatHoldService.releaseHold(holdId, customerId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/holds/{holdId}")
    @Operation(summary = "Get seat hold details",
            description = "Inspect an existing seat hold session, remaining TTL, and held seat IDs.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Seat hold retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Authentication required"),
            @ApiResponse(responseCode = "404", description = "Seat hold not found for the customer")
    })
    public ResponseEntity<SeatHoldResponse> getHold(
            @PathVariable UUID holdId,
            @AuthenticationPrincipal String principal
    ) {
        UUID customerId = UUID.fromString(principal);
        log.debug("Received request to fetch hold {} by customer {}", holdId, customerId);

        SeatHoldResponse response = seatHoldService.getHold(holdId, customerId);
        return ResponseEntity.ok(response);
    }
}

package com.bookit.catalogue.presentation.controller;

import com.bookit.catalogue.application.dto.*;
import com.bookit.catalogue.application.service.VenueManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/operator")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('CINEMA_STAFF', 'SUPER_ADMIN')")
@Tag(name = "Operator Venue Management", description = "Privileged APIs for cinema operators to manage venues, screens, and seat layouts")
public class OperatorVenueController {

    private final VenueManagementService venueManagementService;

    @PostMapping("/cinemas")
    @Operation(summary = "Create cinema", description = "Creates a new cinema under an organization and city")
    public ResponseEntity<CinemaResponse> createCinema(
            @Valid @RequestBody CreateCinemaRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(venueManagementService.createCinema(request));
    }

    @PostMapping("/screens")
    @Operation(summary = "Create screen", description = "Creates a new auditorium screen within a cinema")
    public ResponseEntity<ScreenResponse> createScreen(
            @Valid @RequestBody CreateScreenRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(venueManagementService.createScreen(request));
    }

    @PostMapping("/seat-map-drafts")
    @Operation(summary = "Create seat map draft", description = "Creates a new draft seat map version with auto-incremented version number")
    public ResponseEntity<SeatMapResponse> createSeatMapDraft(
            @Valid @RequestBody CreateSeatMapDraftRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(venueManagementService.createSeatMapDraft(request));
    }

    @PutMapping("/seat-maps/{versionId}/layout")
    @Operation(summary = "Define seat layout", description = "Defines or replaces sections and physical seats for a draft seat map (strictly immutable once published)")
    public ResponseEntity<SeatMapResponse> defineSeatLayout(
            @PathVariable UUID versionId,
            @Valid @RequestBody DefineSeatLayoutRequest request
    ) {
        return ResponseEntity.ok(venueManagementService.defineSeatLayout(versionId, request));
    }

    @PostMapping("/seat-maps/{versionId}/publish")
    @Operation(summary = "Publish seat map version", description = "Transitions a draft seat map with non-zero seats to PUBLISHED status")
    public ResponseEntity<SeatMapResponse> publishSeatLayout(
            @PathVariable UUID versionId
    ) {
        return ResponseEntity.ok(venueManagementService.publishSeatMapVersion(versionId));
    }
}
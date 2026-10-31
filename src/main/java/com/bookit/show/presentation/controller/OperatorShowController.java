package com.bookit.show.presentation.controller;

import com.bookit.show.application.dto.ConfigureShowPricesRequest;
import com.bookit.show.application.dto.CreateShowRequest;
import com.bookit.show.application.dto.ShowResponse;
import com.bookit.show.application.service.ShowManagementService;
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
@RequestMapping("/api/v1/operator/shows")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('CINEMA_STAFF', 'SUPER_ADMIN')")
@Tag(name = "Operator Show Management",
        description = "Privileged APIs for cinema operators to schedule, price, publish and close shows")
public class OperatorShowController {

    private final ShowManagementService showManagementService;

    @PostMapping
    @Operation(summary = "Schedule draft show",
            description = "Creates a new movie screening in DRAFT state. Start/end times must be non-overlapping with existing shows on the screen.")
    public ResponseEntity<ShowResponse> createDraftShow(
            @Valid @RequestBody CreateShowRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(showManagementService.createDraftShow(request));
    }

    @PutMapping("/{showId}/prices")
    @Operation(summary = "Configure category prices for a show",
            description = "Configures or replaces tier pricing per seat-section category for a DRAFT show.")
    public ResponseEntity<ShowResponse> configurePrices(
            @PathVariable UUID showId,
            @Valid @RequestBody ConfigureShowPricesRequest request
    ) {
        return ResponseEntity.ok(showManagementService.configurePrices(showId, request));
    }

    @PostMapping("/{showId}/publish")
    @Operation(summary = "Publish show and materialize seat inventory",
            description = "Publishes a DRAFT show with configured pricing and materializes physical seats into sellable show_seat inventory.")
    public ResponseEntity<ShowResponse> publishShow(
            @PathVariable UUID showId
    ) {
        return ResponseEntity.ok(showManagementService.publishShow(showId));
    }

    @PostMapping("/{showId}/booking/close")
    @Operation(summary = "Close booking for a show",
            description = "Closes ticket booking when cutoff window is reached or manually closed by cinema staff.")
    public ResponseEntity<ShowResponse> closeBooking(
            @PathVariable UUID showId
    ) {
        return ResponseEntity.ok(showManagementService.closeBooking(showId));
    }

    @PostMapping("/{showId}/cancel")
    @Operation(summary = "Cancel a show and free its screen slot",
            description = "Cancels a scheduled show and frees up the auditorium screen slot in the GiST exclusion constraint.")
    public ResponseEntity<ShowResponse> cancelShow(
            @PathVariable UUID showId
    ) {
        return ResponseEntity.ok(showManagementService.cancelShow(showId));
    }
}

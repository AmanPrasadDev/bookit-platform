package com.bookit.show.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Aggregate Root representing an individual movie screening on a physical screen at a specific time.
 * In accordance with Domain-Driven Design (DDD), cross-boundary references to the Catalogue
 * module (Movie, Screen, SeatMapVersion, MovieFormat) are held by UUID rather than direct JPA entities.
 */
@Entity
@Table(name = "\"show\"")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Show {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "movie_id", nullable = false)
    private UUID movieId;

    @Column(name = "screen_id", nullable = false)
    private UUID screenId;

    @Column(name = "seat_map_version_id", nullable = false)
    private UUID seatMapVersionId;

    @Column(name = "format_id", nullable = false)
    private UUID formatId;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "booking_opens_at", nullable = false)
    private Instant bookingOpensAt;

    @Column(name = "booking_closes_at", nullable = false)
    private Instant bookingClosesAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ShowStatus status = ShowStatus.DRAFT;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Validates domain temporal invariants.
     */
    public void validateTimeWindows() {
        if (endsAt == null || startsAt == null || !endsAt.isAfter(startsAt)) {
            throw new IllegalArgumentException("Show end time must be strictly after start time");
        }
        if (bookingClosesAt == null || bookingOpensAt == null || !bookingClosesAt.isAfter(bookingOpensAt)) {
            throw new IllegalArgumentException("Booking close time must be strictly after booking open time");
        }
        if (bookingClosesAt.isAfter(endsAt)) {
            throw new IllegalArgumentException("Booking close time cannot be after the show end time");
        }
    }

    /**
     * Transitions show to PUBLISHED state when pricing is configured and inventory is materialized.
     */
    public void publish() {
        if (this.status != ShowStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT shows can be published. Current status: " + this.status);
        }
        validateTimeWindows();
        this.status = ShowStatus.PUBLISHED;
    }

    /**
     * Closes booking when cutoff is reached or cinema staff closes sales.
     */
    public void closeBooking() {
        if (this.status != ShowStatus.PUBLISHED) {
            throw new IllegalStateException("Only PUBLISHED shows can be closed for booking. Current status: " + this.status);
        }
        this.status = ShowStatus.BOOKING_CLOSED;
    }

    /**
     * Cancels screening and frees up the screen time slot.
     */
    public void cancel() {
        if (this.status == ShowStatus.CANCELLED) {
            throw new IllegalStateException("Show is already cancelled");
        }
        this.status = ShowStatus.CANCELLED;
    }
}

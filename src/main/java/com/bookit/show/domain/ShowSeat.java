package com.bookit.show.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Materialized atomic inventory row representing a single bookable seat for a specific show.
 * Employs optimistic locking via @Version to prevent lost updates under high concurrency.
 */
@Entity
@Table(name = "show_seat")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShowSeat {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "show_id", nullable = false)
    private UUID showId;

    @Column(name = "physical_seat_id", nullable = false)
    private UUID physicalSeatId;

    @Column(name = "seat_section_id", nullable = false)
    private UUID seatSectionId;

    @Column(name = "row_label", nullable = false, length = 10)
    private String rowLabel;

    @Column(name = "seat_number", nullable = false)
    private int seatNumber;

    @Column(name = "seat_type", nullable = false, length = 20)
    @Builder.Default
    private String seatType = "REGULAR";

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 20)
    @Builder.Default
    private ShowSeatState state = ShowSeatState.AVAILABLE;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    @Column(name = "active_hold_id")
    private UUID activeHoldId;

    @Column(name = "sold_booking_id")
    private UUID soldBookingId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Transitions seat from AVAILABLE to HELD under a customer hold session.
     */
    public void hold(UUID holdId) {
        if (this.state != ShowSeatState.AVAILABLE) {
            throw new IllegalStateException("Seat is not available for hold. Current state: " + this.state);
        }
        this.state = ShowSeatState.HELD;
        this.activeHoldId = holdId;
    }

    /**
     * Releases an expired or cancelled hold back to AVAILABLE.
     */
    public void release() {
        if (this.state != ShowSeatState.HELD) {
            throw new IllegalStateException("Only held seats can be released. Current state: " + this.state);
        }
        this.state = ShowSeatState.AVAILABLE;
        this.activeHoldId = null;
    }

    /**
     * Converts a held seat to permanently SOLD upon confirmed booking payment.
     */
    public void sell(UUID bookingId) {
        if (this.state != ShowSeatState.HELD) {
            throw new IllegalStateException("Only held seats can be sold. Current state: " + this.state);
        }
        this.state = ShowSeatState.SOLD;
        this.soldBookingId = bookingId;
        this.activeHoldId = null;
    }

    /**
     * Administratively blocks an available seat.
     */
    public void block() {
        if (this.state != ShowSeatState.AVAILABLE) {
            throw new IllegalStateException("Only available seats can be blocked. Current state: " + this.state);
        }
        this.state = ShowSeatState.BLOCKED;
    }

    /**
     * Unblocks a blocked seat back to AVAILABLE.
     */
    public void unblock() {
        if (this.state != ShowSeatState.BLOCKED) {
            throw new IllegalStateException("Only blocked seats can be unblocked. Current state: " + this.state);
        }
        this.state = ShowSeatState.AVAILABLE;
    }
}

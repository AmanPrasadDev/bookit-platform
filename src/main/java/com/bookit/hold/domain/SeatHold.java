package com.bookit.hold.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Ephemeral seat hold session granting a customer exclusive purchase rights for a bounded time window.
 */
@Entity
@Table(name = "seat_hold")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeatHold {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "show_id", nullable = false)
    private UUID showId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private SeatHoldStatus status = SeatHoldStatus.HELD;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Checks if this hold has expired relative to the system or test clock.
     */
    public boolean isExpired(Clock clock) {
        return clock.instant().isAfter(this.expiresAt);
    }

    /**
     * Customer voluntarily releases the hold prior to checkout.
     */
    public void release() {
        if (this.status != SeatHoldStatus.HELD) {
            throw new IllegalStateException("Only active HELD holds can be released. Current status: " + this.status);
        }
        this.status = SeatHoldStatus.RELEASED;
    }

    /**
     * System marks the hold expired when the TTL window lapses.
     */
    public void expire() {
        if (this.status != SeatHoldStatus.HELD) {
            throw new IllegalStateException("Only active HELD holds can be expired. Current status: " + this.status);
        }
        this.status = SeatHoldStatus.EXPIRED;
    }

    /**
     * Converts hold to finalized booking state upon confirmed payment.
     */
    public void convert() {
        if (this.status != SeatHoldStatus.HELD) {
            throw new IllegalStateException("Only active HELD holds can be converted to confirmed booking. Current status: " + this.status);
        }
        this.status = SeatHoldStatus.CONVERTED;
    }
}

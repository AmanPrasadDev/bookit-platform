package com.bookit.show.domain;

/**
 * High-concurrency state machine of an individual materialized show seat.
 */
public enum ShowSeatState {
    /**
     * Seat is open and available to be selected and held by any customer.
     */
    AVAILABLE,

    /**
     * Seat is locked under an active hold for a customer with a 10-minute TTL.
     */
    HELD,

    /**
     * Payment has succeeded and ticket has been issued. Seat is permanently sold.
     */
    SOLD,

    /**
     * Seat is administratively blocked (e.g. broken recliner or VIP reservation).
     */
    BLOCKED
}

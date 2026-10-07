package com.bookit.show.domain;

/**
 * Lifecycle state machine of a cinema screening.
 */
public enum ShowStatus {
    /**
     * Show scheduled by cinema operator, but pricing is still being configured
     * and seats are not yet bookable by customers.
     */
    DRAFT,

    /**
     * Show published, seat inventory materialized, and customers can discover and book seats.
     */
    PUBLISHED,

    /**
     * Booking window closed (e.g. show cutoff reached). No new holds permitted.
     */
    BOOKING_CLOSED,

    /**
     * Show cancelled due to operational reasons. Frees the screen slot in PostgreSQL exclusion constraint.
     */
    CANCELLED
}

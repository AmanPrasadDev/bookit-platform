package com.bookit.hold.domain;

/**
 * Lifecycle states of a seat hold session.
 */
public enum SeatHoldStatus {
    /**
     * Active hold reserving seats for checkout (standard 10-minute TTL).
     */
    HELD,

    /**
     * Explicitly released by the customer before checkout completion.
     */
    RELEASED,

    /**
     * Hold timer expired without completing payment; inventory returned to AVAILABLE.
     */
    EXPIRED,

    /**
     * Successfully converted into a confirmed booking following payment authorization.
     */
    CONVERTED
}

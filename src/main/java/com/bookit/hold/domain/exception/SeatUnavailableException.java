package com.bookit.hold.domain.exception;

/**
 * Thrown when one or more requested seats cannot be held because they are already
 * held by another customer, sold, or administratively blocked.
 */
public class SeatUnavailableException extends RuntimeException {

    public SeatUnavailableException(String message) {
        super(message);
    }

    public SeatUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

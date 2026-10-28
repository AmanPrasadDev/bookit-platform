package com.bookit.show.domain.exception;

/**
 * Thrown when a show cannot be scheduled because its time range overlaps another active show
 * on the same screen. Raised by translating PostgreSQL's exclusion-constraint violation
 * ({@code no_overlapping_screen_shows}, SQLState 23P01); mapped to HTTP 409.
 */
public class ScheduleConflictException extends RuntimeException {
    public ScheduleConflictException(String message) {
        super(message);
    }

    public ScheduleConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}

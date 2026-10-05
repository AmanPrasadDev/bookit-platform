package com.bookit.catalogue.domain.exception;

public class SeatMapImmutableException extends RuntimeException {
    public SeatMapImmutableException(String message) {
        super(message);
    }
    public SeatMapImmutableException(String message, Throwable cause) {
        super(message, cause);
    }
}

package com.bookit.show.domain;

/**
 * Lifecycle status of a show pricing configuration.
 */
public enum PriceConfigurationStatus {
    /**
     * Currently active pricing schema for quoting and booking seats for this show.
     */
    ACTIVE,

    /**
     * Historical or archived pricing revision superseded by a newer version.
     */
    ARCHIVED
}

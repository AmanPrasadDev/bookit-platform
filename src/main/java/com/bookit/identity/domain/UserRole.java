package com.bookit.identity.domain;

/**
 * Platform roles defining authorization boundaries.
 */
public enum UserRole {
    PLATFORM_ADMIN,     // System administrators (global control)
    ORGANIZATION_ADMIN, // Cinema chain admins (e.g. PVR admin, scoped to organization_id)
    CINEMA_STAFF,       // Venue staff (e.g. ticket scanners, scoped to cinema_id)
    CUSTOMER            // End-user ticket buyers
}
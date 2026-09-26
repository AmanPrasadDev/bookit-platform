package com.bookit.identity.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a role assigned to a user, optionally scoped to a specific
 * cinema chain (organizationId) or physical cinema venue (cinemaId).
 */
@Entity
@Table(name = "role_assignment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoleAssignment {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 64)
    private UserRole role;

    /**
     * Scopes an ORGANIZATION_ADMIN to their specific cinema chain.
     * Null for customers and platform admins.
     */
    @Column(name = "organization_id")
    private UUID organizationId;

    /**
     * Scopes CINEMA_STAFF to a specific physical cinema venue.
     * Null for customers and platform admins.
     */
    @Column(name = "cinema_id")
    private UUID cinemaId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
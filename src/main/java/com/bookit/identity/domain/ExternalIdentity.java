package com.bookit.identity.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Maps an internal BookIt User to an external identity provider (Google, Phone OTP, etc.).
 * Enables one user to have multiple login methods pointing to the same internal account.
 */
@Entity
@Table(
        name = "external_identity",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_provider_user_id", columnNames = {"provider", "provider_user_id"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExternalIdentity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * Many external identities (e.g. Google AND Phone) can belong to ONE internal User.
     * FetchType.LAZY ensures we only load the full User object when explicitly requested.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 32)
    private IdentityProvider provider;

    /**
     * The unique subject ID from the provider:
     * - For Google: the 'sub' claim from Google's id_token
     * - For Phone: the E.164 formatted phone number (e.g. +919876543210)
     */
    @Column(name = "provider_user_id", nullable = false)
    private String providerUserId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
package com.bookit.identity.infrastructure.repository;

import com.bookit.identity.domain.ExternalIdentity;
import com.bookit.identity.domain.IdentityProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for external identity mappings.
 * Maps external OAuth / Phone credentials to internal BookIt platform users.
 */
@Repository
public interface ExternalIdentityRepository extends JpaRepository<ExternalIdentity, UUID> {

    /**
     * Finds an external identity by its provider (e.g. GOOGLE) and provider-specific ID (e.g. 'sub' claim).
     */
    Optional<ExternalIdentity> findByProviderAndProviderUserId(IdentityProvider provider, String providerUserId);

    /**
     * Retrieves all external identities linked to a specific BookIt user.
     * Navigates the 'user' relationship to match 'user.id'.
     */
    List<ExternalIdentity> findAllByUserId(UUID userId);

    /**
     * Checks if a provider-linked account already exists.
     */
    boolean existsByProviderAndProviderUserId(IdentityProvider provider, String providerUserId);
}
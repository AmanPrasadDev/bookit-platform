package com.bookit.identity.infrastructure.repository;

import com.bookit.identity.domain.RoleAssignment;
import com.bookit.identity.domain.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for user role assignments and security scopes.
 */
@Repository
public interface RoleAssignmentRepository extends JpaRepository<RoleAssignment, UUID> {

    /**
     * Retrieves all active roles and scopes for a given user.
     * Used during JWT generation to embed granted authorities.
     */
    List<RoleAssignment> findAllByUserId(UUID userId);

    /**
     * Checks if a user already holds a specific role globally.
     */
    boolean existsByUserIdAndRole(UUID userId, UserRole role);

    /**
     * Finds all staff members assigned to a specific physical cinema venue.
     */
    List<RoleAssignment> findAllByCinemaId(UUID cinemaId);

    /**
     * Finds all administrators assigned to a specific cinema chain/organization.
     */
    List<RoleAssignment> findAllByOrganizationId(UUID organizationId);
}
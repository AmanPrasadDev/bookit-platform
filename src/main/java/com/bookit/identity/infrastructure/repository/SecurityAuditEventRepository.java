package com.bookit.identity.infrastructure.repository;

import com.bookit.identity.domain.SecurityAuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Spring Data JPA repository for the immutable security audit trail.
 * Uses pagination to protect memory during heavy audit log queries.
 */
@Repository
public interface SecurityAuditEventRepository extends JpaRepository<SecurityAuditEvent, UUID> {

    /**
     * Retrieves paginated security audit events for a specific user, most recent first.
     */
    Page<SecurityAuditEvent> findAllByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /**
     * Retrieves paginated events by event type (e.g. 'TOKEN_REUSE_DETECTED', 'LOGIN_FAILURE').
     */
    Page<SecurityAuditEvent> findAllByEventTypeOrderByCreatedAtDesc(String eventType, Pageable pageable);
}
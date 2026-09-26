package com.bookit.identity.infrastructure.repository;

import com.bookit.identity.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for the User aggregate.
 * Generates SQL queries automatically at runtime based on method signature conventions.
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Looks up a user by email ignoring case differences (e.g. 'Aman@x.com' matches 'aman@x.com').
     */
    Optional<User> findByEmailIgnoreCase(String email);

    /**
     * Looks up a user by exact verified phone number (E.164 format, e.g. +919876543210).
     */
    Optional<User> findByPhoneNumber(String phoneNumber);

    /**
     * Efficient existence check for registration uniqueness validation.
     */
    boolean existsByEmailIgnoreCase(String email);

    /**
     * Efficient existence check for phone registration.
     */
    boolean existsByPhoneNumber(String phoneNumber);
}
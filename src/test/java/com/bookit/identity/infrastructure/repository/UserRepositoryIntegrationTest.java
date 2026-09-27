package com.bookit.identity.infrastructure.repository;

import com.bookit.identity.domain.User;
import com.bookit.identity.domain.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test suite for UserRepository.
 * Uses @SpringBootTest with @ActiveProfiles("local") to execute against real PostgreSQL in Docker.
 * Uses @Transactional to ensure automatic test rollback and clean state isolation.
 */
@SpringBootTest
@ActiveProfiles("local")
@Transactional
class UserRepositoryIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("should save a user and find them by email ignoring case")
    void shouldSaveAndFindUserByEmail() {
        // Given: a registered user with lowercase email
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("aman@bookit.com")
                .fullName("Aman Prasad")
                .phoneNumber("+919876543210")
                .status(UserStatus.ACTIVE)
                .build();
        userRepository.save(user);

        // When: queried with mixed-case email
        Optional<User> found = userRepository.findByEmailIgnoreCase("Aman@BookIt.com");

        // Then: user is found with matching properties
        assertThat(found).isPresent();
        assertThat(found.get().getFullName()).isEqualTo("Aman Prasad");
        assertThat(found.get().getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("should return empty optional when querying for non-existent email")
    void shouldReturnEmptyWhenEmailNotFound() {
        // When: queried for an unregistered email
        Optional<User> found = userRepository.findByEmailIgnoreCase("nonexistent@bookit.com");

        // Then: empty optional is returned
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("should save a user and find them by exact phone number")
    void shouldSaveAndFindByPhoneNumber() {
        // Given: a registered user with E.164 phone number
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("aman.phone@bookit.com")
                .fullName("Aman Prasad")
                .phoneNumber("+919876543210")
                .status(UserStatus.ACTIVE)
                .build();
        userRepository.save(user);

        // When: queried by phone number
        Optional<User> found = userRepository.findByPhoneNumber("+919876543210");

        // Then: user is found
        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("aman.phone@bookit.com");
    }

    @Test
    @DisplayName("should return empty optional when querying for non-existent phone number")
    void shouldReturnEmptyWhenPhoneNotFound() {
        // When: queried for an unregistered phone number
        Optional<User> found = userRepository.findByPhoneNumber("+910000000000");

        // Then: empty optional is returned
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("should return true when email exists in database (case-insensitive)")
    void shouldReturnTrueWhenEmailExists() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("exist@bookit.com")
                .fullName("Existing User")
                .status(UserStatus.ACTIVE)
                .build();
        userRepository.save(user);

        boolean exists = userRepository.existsByEmailIgnoreCase("EXIST@Bookit.com");

        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("should return false when email does not exist in database")
    void shouldReturnFalseWhenEmailDoesNotExist() {
        boolean exists = userRepository.existsByEmailIgnoreCase("unknown@bookit.com");

        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("should return true when phone number exists in database")
    void shouldReturnTrueWhenPhoneExists() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("phone.exist@bookit.com")
                .fullName("Phone User")
                .phoneNumber("+919111122223")
                .status(UserStatus.ACTIVE)
                .build();
        userRepository.save(user);

        boolean exists = userRepository.existsByPhoneNumber("+919111122223");

        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("should return false when phone number does not exist in database")
    void shouldReturnFalseWhenPhoneDoesNotExist() {
        boolean exists = userRepository.existsByPhoneNumber("+910999999999");

        assertThat(exists).isFalse();
    }
}
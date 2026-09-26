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
 * Integration test for UserRepository.
 * Uses @SpringBootTest with @ActiveProfiles("local") to connect to Docker PostgreSQL.
 * Annotated with @Transactional so every test rolls back automatically upon completion.
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
        // Given: a new user entity with lowercase email
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("aman@bookit.com")
                .fullName("Aman Prasad")
                .phoneNumber("+919876543210")
                .status(UserStatus.ACTIVE)
                .build();

        // When: user is saved to PostgreSQL
        userRepository.save(user);

        // Then: looking up with mixed-case email must find the user
        Optional<User> foundUser = userRepository.findByEmailIgnoreCase("Aman@BookIt.com");

        assertThat(foundUser).isPresent();
        assertThat(foundUser.get().getFullName()).isEqualTo("Aman Prasad");
        assertThat(foundUser.get().getStatus()).isEqualTo(UserStatus.ACTIVE);
    }
}
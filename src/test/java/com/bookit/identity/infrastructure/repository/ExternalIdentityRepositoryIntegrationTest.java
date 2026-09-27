package com.bookit.identity.infrastructure.repository;

import com.bookit.identity.domain.ExternalIdentity;
import com.bookit.identity.domain.IdentityProvider;
import com.bookit.identity.domain.User;
import com.bookit.identity.domain.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
class ExternalIdentityRepositoryIntegrationTest {

    @Autowired
    private ExternalIdentityRepository externalIdentityRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("should save an external identity and find it by provider and providerUserId")
    void shouldSaveAndFindExternalIdentity() {
        // Given: a saved user and an external identity
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("aman@bookit.com")
                .fullName("Aman Prasad")
                .phoneNumber("+919876543210")
                .status(UserStatus.ACTIVE)
                .build();
        userRepository.save(user);

        ExternalIdentity identity = ExternalIdentity.builder()
                .id(UUID.randomUUID())
                .user(user)
                .provider(IdentityProvider.GOOGLE)
                .providerUserId("google-sub-12345")
                .build();
        externalIdentityRepository.save(identity); // <-- Crucial save!

        // When: queried by correct provider and providerUserId
        Optional<ExternalIdentity> found = externalIdentityRepository.findByProviderAndProviderUserId(
                IdentityProvider.GOOGLE, "google-sub-12345"
        );

        // Then: identity is found with matching details
        assertThat(found).isPresent();
        assertThat(found.get().getProvider()).isEqualTo(IdentityProvider.GOOGLE);
        assertThat(found.get().getProviderUserId()).isEqualTo("google-sub-12345");
        assertThat(found.get().getUser().getId()).isEqualTo(user.getId());
    }

    @Test
    @DisplayName("should return empty optional when searched with wrong providerUserId")
    void shouldReturnEmptyWhenProviderUserIdIsWrong() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("aman@bookit.com")
                .fullName("Aman Prasad")
                .status(UserStatus.ACTIVE)
                .build();
        userRepository.save(user);

        ExternalIdentity identity = ExternalIdentity.builder()
                .id(UUID.randomUUID())
                .user(user)
                .provider(IdentityProvider.GOOGLE)
                .providerUserId("google-sub-12345")
                .build();
        externalIdentityRepository.save(identity);

        Optional<ExternalIdentity> found = externalIdentityRepository.findByProviderAndProviderUserId(
                IdentityProvider.GOOGLE, "wrong-sub-99999"
        );

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("should return empty optional when searched with wrong provider enum")
    void shouldReturnEmptyWhenProviderIsWrong() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("aman@bookit.com")
                .fullName("Aman Prasad")
                .status(UserStatus.ACTIVE)
                .build();
        userRepository.save(user);

        ExternalIdentity identity = ExternalIdentity.builder()
                .id(UUID.randomUUID())
                .user(user)
                .provider(IdentityProvider.GOOGLE)
                .providerUserId("google-sub-12345")
                .build();
        externalIdentityRepository.save(identity);

        Optional<ExternalIdentity> found = externalIdentityRepository.findByProviderAndProviderUserId(
                IdentityProvider.PHONE, "google-sub-12345"
        );

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("should find all external identities linked to a specific user")
    void shouldFindAllIdentitiesByUserId() {
        // Given: a user with TWO linked identities (Google + Phone)
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("aman@bookit.com")
                .fullName("Aman Prasad")
                .status(UserStatus.ACTIVE)
                .build();
        userRepository.save(user);

        ExternalIdentity googleIdentity = ExternalIdentity.builder()
                .id(UUID.randomUUID())
                .user(user)
                .provider(IdentityProvider.GOOGLE)
                .providerUserId("google-sub-101")
                .build();
        ExternalIdentity phoneIdentity = ExternalIdentity.builder()
                .id(UUID.randomUUID())
                .user(user)
                .provider(IdentityProvider.PHONE)
                .providerUserId("+919876543210")
                .build();
        externalIdentityRepository.save(googleIdentity);
        externalIdentityRepository.save(phoneIdentity);

        // When: queried by userId
        List<ExternalIdentity> identities = externalIdentityRepository.findAllByUserId(user.getId());

        // Then: both accounts are returned
        assertThat(identities).hasSize(2);
    }

    @Test
    @DisplayName("should return true when provider and providerUserId exist")
    void shouldReturnTrueWhenProviderAccountExists() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("test@bookit.com")
                .fullName("Test User")
                .status(UserStatus.ACTIVE)
                .build();
        userRepository.save(user);

        ExternalIdentity identity = ExternalIdentity.builder()
                .id(UUID.randomUUID())
                .user(user)
                .provider(IdentityProvider.GOOGLE)
                .providerUserId("existing-sub-555")
                .build();
        externalIdentityRepository.save(identity);

        boolean exists = externalIdentityRepository.existsByProviderAndProviderUserId(
                IdentityProvider.GOOGLE, "existing-sub-555"
        );
        boolean notExists = externalIdentityRepository.existsByProviderAndProviderUserId(
                IdentityProvider.GOOGLE, "non-existing-sub-000"
        );

        assertThat(exists).isTrue();
        assertThat(notExists).isFalse();
    }
}
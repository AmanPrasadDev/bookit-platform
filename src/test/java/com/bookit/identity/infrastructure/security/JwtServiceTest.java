package com.bookit.identity.infrastructure.security;

import com.bookit.identity.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    // 256-bit test secret (at least 64 characters)
    private static final String TEST_SECRET = "super-secret-key-that-is-at-least-256-bits-long-for-bookit-platform-tests!";
    private static final String ISSUER = "bookit-test";
    private static final long EXPIRATION_MINUTES = 30;

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(TEST_SECRET, ISSUER, EXPIRATION_MINUTES);
    }

    @Test
    @DisplayName("Should generate a valid token with all claims and optional cinemaId")
    void shouldGenerateValidTokenWithAllClaims() {
        UUID userId = UUID.randomUUID();
        UUID cinemaId = UUID.randomUUID();
        String email = "aman@bookit.com";
        List<UserRole> roles = List.of(UserRole.CINEMA_STAFF, UserRole.PLATFORM_ADMIN);

        String token = jwtService.generateAccessToken(userId, email, roles, cinemaId);

        assertThat(jwtService.isTokenValid(token)).isTrue();
        assertThat(jwtService.extractUserId(token)).isEqualTo(userId);
        assertThat(jwtService.extractEmail(token)).isEqualTo(email);
        assertThat(jwtService.extractRoles(token)).containsExactly("CINEMA_STAFF", "PLATFORM_ADMIN");
        assertThat(jwtService.extractCinemaId(token)).contains(cinemaId);
    }

    @Test
    @DisplayName("Should handle customer tokens without cinemaId gracefully")
    void shouldHandleCustomerTokensWithoutCinemaId() {
        UUID userId = UUID.randomUUID();
        String email = "customer@bookit.com";
        List<UserRole> roles = List.of(UserRole.CUSTOMER);

        String token = jwtService.generateAccessToken(userId, email, roles, null);

        assertThat(jwtService.isTokenValid(token)).isTrue();
        assertThat(jwtService.extractCinemaId(token)).isEmpty();
    }

    @Test
    @DisplayName("Should reject tampered token signatures")
    void shouldRejectTamperedToken() {
        UUID userId = UUID.randomUUID();
        String email = "aman@bookit.com";
        List<UserRole> roles = List.of(UserRole.CUSTOMER);

        String token = jwtService.generateAccessToken(userId, email, roles, null);
        String tamperedToken = token + "tampered";

        assertThat(jwtService.isTokenValid(tamperedToken)).isFalse();
    }

    @Test
    @DisplayName("Should reject expired tokens")
    void shouldRejectExpiredToken() {
        // Service configured with negative expiration (-1 minute)
        JwtService expiredJwtService = new JwtService(TEST_SECRET, ISSUER, -1);

        UUID userId = UUID.randomUUID();
        String email = "aman@bookit.com";
        List<UserRole> roles = List.of(UserRole.CUSTOMER);

        String expiredToken = expiredJwtService.generateAccessToken(userId, email, roles, null);

        assertThat(expiredJwtService.isTokenValid(expiredToken)).isFalse();
    }

    @Test
    @DisplayName("Should reject tokens signed with a different secret key")
    void shouldRejectTokenSignedWithDifferentSecret() {
        JwtService otherJwtService = new JwtService(
                "different-secret-key-that-is-also-at-least-256-bits-long-12345678!",
                ISSUER,
                EXPIRATION_MINUTES
        );

        UUID userId = UUID.randomUUID();
        String email = "aman@bookit.com";
        List<UserRole> roles = List.of(UserRole.CUSTOMER);

        // Minted with our secret
        String token = jwtService.generateAccessToken(userId, email, roles, null);

        // Verified by a service configured with a foreign secret -> Rejected!
        assertThat(otherJwtService.isTokenValid(token)).isFalse();
    }

    @Test
    @DisplayName("Should return false for null, blank, or malformed inputs")
    void shouldHandleMalformedInputsGracefully() {
        assertThat(jwtService.isTokenValid(null)).isFalse();
        assertThat(jwtService.isTokenValid("")).isFalse();
        assertThat(jwtService.isTokenValid("   ")).isFalse();
        assertThat(jwtService.isTokenValid("not.a.real.jwt")).isFalse();
    }

    @Test
    @DisplayName("Should throw JwtException when extracting claims from tampered or expired token")
    void shouldThrowExceptionWhenExtractingClaimsFromInvalidToken() {
        UUID userId = UUID.randomUUID();
        String token = jwtService.generateAccessToken(userId, "aman@bookit.com", List.of(UserRole.CUSTOMER), null);
        String tamperedToken = token + "tampered";
        
        // Direct call to extractAllClaims on tampered token must throw SignatureException
        assertThatThrownBy(() -> jwtService.extractAllClaims(tamperedToken))
                .isInstanceOf(io.jsonwebtoken.security.SignatureException.class);

        // Direct call to extractAllClaims on expired token must throw ExpiredJwtException
        JwtService expiredJwtService = new JwtService(TEST_SECRET, ISSUER, -1);
        String expiredToken = expiredJwtService.generateAccessToken(userId, "aman@bookit.com", List.of(UserRole.CUSTOMER), null);

        assertThatThrownBy(() -> expiredJwtService.extractAllClaims(expiredToken))
                .isInstanceOf(io.jsonwebtoken.ExpiredJwtException.class);
    }

    @Test
    @DisplayName("Should fail when configured with a weak secret key under 256 bits")
    void shouldFailWithWeakKey() {
        JwtService weakJwtService = new JwtService("too-short-secret", ISSUER, EXPIRATION_MINUTES);

        assertThatThrownBy(() -> weakJwtService.generateAccessToken(
                UUID.randomUUID(),
                "aman@bookit.com",
                List.of(UserRole.CUSTOMER),
                null
        )).isInstanceOf(io.jsonwebtoken.security.WeakKeyException.class);
    }

    @Test
    @DisplayName("Should handle empty roles list without error")
    void shouldHandleEmptyRolesList() {
        UUID userId = UUID.randomUUID();
        String token = jwtService.generateAccessToken(userId, "aman@bookit.com", List.of(), null);

        assertThat(jwtService.isTokenValid(token)).isTrue();
        assertThat(jwtService.extractRoles(token)).isEmpty();
    }
}
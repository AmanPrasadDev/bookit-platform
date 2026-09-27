package com.bookit.identity.infrastructure.security;

import com.bookit.identity.domain.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for minting, parsing, and cryptographically validating
 * BookIt Platform JSON Web Tokens (JWT) using HMAC-SHA256.
 */
@Service
public class JwtService {

    private final String secret;
    private final String issuer;
    private final long accessTokenExpirationMinutes;

    public JwtService(
            @Value("${security.jwt.secret}") String secret,
            @Value("${security.jwt.issuer:bookit-platform}") String issuer,
            @Value("${security.jwt.access-token-expiration-minutes:30}") long accessTokenExpirationMinutes
    ) {
        this.secret = secret;
        this.issuer = issuer;
        this.accessTokenExpirationMinutes = accessTokenExpirationMinutes;
    }

    /**
     * Derives a cryptographic HMAC-SHA256 key from our configured secret string.
     */
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generates a signed 30-minute Access Token containing user identity and scoped roles.
     */
    public String generateAccessToken(
            UUID userId,
            String email,
            List<UserRole> userRoles,
            UUID cinemaId
    ) {
        List<String> roles = userRoles.stream()
                .map(UserRole::name)
                .toList();

        long now = System.currentTimeMillis();
        long expirationMs = accessTokenExpirationMinutes * 60 * 1000;

        JwtBuilder builder = Jwts.builder()
                .issuer(issuer)
                .subject(userId.toString())
                .claim("email", email)
                .claim("roles", roles)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expirationMs))
                .signWith(getSigningKey(), Jwts.SIG.HS256);

        if (cinemaId != null) {
            builder.claim("cinemaId", cinemaId.toString());
        }

        return builder.compact();
    }

    /**
     * Parses and cryptographically validates the token.
     * Throws JwtException if the signature is invalid or the token has expired.
     */
    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public UUID extractUserId(String token) {
        return UUID.fromString(extractAllClaims(token).getSubject());
    }

    public String extractEmail(String token) {
        return extractAllClaims(token).get("email", String.class);
    }

    @SuppressWarnings("unchecked")
    public List<String> extractRoles(String token) {
        return extractAllClaims(token).get("roles", List.class);
    }

    public Optional<UUID> extractCinemaId(String token) {
        String cinemaIdStr = extractAllClaims(token).get("cinemaId", String.class);
        return cinemaIdStr != null ? Optional.of(UUID.fromString(cinemaIdStr)) : Optional.empty();
    }

    /**
     * Returns true if the token has a valid HMAC signature and has not expired.
     */
    public boolean isTokenValid(String token) {
        try {
            Claims claims = extractAllClaims(token);
            return !claims.getExpiration().before(new Date());
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
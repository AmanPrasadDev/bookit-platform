package com.bookit.identity.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @Test
    @DisplayName("Should authenticate request and populate SecurityContext with user principal, roles, and request details")
    void shouldAuthenticateWhenValidTokenProvided() throws ServletException, IOException {
        // Arrange
        UUID userId = UUID.randomUUID();
        String token = "validToken";
        request.addHeader("Authorization", "Bearer " + token);
        request.setRemoteAddr("192.168.1.100");

        when(jwtService.isTokenValid(token)).thenReturn(true);
        when(jwtService.extractUserId(token)).thenReturn(userId);
        when(jwtService.extractRoles(token)).thenReturn(List.of("CUSTOMER", "CINEMA_STAFF"));

        // Act
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Assert
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isEqualTo(userId.toString());
        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .containsExactlyInAnyOrder("ROLE_CUSTOMER", "ROLE_CINEMA_STAFF");

        // Verify request details (IP address) attached
        assertThat(authentication.getDetails()).isInstanceOf(WebAuthenticationDetails.class);
        WebAuthenticationDetails details = (WebAuthenticationDetails) authentication.getDetails();
        assertThat(details.getRemoteAddress()).isEqualTo("192.168.1.100");

        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Should pass request through without authenticating when Authorization header is missing")
    void withoutHeaderShouldNotAuthenticate() throws ServletException, IOException {
        // Act (no Authorization header added)
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Assert
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(jwtService);
    }

    @Test
    @DisplayName("Should pass request through without authenticating when prefix is not Bearer")
    void wrongPrefixShouldNotAuthenticate() throws ServletException, IOException {
        // Arrange
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");

        // Act
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Assert
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(jwtService);
    }

    @Test
    @DisplayName("Should pass request through without authenticating when token is invalid or expired")
    void invalidOrExpiredTokenShouldNotAuthenticate() throws ServletException, IOException {
        // Arrange
        String token = "invalidOrExpiredToken";
        request.addHeader("Authorization", "Bearer " + token);

        when(jwtService.isTokenValid(token)).thenReturn(false);

        // Act
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Assert
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
        verify(jwtService).isTokenValid(token);
        verify(jwtService, never()).extractUserId(any());
        verify(jwtService, never()).extractRoles(any());
    }

    @Test
    @DisplayName("Should not overwrite pre-existing authentication in SecurityContext")
    void shouldNotOverwritePreExistingAuthentication() throws ServletException, IOException {
        // Arrange: pre-existing admin authentication already in context
        Authentication existingAuth = new UsernamePasswordAuthenticationToken(
                "existing-admin-id",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"))
        );
        SecurityContextHolder.getContext().setAuthentication(existingAuth);

        String token = "validToken";
        request.addHeader("Authorization", "Bearer " + token);

        when(jwtService.isTokenValid(token)).thenReturn(true);
        when(jwtService.extractUserId(token)).thenReturn(UUID.randomUUID());
        when(jwtService.extractRoles(token)).thenReturn(List.of("CUSTOMER"));

        // Act
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Assert: authentication was preserved, not replaced
        Authentication currentAuth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(currentAuth).isSameAs(existingAuth);
        assertThat(currentAuth.getPrincipal()).isEqualTo("existing-admin-id");
        verify(filterChain).doFilter(request, response);
    }
}
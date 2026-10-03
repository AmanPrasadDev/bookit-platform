package com.bookit.identity.infrastructure.security;

import com.bookit.identity.domain.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(SecurityIntegrationTest.TestEndpointController.class)
public class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    // Helper dummy controller inside the test to provide sample endpoints!
    @RestController
    static class TestEndpointController {
        @GetMapping("/api/v1/auth/public-test")
        public String publicAuthEndpoint() {
            return "public-auth-ok";
        }

        @GetMapping("/api/v1/test/protected")
        public String protectedEndpoint() {
            return "protected-ok";
        }

        @GetMapping("/api/v1/test/staff-only")
        @PreAuthorize("hasRole('CINEMA_STAFF')")
        public String staffOnlyEndpoint() {
            return "staff-only-ok";
        }
    }

    @Test
    @DisplayName("Should allow access to actuator health without authentication")
    void shouldAllowActuatorHealthWithoutToken() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should allow access to /api/v1/auth/** endpoints without authentication")
    void shouldAllowPublicAuthEndpointsWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/auth/public-test"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should reject access to protected endpoint when no token is provided")
    void shouldRejectProtectedEndpointWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/test/protected"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should allow access to protected endpoint when valid Bearer token is provided")
    void shouldAllowProtectedEndpointWithValidToken() throws Exception {
        String token = jwtService.generateAccessToken(
                UUID.randomUUID(),
                "aman@bookit.com",
                List.of(UserRole.CINEMA_STAFF),
                UUID.randomUUID()
        );

        mockMvc.perform(get("/api/v1/test/protected")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should reject staff endpoint when user has only CUSTOMER role")
    void shouldRejectStaffEndpointWhenUserHasOnlyCustomerRole() throws Exception {
        String token = jwtService.generateAccessToken(
                UUID.randomUUID(),
                "aman@bookit.com",
                List.of(UserRole.CUSTOMER),
                UUID.randomUUID()
        );

        mockMvc.perform(get("/api/v1/test/staff-only")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should allow staff endpoint when user has CINEMA_STAFF role")
    void shouldAllowStaffEndpointWhenUserHasStaffRole() throws Exception {
        String token = jwtService.generateAccessToken(
                UUID.randomUUID(),
                "staff@bookit.com",
                List.of(UserRole.CINEMA_STAFF),
                UUID.randomUUID()
        );

        mockMvc.perform(get("/api/v1/test/staff-only")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should reject protected endpoint when invalid or tampered token is provided")
    void shouldRejectProtectedEndpointWithInvalidToken() throws Exception {
        mockMvc.perform(get("/api/v1/test/protected")
                        .header("Authorization", "Bearer invalid.malformed.token"))
                .andExpect(status().isForbidden());
    }
}
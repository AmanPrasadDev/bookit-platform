package com.bookit.identity.infrastructure.repository;

import com.bookit.identity.domain.SecurityAuditEvent;
import com.bookit.identity.domain.User;
import com.bookit.identity.domain.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
class SecurityAuditEventRepositoryIntegrationTest {

    @Autowired
    private SecurityAuditEventRepository securityAuditEventRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("should retrieve paginated audit events for a user with correct page metadata")
    void shouldRetrievePaginatedAuditEventsForUser() {
        // Given: a user with 15 audit events
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("audit@bookit.com")
                .fullName("Audit Test User")
                .status(UserStatus.ACTIVE)
                .build();
        userRepository.save(user);

        List<SecurityAuditEvent> events = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            events.add(SecurityAuditEvent.builder()
                    .id(UUID.randomUUID())
                    .user(user)
                    .eventType("LOGIN_SUCCESS")
                    .ipAddress("192.168.1." + i)
                    .userAgent("Mozilla/5.0")
                    .metadata("{\"attempt\": " + i + "}") // Valid JSON for JSONB column
                    .build());
        }
        securityAuditEventRepository.saveAll(events);

        // When: requesting page 0 with page size 10
        Page<SecurityAuditEvent> page = securityAuditEventRepository.findAllByUserIdOrderByCreatedAtDesc(
                user.getId(), PageRequest.of(0, 10)
        );

        // Then: 10 items returned on this page, 15 total items, 2 total pages
        assertThat(page.getContent()).hasSize(10);
        assertThat(page.getTotalElements()).isEqualTo(15);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.hasNext()).isTrue();
    }

    @Test
    @DisplayName("should filter paginated audit events by eventType")
    void shouldFilterPaginatedAuditEventsByEventType() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("audit2@bookit.com")
                .fullName("Audit User 2")
                .status(UserStatus.ACTIVE)
                .build();
        userRepository.save(user);

        // 3 failed logins and 1 success
        for (int i = 0; i < 3; i++) {
            securityAuditEventRepository.save(SecurityAuditEvent.builder()
                    .id(UUID.randomUUID())
                    .user(user)
                    .eventType("LOGIN_FAILURE")
                    .metadata("{}")
                    .build());
        }
        securityAuditEventRepository.save(SecurityAuditEvent.builder()
                .id(UUID.randomUUID())
                .user(user)
                .eventType("LOGIN_SUCCESS")
                .metadata("{}")
                .build());

        // When: querying only LOGIN_FAILURE events
        Page<SecurityAuditEvent> failurePage = securityAuditEventRepository.findAllByEventTypeOrderByCreatedAtDesc(
                "LOGIN_FAILURE", PageRequest.of(0, 10)
        );

        // Then: exactly 3 failure events returned
        assertThat(failurePage.getContent()).hasSize(3);
        assertThat(failurePage.getTotalElements()).isEqualTo(3);
    }
}
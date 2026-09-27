package com.bookit.identity.infrastructure.repository;

import com.bookit.identity.domain.RoleAssignment;
import com.bookit.identity.domain.User;
import com.bookit.identity.domain.UserRole;
import com.bookit.identity.domain.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
class RoleAssignmentRepositoryIntegrationTest {

    @Autowired
    private RoleAssignmentRepository roleAssignmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("should find all staff assigned to a specific cinema venue")
    void shouldFindStaffAssignedToCinema() {
        // Given: a user assigned to a cinema
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("staff@pvr.com")
                .fullName("PVR Staff Member")
                .status(UserStatus.ACTIVE)
                .build();
        userRepository.save(user);

        UUID cinemaId = UUID.randomUUID();
        RoleAssignment assignment = RoleAssignment.builder()
                .id(UUID.randomUUID())
                .user(user)
                .role(UserRole.CINEMA_STAFF)
                .cinemaId(cinemaId)
                .build();
        roleAssignmentRepository.save(assignment);

        // When: queried by cinemaId
        List<RoleAssignment> staffList = roleAssignmentRepository.findAllByCinemaId(cinemaId);

        // Then: exactly 1 assignment returned for that cinema
        assertThat(staffList).hasSize(1);
        assertThat(staffList.getFirst().getCinemaId()).isEqualTo(cinemaId);
        assertThat(staffList.getFirst().getRole()).isEqualTo(UserRole.CINEMA_STAFF);
    }

    @Test
    @DisplayName("should return true when user holds the specified role")
    void shouldReturnTrueWhenUserHoldsRole() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("staff2@pvr.com")
                .fullName("Staff 2")
                .status(UserStatus.ACTIVE)
                .build();
        userRepository.save(user);

        RoleAssignment assignment = RoleAssignment.builder()
                .id(UUID.randomUUID())
                .user(user)
                .role(UserRole.CINEMA_STAFF)
                .cinemaId(UUID.randomUUID())
                .build();
        roleAssignmentRepository.save(assignment);

        boolean exists = roleAssignmentRepository.existsByUserIdAndRole(user.getId(), UserRole.CINEMA_STAFF);

        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("should return false when user does not hold the specified role")
    void shouldReturnFalseWhenUserDoesNotHoldRole() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("customer@gmail.com")
                .fullName("Customer Only")
                .status(UserStatus.ACTIVE)
                .build();
        userRepository.save(user);

        RoleAssignment assignment = RoleAssignment.builder()
                .id(UUID.randomUUID())
                .user(user)
                .role(UserRole.CUSTOMER)
                .build();
        roleAssignmentRepository.save(assignment);

        // When: checking for PLATFORM_ADMIN (which was never assigned)
        boolean hasAdminRole = roleAssignmentRepository.existsByUserIdAndRole(user.getId(), UserRole.PLATFORM_ADMIN);

        assertThat(hasAdminRole).isFalse();
    }

    @Test
    @DisplayName("should find all roles assigned to a user")
    void shouldFindAllRolesByUserId() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("multi@bookit.com")
                .fullName("Multi Role User")
                .status(UserStatus.ACTIVE)
                .build();
        userRepository.save(user);

        // User is both a CUSTOMER and a CINEMA_STAFF
        RoleAssignment role1 = RoleAssignment.builder()
                .id(UUID.randomUUID())
                .user(user)
                .role(UserRole.CUSTOMER)
                .build();
        RoleAssignment role2 = RoleAssignment.builder()
                .id(UUID.randomUUID())
                .user(user)
                .role(UserRole.CINEMA_STAFF)
                .cinemaId(UUID.randomUUID())
                .build();
        roleAssignmentRepository.save(role1);
        roleAssignmentRepository.save(role2);

        List<RoleAssignment> userRoles = roleAssignmentRepository.findAllByUserId(user.getId());

        assertThat(userRoles).hasSize(2);
    }

    @Test
    @DisplayName("should find all admins assigned to an organization")
    void shouldFindAdminsByOrganizationId() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("orgadmin@pvr.com")
                .fullName("PVR Chain Admin")
                .status(UserStatus.ACTIVE)
                .build();
        userRepository.save(user);

        UUID orgId = UUID.randomUUID();
        RoleAssignment orgAdmin = RoleAssignment.builder()
                .id(UUID.randomUUID())
                .user(user)
                .role(UserRole.ORGANIZATION_ADMIN)
                .organizationId(orgId)
                .build();
        roleAssignmentRepository.save(orgAdmin);

        List<RoleAssignment> orgAdmins = roleAssignmentRepository.findAllByOrganizationId(orgId);

        assertThat(orgAdmins).hasSize(1);
        assertThat(orgAdmins.get(0).getOrganizationId()).isEqualTo(orgId);
        assertThat(orgAdmins.get(0).getRole()).isEqualTo(UserRole.ORGANIZATION_ADMIN);
    }
}
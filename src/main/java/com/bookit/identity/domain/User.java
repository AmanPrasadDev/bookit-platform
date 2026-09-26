package com.bookit.identity.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents an internal BookIt platform user.
 * Maps to the 'app_user' table created in V2__create_identity_schema.sql.
 */
@Entity
@Table(name = "app_user")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "email", unique = true)
    private String email;

    @Column(name = "phone_number", unique = true)
    private String phoneNumber;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}


/*
@Enumerated(EnumType.STRING): Crucial! By default, JPA stores enums as numbers (0, 1, 2). If you ever reorder your enum, numbers get corrupted! EnumType.STRING forces Hibernate to save the exact readable text ('ACTIVE').
@CreationTimestamp / @UpdateTimestamp: Hibernate automatically populates these timestamps whenever a record is created or updated—no manual setting needed.
@Builder & @Builder.Default: Gives you the clean Builder pattern (User.builder().email(...).build()).
 */
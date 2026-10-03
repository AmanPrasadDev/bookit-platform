package com.bookit.catalogue.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "seat_section")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeatSection {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "seat_map_version_id", nullable = false)
    private UUID seatMapVersionId;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "category_code", nullable = false, length = 20)
    private String categoryCode;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private int displayOrder = 1;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
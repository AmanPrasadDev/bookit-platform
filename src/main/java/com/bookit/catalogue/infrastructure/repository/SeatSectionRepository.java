package com.bookit.catalogue.infrastructure.repository;

import com.bookit.catalogue.domain.SeatSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SeatSectionRepository extends JpaRepository<SeatSection, UUID> {

    // Returns sections (e.g. Recliner, Prime, Classic) in visual display order
    List<SeatSection> findAllBySeatMapVersionIdOrderByDisplayOrderAsc(UUID seatMapVersionId);
}
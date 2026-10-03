package com.bookit.catalogue.infrastructure.repository;

import com.bookit.catalogue.domain.SeatMapStatus;
import com.bookit.catalogue.domain.SeatMapVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SeatMapVersionRepository extends JpaRepository<SeatMapVersion, UUID> {

    Optional<SeatMapVersion> findByScreenIdAndVersionNumber(UUID screenId, int versionNumber);

    // Finds the current published layout for a screen (used when scheduling shows!)
    Optional<SeatMapVersion> findFirstByScreenIdAndStatusOrderByVersionNumberDesc(UUID screenId, SeatMapStatus status);
}
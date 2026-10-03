package com.bookit.catalogue.infrastructure.repository;

import com.bookit.catalogue.domain.PhysicalSeat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PhysicalSeatRepository extends JpaRepository<PhysicalSeat, UUID> {

    // Returns all chairs in a layout sorted by Row (A, B, C...) then Seat Number (1, 2, 3...)
    List<PhysicalSeat> findAllBySeatMapVersionIdOrderByRowLabelAscSeatNumberAsc(UUID seatMapVersionId);

    long countBySeatMapVersionId(UUID seatMapVersionId);
}
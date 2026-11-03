package com.bookit.hold.infrastructure.repository;

import com.bookit.hold.domain.SeatHold;
import com.bookit.hold.domain.SeatHoldStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SeatHoldRepository extends JpaRepository<SeatHold, UUID> {

    Optional<SeatHold> findByCustomerIdAndStatus(UUID customerId, SeatHoldStatus status);

    Optional<SeatHold> findByIdAndCustomerId(UUID id, UUID customerId);

    List<SeatHold> findAllByShowIdAndStatus(UUID showId, SeatHoldStatus status);

    List<SeatHold> findAllByStatusAndExpiresAtBefore(SeatHoldStatus status, Instant cutoff);
}

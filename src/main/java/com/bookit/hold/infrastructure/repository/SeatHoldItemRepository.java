package com.bookit.hold.infrastructure.repository;

import com.bookit.hold.domain.SeatHoldItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface SeatHoldItemRepository extends JpaRepository<SeatHoldItem, UUID> {

    List<SeatHoldItem> findAllByHoldId(UUID holdId);

    List<SeatHoldItem> findAllByHoldIdIn(Collection<UUID> holdIds);

    void deleteAllByHoldId(UUID holdId);
}

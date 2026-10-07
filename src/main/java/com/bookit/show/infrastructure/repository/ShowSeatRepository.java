package com.bookit.show.infrastructure.repository;

import com.bookit.show.domain.ShowSeat;
import com.bookit.show.domain.ShowSeatState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShowSeatRepository extends JpaRepository<ShowSeat, UUID> {

    List<ShowSeat> findByShowId(UUID showId);

    List<ShowSeat> findByShowIdAndState(UUID showId, ShowSeatState state);

    Optional<ShowSeat> findByShowIdAndPhysicalSeatId(UUID showId, UUID physicalSeatId);

    List<ShowSeat> findByShowIdAndPhysicalSeatIdIn(UUID showId, Collection<UUID> physicalSeatIds);

    long countByShowIdAndState(UUID showId, ShowSeatState state);

    long countByShowId(UUID showId);
}

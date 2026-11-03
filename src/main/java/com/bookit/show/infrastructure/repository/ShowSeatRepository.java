package com.bookit.show.infrastructure.repository;

import com.bookit.show.domain.ShowSeat;
import com.bookit.show.domain.ShowSeatState;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /**
     * Acquires pessimistic row-level write locks (SELECT ... FOR UPDATE) on the specified seats.
     * Crucially orders the locked rows by id ASC to guarantee deadlock-free execution under concurrent contention.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ShowSeat s WHERE s.id IN :seatIds ORDER BY s.id ASC")
    List<ShowSeat> findAllByIdInWithPessimisticLock(@Param("seatIds") Collection<UUID> seatIds);
}

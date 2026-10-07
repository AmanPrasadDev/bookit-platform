package com.bookit.show.infrastructure.repository;

import com.bookit.show.domain.Show;
import com.bookit.show.domain.ShowStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface ShowRepository extends JpaRepository<Show, UUID> {

    List<Show> findByScreenId(UUID screenId);

    List<Show> findByMovieId(UUID movieId);

    List<Show> findByScreenIdAndStatus(UUID screenId, ShowStatus status);

    List<Show> findByMovieIdAndStatus(UUID movieId, ShowStatus status);

    List<Show> findByScreenIdAndStartsAtBetween(UUID screenId, Instant start, Instant end);
}

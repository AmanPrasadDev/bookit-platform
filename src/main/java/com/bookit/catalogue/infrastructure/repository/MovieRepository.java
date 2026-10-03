package com.bookit.catalogue.infrastructure.repository;

import com.bookit.catalogue.domain.Movie;
import com.bookit.catalogue.domain.MovieStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MovieRepository extends JpaRepository<Movie, UUID> {

    List<Movie> findAllByStatus(MovieStatus status);

    Optional<Movie> findByTitleIgnoreCase(String title);
}
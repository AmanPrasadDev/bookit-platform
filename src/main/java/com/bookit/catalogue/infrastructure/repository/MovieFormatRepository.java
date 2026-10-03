package com.bookit.catalogue.infrastructure.repository;

import com.bookit.catalogue.domain.MovieFormat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MovieFormatRepository extends JpaRepository<MovieFormat, UUID> {

    Optional<MovieFormat> findByCode(String code);

    List<MovieFormat> findAllByStatus(String status);
}
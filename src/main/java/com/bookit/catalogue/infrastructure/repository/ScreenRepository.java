package com.bookit.catalogue.infrastructure.repository;

import com.bookit.catalogue.domain.Screen;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ScreenRepository extends JpaRepository<Screen, UUID> {

    List<Screen> findAllByCinemaId(UUID cinemaId);

    Optional<Screen> findByCinemaIdAndName(UUID cinemaId, String name);
}
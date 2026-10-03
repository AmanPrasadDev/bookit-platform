package com.bookit.catalogue.infrastructure.repository;

import com.bookit.catalogue.domain.Cinema;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CinemaRepository extends JpaRepository<Cinema, UUID> {

    List<Cinema> findAllByCityIdAndStatus(UUID cityId, String status);

    List<Cinema> findAllByOrganizationId(UUID organizationId);

    Optional<Cinema> findByOrganizationIdAndName(UUID organizationId, String name);
}
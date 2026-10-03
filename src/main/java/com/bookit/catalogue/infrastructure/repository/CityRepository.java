package com.bookit.catalogue.infrastructure.repository;

import com.bookit.catalogue.domain.City;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CityRepository extends JpaRepository<City, UUID> {

    Optional<City> findByNameIgnoreCase(String name);

    List<City> findAllByStatusOrderByNameAsc(String status);
}
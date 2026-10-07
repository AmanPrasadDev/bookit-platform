package com.bookit.show.infrastructure.repository;

import com.bookit.show.domain.PriceConfigurationStatus;
import com.bookit.show.domain.ShowPriceConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShowPriceConfigurationRepository extends JpaRepository<ShowPriceConfiguration, UUID> {

    Optional<ShowPriceConfiguration> findByShowIdAndStatus(UUID showId, PriceConfigurationStatus status);

    List<ShowPriceConfiguration> findByShowId(UUID showId);
}

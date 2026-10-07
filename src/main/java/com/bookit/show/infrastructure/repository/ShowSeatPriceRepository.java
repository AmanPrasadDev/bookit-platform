package com.bookit.show.infrastructure.repository;

import com.bookit.show.domain.ShowSeatPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShowSeatPriceRepository extends JpaRepository<ShowSeatPrice, UUID> {

    List<ShowSeatPrice> findByPriceConfigurationId(UUID priceConfigurationId);

    Optional<ShowSeatPrice> findByPriceConfigurationIdAndSectionCategoryCode(
            UUID priceConfigurationId, String sectionCategoryCode);
}

package com.bookit.show.infrastructure.repository;

import com.bookit.show.domain.ShowChargeRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ShowChargeRuleRepository extends JpaRepository<ShowChargeRule, UUID> {

    List<ShowChargeRule> findByPriceConfigurationId(UUID priceConfigurationId);
}

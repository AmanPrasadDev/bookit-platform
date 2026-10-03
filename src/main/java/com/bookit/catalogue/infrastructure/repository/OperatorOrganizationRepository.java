package com.bookit.catalogue.infrastructure.repository;

import com.bookit.catalogue.domain.OperatorOrganization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OperatorOrganizationRepository extends JpaRepository<OperatorOrganization, UUID> {

    Optional<OperatorOrganization> findByLegalName(String legalName);
}
package com.bookit.catalogue.application.service;

import com.bookit.catalogue.application.dto.PhysicalSeatResponse;
import com.bookit.catalogue.application.dto.SeatMapResponse;
import com.bookit.catalogue.application.dto.SeatSectionResponse;
import com.bookit.catalogue.domain.PhysicalSeat;
import com.bookit.catalogue.domain.SeatMapVersion;
import com.bookit.catalogue.domain.SeatSection;
import com.bookit.catalogue.infrastructure.repository.PhysicalSeatRepository;
import com.bookit.catalogue.infrastructure.repository.SeatSectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Assembler component responsible for converting relational SeatMapVersion entities
 * and their physical seats/sections into structured SeatMapResponse DTOs.
 */
@Component
@RequiredArgsConstructor
public class SeatMapAssembler {

    private final SeatSectionRepository seatSectionRepository;
    private final PhysicalSeatRepository physicalSeatRepository;

    /**
     * Assembles a hierarchical SeatMapResponse from a SeatMapVersion entity.
     * Fetches sections in display order and groups physical seats by section ID.
     */
    public SeatMapResponse toResponse(SeatMapVersion version) {
        List<SeatSection> sections = seatSectionRepository
                .findAllBySeatMapVersionIdOrderByDisplayOrderAsc(version.getId());

        List<PhysicalSeat> allSeats = physicalSeatRepository
                .findAllBySeatMapVersionIdOrderByRowLabelAscSeatNumberAsc(version.getId());

        Map<UUID, List<PhysicalSeatResponse>> seatsBySection = allSeats.stream()
                .collect(Collectors.groupingBy(
                        PhysicalSeat::getSectionId,
                        Collectors.mapping(PhysicalSeatResponse::fromDomain, Collectors.toList())
                ));

        List<SeatSectionResponse> sectionResponses = sections.stream()
                .map(sec -> SeatSectionResponse.fromDomain(
                        sec,
                        seatsBySection.getOrDefault(sec.getId(), Collections.emptyList())
                ))
                .toList();

        return SeatMapResponse.fromDomain(version, sectionResponses);
    }
}

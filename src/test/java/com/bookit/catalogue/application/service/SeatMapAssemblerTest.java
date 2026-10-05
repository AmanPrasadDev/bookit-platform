package com.bookit.catalogue.application.service;

import com.bookit.catalogue.application.dto.SeatMapResponse;
import com.bookit.catalogue.domain.PhysicalSeat;
import com.bookit.catalogue.domain.SeatMapStatus;
import com.bookit.catalogue.domain.SeatMapVersion;
import com.bookit.catalogue.domain.SeatSection;
import com.bookit.catalogue.domain.SeatType;
import com.bookit.catalogue.infrastructure.repository.PhysicalSeatRepository;
import com.bookit.catalogue.infrastructure.repository.SeatSectionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for SeatMapAssembler.
 *
 * Verifies that relational SeatSection and PhysicalSeat entities are correctly
 * aggregated and grouped into a hierarchical SeatMapResponse DTO.
 */
@ExtendWith(MockitoExtension.class)
class SeatMapAssemblerTest {

    @Mock
    private SeatSectionRepository seatSectionRepository;

    @Mock
    private PhysicalSeatRepository physicalSeatRepository;

    @InjectMocks
    private SeatMapAssembler seatMapAssembler;

    /**
     * PURPOSE: Verify that physical seats belonging to different sections are
     * correctly grouped into their respective parent SeatSectionResponse.
     */
    @Test
    @DisplayName("toResponse should correctly group physical seats by section ID")
    void toResponse_whenSectionsAndSeatsExist_shouldGroupSeatsBySection() {
        UUID versionId = UUID.randomUUID();
        UUID screenId = UUID.randomUUID();
        UUID primeSectionId = UUID.randomUUID();
        UUID reclinerSectionId = UUID.randomUUID();

        SeatMapVersion version = SeatMapVersion.builder()
                .id(versionId)
                .screenId(screenId)
                .versionNumber(1)
                .status(SeatMapStatus.PUBLISHED)
                .totalSeats(3)
                .publishedAt(Instant.now())
                .build();

        SeatSection prime = SeatSection.builder()
                .id(primeSectionId)
                .seatMapVersionId(versionId)
                .name("PRIME")
                .categoryCode("PRIME")
                .displayOrder(1)
                .build();

        SeatSection recliner = SeatSection.builder()
                .id(reclinerSectionId)
                .seatMapVersionId(versionId)
                .name("RECLINER")
                .categoryCode("RECLINER")
                .displayOrder(2)
                .build();

        PhysicalSeat seatA1 = PhysicalSeat.builder()
                .id(UUID.randomUUID())
                .seatMapVersionId(versionId)
                .sectionId(primeSectionId)
                .rowLabel("A")
                .seatNumber(1)
                .seatType(SeatType.REGULAR)
                .build();

        PhysicalSeat seatA2 = PhysicalSeat.builder()
                .id(UUID.randomUUID())
                .seatMapVersionId(versionId)
                .sectionId(primeSectionId)
                .rowLabel("A")
                .seatNumber(2)
                .seatType(SeatType.REGULAR)
                .build();

        PhysicalSeat seatR1 = PhysicalSeat.builder()
                .id(UUID.randomUUID())
                .seatMapVersionId(versionId)
                .sectionId(reclinerSectionId)
                .rowLabel("R")
                .seatNumber(1)
                .seatType(SeatType.RECLINER)
                .build();

        when(seatSectionRepository.findAllBySeatMapVersionIdOrderByDisplayOrderAsc(versionId))
                .thenReturn(List.of(prime, recliner));
        when(physicalSeatRepository.findAllBySeatMapVersionIdOrderByRowLabelAscSeatNumberAsc(versionId))
                .thenReturn(List.of(seatA1, seatA2, seatR1));

        SeatMapResponse response = seatMapAssembler.toResponse(version);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(versionId);
        assertThat(response.sections()).hasSize(2);

        // Prime section assertions
        assertThat(response.sections().get(0).name()).isEqualTo("PRIME");
        assertThat(response.sections().get(0).seats()).hasSize(2);
        assertThat(response.sections().get(0).seats().get(0).seatNumber()).isEqualTo(1);
        assertThat(response.sections().get(0).seats().get(1).seatNumber()).isEqualTo(2);

        // Recliner section assertions
        assertThat(response.sections().get(1).name()).isEqualTo("RECLINER");
        assertThat(response.sections().get(1).seats()).hasSize(1);
        assertThat(response.sections().get(1).seats().getFirst().seatType()).isEqualTo(SeatType.RECLINER);

        verify(seatSectionRepository).findAllBySeatMapVersionIdOrderByDisplayOrderAsc(versionId);
        verify(physicalSeatRepository).findAllBySeatMapVersionIdOrderByRowLabelAscSeatNumberAsc(versionId);
    }

    /**
     * PURPOSE: Verify that when a section exists without any physical seats,
     * the assembler safely assigns an empty list without throwing NullPointerException.
     */
    @Test
    @DisplayName("toResponse should return empty seat list for section when no seats exist")
    void toResponse_whenSectionHasNoSeats_shouldReturnEmptySeatList() {
        UUID versionId = UUID.randomUUID();
        SeatMapVersion version = SeatMapVersion.builder()
                .id(versionId)
                .screenId(UUID.randomUUID())
                .versionNumber(1)
                .status(SeatMapStatus.DRAFT)
                .totalSeats(0)
                .build();

        SeatSection emptySection = SeatSection.builder()
                .id(UUID.randomUUID())
                .seatMapVersionId(versionId)
                .name("BALCONY")
                .categoryCode("BALCONY")
                .displayOrder(1)
                .build();

        when(seatSectionRepository.findAllBySeatMapVersionIdOrderByDisplayOrderAsc(versionId))
                .thenReturn(List.of(emptySection));
        when(physicalSeatRepository.findAllBySeatMapVersionIdOrderByRowLabelAscSeatNumberAsc(versionId))
                .thenReturn(Collections.emptyList());

        SeatMapResponse response = seatMapAssembler.toResponse(version);

        assertThat(response.sections()).hasSize(1);
        assertThat(response.sections().getFirst().seats()).isEmpty();
    }

    /**
     * PURPOSE: Verify that when a version has no sections at all,
     * the assembler returns an empty sections list.
     */
    @Test
    @DisplayName("toResponse should return empty sections list when version has no sections")
    void toResponse_whenNoSectionsExist_shouldReturnEmptySectionsList() {
        UUID versionId = UUID.randomUUID();
        SeatMapVersion version = SeatMapVersion.builder()
                .id(versionId)
                .screenId(UUID.randomUUID())
                .versionNumber(1)
                .status(SeatMapStatus.DRAFT)
                .totalSeats(0)
                .build();

        when(seatSectionRepository.findAllBySeatMapVersionIdOrderByDisplayOrderAsc(versionId))
                .thenReturn(Collections.emptyList());
        when(physicalSeatRepository.findAllBySeatMapVersionIdOrderByRowLabelAscSeatNumberAsc(versionId))
                .thenReturn(Collections.emptyList());

        SeatMapResponse response = seatMapAssembler.toResponse(version);

        assertThat(response.sections()).isEmpty();
    }
}

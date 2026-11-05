package com.bookit.hold.application.service;

import com.bookit.catalogue.domain.exception.ResourceNotFoundException;
import com.bookit.common.lock.DistributedLockService;
import com.bookit.hold.application.dto.CreateSeatHoldRequest;
import com.bookit.hold.application.dto.SeatHoldResponse;
import com.bookit.hold.domain.SeatHold;
import com.bookit.hold.domain.SeatHoldItem;
import com.bookit.hold.domain.SeatHoldStatus;
import com.bookit.hold.domain.exception.SeatUnavailableException;
import com.bookit.hold.infrastructure.repository.SeatHoldItemRepository;
import com.bookit.hold.infrastructure.repository.SeatHoldRepository;
import com.bookit.show.domain.Show;
import com.bookit.show.domain.ShowSeat;
import com.bookit.show.domain.ShowSeatState;
import com.bookit.show.domain.ShowStatus;
import com.bookit.show.infrastructure.repository.ShowRepository;
import com.bookit.show.infrastructure.repository.ShowSeatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SeatHoldServiceTest {

    @Mock
    private SeatHoldRepository seatHoldRepository;

    @Mock
    private SeatHoldItemRepository seatHoldItemRepository;

    @Mock
    private ShowRepository showRepository;

    @Mock
    private ShowSeatRepository showSeatRepository;

    @Mock
    private DistributedLockService distributedLockService;

    private Clock clock;
    private SeatHoldService seatHoldService;

    private final Instant fixedNow = Instant.parse("2026-11-05T12:00:00Z");

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(fixedNow, ZoneId.of("UTC"));
        seatHoldService = new SeatHoldService(
                seatHoldRepository,
                seatHoldItemRepository,
                showRepository,
                showSeatRepository,
                distributedLockService,
                clock
        );
    }

    // =========================================================================
    // 1. holdSeats Tests
    // =========================================================================

    @Nested
    @DisplayName("holdSeats")
    class HoldSeatsTests {

        @Test
        @DisplayName("Should successfully hold seats when all invariants and locks are satisfied")
        void holdSeats_whenValid_shouldSucceed() {
            UUID customerId = UUID.randomUUID();
            UUID showId = UUID.randomUUID();
            UUID seatId1 = UUID.randomUUID();
            UUID seatId2 = UUID.randomUUID();

            CreateSeatHoldRequest request = new CreateSeatHoldRequest(showId, List.of(seatId1, seatId2));
            Show show = buildSampleShow(showId, ShowStatus.PUBLISHED, fixedNow.plus(1, ChronoUnit.DAYS));
            ShowSeat seat1 = buildSampleShowSeat(seatId1, showId, "A", 1, ShowSeatState.AVAILABLE);
            ShowSeat seat2 = buildSampleShowSeat(seatId2, showId, "A", 2, ShowSeatState.AVAILABLE);

            when(showRepository.findById(showId)).thenReturn(Optional.of(show));
            when(seatHoldRepository.findByCustomerIdAndStatus(customerId, SeatHoldStatus.HELD)).thenReturn(Optional.empty());
            when(distributedLockService.acquireAllLocks(anyList(), anyString(), any(Duration.class))).thenReturn(true);
            when(showSeatRepository.findAllByIdInWithPessimisticLock(anyList())).thenReturn(List.of(seat1, seat2));
            when(seatHoldRepository.save(any(SeatHold.class))).thenAnswer(invocation -> invocation.getArgument(0));

            SeatHoldResponse response = seatHoldService.holdSeats(request, customerId);

            assertThat(response).isNotNull();
            assertThat(response.status()).isEqualTo(SeatHoldStatus.HELD);
            assertThat(response.customerId()).isEqualTo(customerId);
            assertThat(response.showId()).isEqualTo(showId);
            assertThat(response.seatIds()).containsExactlyInAnyOrder(seatId1, seatId2);
            assertThat(response.expiresAt()).isEqualTo(fixedNow.plus(10, ChronoUnit.MINUTES));

            assertThat(seat1.getState()).isEqualTo(ShowSeatState.HELD);
            assertThat(seat2.getState()).isEqualTo(ShowSeatState.HELD);
            assertThat(seat1.getActiveHoldId()).isEqualTo(response.id());
            assertThat(seat2.getActiveHoldId()).isEqualTo(response.id());

            verify(seatHoldRepository).save(any(SeatHold.class));
            verify(showSeatRepository).saveAllAndFlush(List.of(seat1, seat2));
            verify(seatHoldItemRepository).saveAll(anyList());
            verify(distributedLockService).acquireAllLocks(anyList(), anyString(), eq(Duration.ofMinutes(10)));
            verify(distributedLockService, never()).releaseAllLocks(anyList(), anyString());
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when show does not exist")
        void holdSeats_whenShowNotFound_shouldThrow() {
            UUID showId = UUID.randomUUID();
            CreateSeatHoldRequest request = new CreateSeatHoldRequest(showId, List.of(UUID.randomUUID()));

            when(showRepository.findById(showId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> seatHoldService.holdSeats(request, UUID.randomUUID()))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Show not found with id: " + showId);

            verifyNoInteractions(distributedLockService);
            verifyNoInteractions(showSeatRepository);
        }

        @Test
        @DisplayName("Should throw IllegalStateException when show is in DRAFT status")
        void holdSeats_whenShowNotPublished_shouldThrow() {
            UUID showId = UUID.randomUUID();
            CreateSeatHoldRequest request = new CreateSeatHoldRequest(showId, List.of(UUID.randomUUID()));
            Show draftShow = buildSampleShow(showId, ShowStatus.DRAFT, fixedNow.plus(1, ChronoUnit.DAYS));

            when(showRepository.findById(showId)).thenReturn(Optional.of(draftShow));

            assertThatThrownBy(() -> seatHoldService.holdSeats(request, UUID.randomUUID()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot hold seats for a show in status: DRAFT");

            verifyNoInteractions(distributedLockService);
            verifyNoInteractions(showSeatRepository);
        }

        @Test
        @DisplayName("Should throw IllegalStateException when booking window has closed")
        void holdSeats_whenBookingClosed_shouldThrow() {
            UUID showId = UUID.randomUUID();
            CreateSeatHoldRequest request = new CreateSeatHoldRequest(showId, List.of(UUID.randomUUID()));
            Show closedShow = buildSampleShow(showId, ShowStatus.PUBLISHED, fixedNow.minusSeconds(1));

            when(showRepository.findById(showId)).thenReturn(Optional.of(closedShow));

            assertThatThrownBy(() -> seatHoldService.holdSeats(request, UUID.randomUUID()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Booking has closed for this show");

            verifyNoInteractions(distributedLockService);
            verifyNoInteractions(showSeatRepository);
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when seat list exceeds 10 seats")
        void holdSeats_whenExceedsMaxSeats_shouldThrow() {
            UUID showId = UUID.randomUUID();
            List<UUID> elevenSeats = IntStream.range(0, 11).mapToObj(i -> UUID.randomUUID()).toList();
            CreateSeatHoldRequest request = new CreateSeatHoldRequest(showId, elevenSeats);
            Show show = buildSampleShow(showId, ShowStatus.PUBLISHED, fixedNow.plus(1, ChronoUnit.DAYS));

            when(showRepository.findById(showId)).thenReturn(Optional.of(show));

            assertThatThrownBy(() -> seatHoldService.holdSeats(request, UUID.randomUUID()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Number of seats to hold must be between 1 and 10");

            verifyNoInteractions(distributedLockService);
            verifyNoInteractions(showSeatRepository);
        }

        @Test
        @DisplayName("Should throw IllegalStateException when customer already has an active unexpired hold")
        void holdSeats_whenCustomerAlreadyHasActiveHold_shouldThrow() {
            UUID customerId = UUID.randomUUID();
            UUID showId = UUID.randomUUID();
            CreateSeatHoldRequest request = new CreateSeatHoldRequest(showId, List.of(UUID.randomUUID()));
            Show show = buildSampleShow(showId, ShowStatus.PUBLISHED, fixedNow.plus(1, ChronoUnit.DAYS));

            SeatHold activeHold = SeatHold.builder()
                    .id(UUID.randomUUID())
                    .customerId(customerId)
                    .showId(showId)
                    .status(SeatHoldStatus.HELD)
                    .expiresAt(fixedNow.plus(5, ChronoUnit.MINUTES))
                    .build();

            when(showRepository.findById(showId)).thenReturn(Optional.of(show));
            when(seatHoldRepository.findByCustomerIdAndStatus(customerId, SeatHoldStatus.HELD)).thenReturn(Optional.of(activeHold));

            assertThatThrownBy(() -> seatHoldService.holdSeats(request, customerId))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Customer already has an active hold in progress");

            verifyNoInteractions(distributedLockService);
            verifyNoInteractions(showSeatRepository);
        }

        @Test
        @DisplayName("Should allow new hold when customer's previous hold has already expired")
        void holdSeats_whenCustomerPreviousHoldExpired_shouldSucceed() {
            UUID customerId = UUID.randomUUID();
            UUID showId = UUID.randomUUID();
            UUID seatId = UUID.randomUUID();
            CreateSeatHoldRequest request = new CreateSeatHoldRequest(showId, List.of(seatId));
            Show show = buildSampleShow(showId, ShowStatus.PUBLISHED, fixedNow.plus(1, ChronoUnit.DAYS));

            SeatHold expiredHold = SeatHold.builder()
                    .id(UUID.randomUUID())
                    .customerId(customerId)
                    .showId(showId)
                    .status(SeatHoldStatus.HELD)
                    .expiresAt(fixedNow.minus(1, ChronoUnit.SECONDS))
                    .build();

            ShowSeat seat = buildSampleShowSeat(seatId, showId, "A", 1, ShowSeatState.AVAILABLE);

            when(showRepository.findById(showId)).thenReturn(Optional.of(show));
            when(seatHoldRepository.findByCustomerIdAndStatus(customerId, SeatHoldStatus.HELD)).thenReturn(Optional.of(expiredHold));
            when(distributedLockService.acquireAllLocks(anyList(), anyString(), any(Duration.class))).thenReturn(true);
            when(showSeatRepository.findAllByIdInWithPessimisticLock(anyList())).thenReturn(List.of(seat));
            when(seatHoldRepository.save(any(SeatHold.class))).thenAnswer(invocation -> invocation.getArgument(0));

            SeatHoldResponse response = seatHoldService.holdSeats(request, customerId);

            assertThat(response).isNotNull();
            assertThat(response.status()).isEqualTo(SeatHoldStatus.HELD);
        }

        @Test
        @DisplayName("Should throw SeatUnavailableException when Redis fast-path lock fails (Layer 1)")
        void holdSeats_whenRedisLockFails_shouldThrowSeatUnavailable() {
            UUID customerId = UUID.randomUUID();
            UUID showId = UUID.randomUUID();
            UUID seatId = UUID.randomUUID();
            CreateSeatHoldRequest request = new CreateSeatHoldRequest(showId, List.of(seatId));
            Show show = buildSampleShow(showId, ShowStatus.PUBLISHED, fixedNow.plus(1, ChronoUnit.DAYS));

            when(showRepository.findById(showId)).thenReturn(Optional.of(show));
            when(seatHoldRepository.findByCustomerIdAndStatus(customerId, SeatHoldStatus.HELD)).thenReturn(Optional.empty());
            when(distributedLockService.acquireAllLocks(anyList(), anyString(), any(Duration.class))).thenReturn(false);

            assertThatThrownBy(() -> seatHoldService.holdSeats(request, customerId))
                    .isInstanceOf(SeatUnavailableException.class)
                    .hasMessageContaining("One or more selected seats are currently held by another user");

            // Critical invariant: PostgreSQL row locks must NEVER be touched when Redis fast-fails!
            verifyNoInteractions(showSeatRepository);
            verify(seatHoldRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should release Redis locks and throw ResourceNotFoundException when seat IDs don't exist in DB")
        void holdSeats_whenSeatMissingInDb_shouldRollbackRedisAndThrow() {
            UUID customerId = UUID.randomUUID();
            UUID showId = UUID.randomUUID();
            UUID requestedSeat1 = UUID.randomUUID();
            UUID requestedSeat2 = UUID.randomUUID();
            CreateSeatHoldRequest request = new CreateSeatHoldRequest(showId, List.of(requestedSeat1, requestedSeat2));
            Show show = buildSampleShow(showId, ShowStatus.PUBLISHED, fixedNow.plus(1, ChronoUnit.DAYS));

            when(showRepository.findById(showId)).thenReturn(Optional.of(show));
            when(seatHoldRepository.findByCustomerIdAndStatus(customerId, SeatHoldStatus.HELD)).thenReturn(Optional.empty());
            when(distributedLockService.acquireAllLocks(anyList(), anyString(), any(Duration.class))).thenReturn(true);
            // DB only finds 1 of the 2 requested seats
            when(showSeatRepository.findAllByIdInWithPessimisticLock(anyList()))
                    .thenReturn(List.of(buildSampleShowSeat(requestedSeat1, showId, "A", 1, ShowSeatState.AVAILABLE)));

            assertThatThrownBy(() -> seatHoldService.holdSeats(request, customerId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("One or more requested seats do not exist");

            // Critical invariant: Compensating transaction releases Redis locks
            verify(distributedLockService).releaseAllLocks(anyList(), anyString());
            verify(seatHoldRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should release Redis locks and throw SeatUnavailableException when a seat is already HELD or SOLD in DB (Layer 2)")
        void holdSeats_whenSeatAlreadyHeldInDb_shouldRollbackRedisAndThrow() {
            UUID customerId = UUID.randomUUID();
            UUID showId = UUID.randomUUID();
            UUID seatId1 = UUID.randomUUID();
            UUID seatId2 = UUID.randomUUID();
            CreateSeatHoldRequest request = new CreateSeatHoldRequest(showId, List.of(seatId1, seatId2));
            Show show = buildSampleShow(showId, ShowStatus.PUBLISHED, fixedNow.plus(1, ChronoUnit.DAYS));

            ShowSeat seat1 = buildSampleShowSeat(seatId1, showId, "A", 1, ShowSeatState.AVAILABLE);
            ShowSeat seat2 = buildSampleShowSeat(seatId2, showId, "A", 2, ShowSeatState.HELD); // Already held!

            when(showRepository.findById(showId)).thenReturn(Optional.of(show));
            when(seatHoldRepository.findByCustomerIdAndStatus(customerId, SeatHoldStatus.HELD)).thenReturn(Optional.empty());
            when(distributedLockService.acquireAllLocks(anyList(), anyString(), any(Duration.class))).thenReturn(true);
            when(showSeatRepository.findAllByIdInWithPessimisticLock(anyList())).thenReturn(List.of(seat1, seat2));

            assertThatThrownBy(() -> seatHoldService.holdSeats(request, customerId))
                    .isInstanceOf(SeatUnavailableException.class)
                    .hasMessageContaining("Seat A2 is no longer available");

            // Critical invariant: Compensating transaction releases Redis locks
            verify(distributedLockService).releaseAllLocks(anyList(), anyString());
            verify(seatHoldRepository, never()).save(any());
        }
    }

    // =========================================================================
    // 2. releaseHold Tests
    // =========================================================================

    @Nested
    @DisplayName("releaseHold")
    class ReleaseHoldTests {

        @Test
        @DisplayName("Should release hold, transition seats to AVAILABLE, and release Redis locks")
        void releaseHold_whenValid_shouldSucceed() {
            UUID holdId = UUID.randomUUID();
            UUID customerId = UUID.randomUUID();
            UUID showId = UUID.randomUUID();
            UUID seatId1 = UUID.randomUUID();
            UUID seatId2 = UUID.randomUUID();

            SeatHold hold = SeatHold.builder()
                    .id(holdId)
                    .customerId(customerId)
                    .showId(showId)
                    .status(SeatHoldStatus.HELD)
                    .expiresAt(fixedNow.plus(8, ChronoUnit.MINUTES))
                    .build();

            SeatHoldItem item1 = SeatHoldItem.builder().id(UUID.randomUUID()).holdId(holdId).showSeatId(seatId1).build();
            SeatHoldItem item2 = SeatHoldItem.builder().id(UUID.randomUUID()).holdId(holdId).showSeatId(seatId2).build();

            ShowSeat seat1 = buildSampleShowSeat(seatId1, showId, "A", 1, ShowSeatState.HELD);
            ShowSeat seat2 = buildSampleShowSeat(seatId2, showId, "A", 2, ShowSeatState.HELD);

            when(seatHoldRepository.findByIdAndCustomerId(holdId, customerId)).thenReturn(Optional.of(hold));
            when(seatHoldItemRepository.findAllByHoldId(holdId)).thenReturn(List.of(item1, item2));
            when(showSeatRepository.findAllById(List.of(seatId1, seatId2))).thenReturn(List.of(seat1, seat2));

            SeatHoldResponse response = seatHoldService.releaseHold(holdId, customerId);

            assertThat(response).isNotNull();
            assertThat(response.status()).isEqualTo(SeatHoldStatus.RELEASED);
            assertThat(seat1.getState()).isEqualTo(ShowSeatState.AVAILABLE);
            assertThat(seat2.getState()).isEqualTo(ShowSeatState.AVAILABLE);

            verify(showSeatRepository).saveAllAndFlush(List.of(seat1, seat2));
            verify(seatHoldRepository).saveAndFlush(hold);
            verify(distributedLockService).releaseAllLocks(List.of("lock:seat:" + seatId1, "lock:seat:" + seatId2), holdId.toString());
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when hold does not exist or customer does not own it")
        void releaseHold_whenNotFound_shouldThrow() {
            UUID holdId = UUID.randomUUID();
            UUID customerId = UUID.randomUUID();

            when(seatHoldRepository.findByIdAndCustomerId(holdId, customerId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> seatHoldService.releaseHold(holdId, customerId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Hold not found with id: " + holdId);

            verifyNoInteractions(showSeatRepository);
            verifyNoInteractions(distributedLockService);
        }
    }

    // =========================================================================
    // 3. getHold Tests
    // =========================================================================

    @Nested
    @DisplayName("getHold")
    class GetHoldTests {

        @Test
        @DisplayName("Should return hold details when found")
        void getHold_whenFound_shouldReturnDetails() {
            UUID holdId = UUID.randomUUID();
            UUID customerId = UUID.randomUUID();
            UUID showId = UUID.randomUUID();
            UUID seatId = UUID.randomUUID();

            SeatHold hold = SeatHold.builder()
                    .id(holdId)
                    .customerId(customerId)
                    .showId(showId)
                    .status(SeatHoldStatus.HELD)
                    .expiresAt(fixedNow.plus(10, ChronoUnit.MINUTES))
                    .build();

            SeatHoldItem item = SeatHoldItem.builder().id(UUID.randomUUID()).holdId(holdId).showSeatId(seatId).build();

            when(seatHoldRepository.findByIdAndCustomerId(holdId, customerId)).thenReturn(Optional.of(hold));
            when(seatHoldItemRepository.findAllByHoldId(holdId)).thenReturn(List.of(item));

            SeatHoldResponse response = seatHoldService.getHold(holdId, customerId);

            assertThat(response).isNotNull();
            assertThat(response.id()).isEqualTo(holdId);
            assertThat(response.customerId()).isEqualTo(customerId);
            assertThat(response.showId()).isEqualTo(showId);
            assertThat(response.seatIds()).containsExactly(seatId);
            assertThat(response.status()).isEqualTo(SeatHoldStatus.HELD);
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when hold not found")
        void getHold_whenNotFound_shouldThrow() {
            UUID holdId = UUID.randomUUID();
            UUID customerId = UUID.randomUUID();

            when(seatHoldRepository.findByIdAndCustomerId(holdId, customerId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> seatHoldService.getHold(holdId, customerId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Hold not found with id: " + holdId);
        }
    }

    // =========================================================================
    // Helper Test Fixtures
    // =========================================================================

    private Show buildSampleShow(UUID showId, ShowStatus status, Instant bookingClosesAt) {
        return Show.builder()
                .id(showId)
                .movieId(UUID.randomUUID())
                .screenId(UUID.randomUUID())
                .seatMapVersionId(UUID.randomUUID())
                .formatId(UUID.randomUUID())
                .startsAt(fixedNow.plus(2, ChronoUnit.HOURS))
                .endsAt(fixedNow.plus(4, ChronoUnit.HOURS))
                .bookingOpensAt(fixedNow.minus(1, ChronoUnit.DAYS))
                .bookingClosesAt(bookingClosesAt)
                .status(status)
                .build();
    }

    private ShowSeat buildSampleShowSeat(UUID seatId, UUID showId, String row, int number, ShowSeatState state) {
        return ShowSeat.builder()
                .id(seatId)
                .showId(showId)
                .physicalSeatId(UUID.randomUUID())
                .seatSectionId(UUID.randomUUID())
                .rowLabel(row)
                .seatNumber(number)
                .seatType("REGULAR")
                .state(state)
                .version(0L)
                .build();
    }
}

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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Core application service orchestrating high-concurrency seat holds.
 * Implements the 3-layer defense pipeline:
 * 1. Redis fast-path distributed lock (sub-millisecond rejection of contention)
 * 2. Deterministic seat ID sorting (deadlock elimination)
 * 3. PostgreSQL pessimistic row-level locking (SELECT ... FOR UPDATE) and state machine transitions
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class SeatHoldService {

    public static final int HOLD_DURATION_MINUTES = 10;
    public static final int MAX_SEATS_PER_HOLD = 10;

    private final SeatHoldRepository seatHoldRepository;
    private final SeatHoldItemRepository seatHoldItemRepository;
    private final ShowRepository showRepository;
    private final ShowSeatRepository showSeatRepository;
    private final DistributedLockService distributedLockService;
    private final Clock clock;

    /**
     * Attempts to place an exclusive 10-minute hold on the requested seats.
     */
    public SeatHoldResponse holdSeats(CreateSeatHoldRequest request, UUID customerId) {
        // 1. Pre-condition validations
        Show show = showRepository.findById(request.showId())
                .orElseThrow(() -> new ResourceNotFoundException("Show not found with id: " + request.showId()));

        if (show.getStatus() != ShowStatus.PUBLISHED) {
            throw new IllegalStateException("Cannot hold seats for a show in status: " + show.getStatus());
        }

        if (show.getBookingClosesAt().isBefore(clock.instant())) {
            throw new IllegalStateException("Booking has closed for this show");
        }

        if (request.seatIds() == null || request.seatIds().isEmpty() || request.seatIds().size() > MAX_SEATS_PER_HOLD) {
            throw new IllegalArgumentException("Number of seats to hold must be between 1 and " + MAX_SEATS_PER_HOLD);
        }

        Optional<SeatHold> existingSeatHold = seatHoldRepository.findByCustomerIdAndStatus(customerId, SeatHoldStatus.HELD);
        if (existingSeatHold.isPresent() && !existingSeatHold.get().isExpired(clock)) {
            throw new IllegalStateException("Customer already has an active hold in progress");
        }

        // 2. Deadlock Prevention — Deterministic Sorting!
        List<UUID> sortedSeatIds = request.seatIds().stream()
                .sorted()
                .toList();

        // 3. Layer 1 — Redis Fast-Path Distributed Lock ("The Bouncer")
        List<String> lockKeys = sortedSeatIds.stream()
                .map(id -> "lock:seat:" + id)
                .toList();

        String holdToken = UUID.randomUUID().toString();
        boolean lockAcquired = distributedLockService.acquireAllLocks(lockKeys, holdToken, Duration.ofMinutes(HOLD_DURATION_MINUTES));
        if (!lockAcquired) {
            throw new SeatUnavailableException("One or more selected seats are currently held by another user");
        }

        // 4. Layer 2 — PostgreSQL Authoritative Pessimistic Row Lock ("The Judge")
        List<ShowSeat> foundSeats = showSeatRepository.findAllByIdInWithPessimisticLock(sortedSeatIds);
        if (foundSeats.size() != sortedSeatIds.size()) {
            distributedLockService.releaseAllLocks(lockKeys, holdToken);
            throw new ResourceNotFoundException("One or more requested seats do not exist");
        }

        for (ShowSeat seat : foundSeats) {
            if (seat.getState() != ShowSeatState.AVAILABLE) {
                distributedLockService.releaseAllLocks(lockKeys, holdToken);
                throw new SeatUnavailableException("Seat " + seat.getRowLabel() + seat.getSeatNumber() + " is no longer available");
            }
        }

        // 5. Layer 3 — State Transition & Persistence
        UUID holdId = UUID.randomUUID();
        Instant expiresAt = clock.instant().plus(HOLD_DURATION_MINUTES, ChronoUnit.MINUTES);

        SeatHold seatHold = SeatHold.builder()
                .id(holdId)
                .customerId(customerId)
                .showId(show.getId())
                .status(SeatHoldStatus.HELD)
                .expiresAt(expiresAt)
                .build();

        SeatHold savedHold = seatHoldRepository.save(seatHold);

        List<SeatHoldItem> holdItems = new ArrayList<>();
        for (ShowSeat seat : foundSeats) {
            seat.hold(holdId);
            holdItems.add(SeatHoldItem.builder()
                    .id(UUID.randomUUID())
                    .holdId(holdId)
                    .showSeatId(seat.getId())
                    .build());
        }

        showSeatRepository.saveAllAndFlush(foundSeats);
        seatHoldItemRepository.saveAll(holdItems);

        return SeatHoldResponse.fromDomain(savedHold, sortedSeatIds);
    }

    /**
     * Customer explicitly releases their hold prior to checkout completion.
     */
    public SeatHoldResponse releaseHold(UUID holdId, UUID customerId) {
        SeatHold seatHold = seatHoldRepository.findByIdAndCustomerId(holdId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Hold not found with id: " + holdId));

        seatHold.release();

        List<SeatHoldItem> seatHoldItems = seatHoldItemRepository.findAllByHoldId(holdId);
        List<UUID> showSeatIds = seatHoldItems.stream()
                .map(SeatHoldItem::getShowSeatId)
                .toList();

        List<ShowSeat> showSeats = showSeatRepository.findAllById(showSeatIds);
        for (ShowSeat seat : showSeats) {
            seat.release();
        }

        showSeatRepository.saveAllAndFlush(showSeats);
        seatHoldRepository.saveAndFlush(seatHold);

        List<String> lockKeys = showSeatIds.stream()
                .map(id -> "lock:seat:" + id)
                .toList();
        distributedLockService.releaseAllLocks(lockKeys, holdId.toString());

        return SeatHoldResponse.fromDomain(seatHold, showSeatIds);
    }

    /**
     * Retrieves details and remaining validity of an existing hold session.
     */
    @Transactional(readOnly = true)
    public SeatHoldResponse getHold(UUID holdId, UUID customerId) {
        SeatHold seatHold = seatHoldRepository.findByIdAndCustomerId(holdId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Hold not found with id: " + holdId));

        List<SeatHoldItem> seatHoldItems = seatHoldItemRepository.findAllByHoldId(holdId);
        List<UUID> showSeatIds = seatHoldItems.stream()
                .map(SeatHoldItem::getShowSeatId)
                .toList();

        return SeatHoldResponse.fromDomain(seatHold, showSeatIds);
    }
}

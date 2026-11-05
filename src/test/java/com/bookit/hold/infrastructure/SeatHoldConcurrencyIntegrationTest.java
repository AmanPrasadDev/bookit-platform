package com.bookit.hold.infrastructure;

import com.bookit.catalogue.domain.*;
import com.bookit.catalogue.infrastructure.repository.*;
import com.bookit.hold.application.dto.CreateSeatHoldRequest;
import com.bookit.hold.application.service.SeatHoldService;
import com.bookit.hold.domain.SeatHold;
import com.bookit.hold.domain.SeatHoldStatus;
import com.bookit.hold.domain.exception.SeatUnavailableException;
import com.bookit.hold.infrastructure.repository.SeatHoldItemRepository;
import com.bookit.hold.infrastructure.repository.SeatHoldRepository;
import com.bookit.identity.domain.User;
import com.bookit.identity.domain.UserStatus;
import com.bookit.identity.infrastructure.repository.UserRepository;
import com.bookit.show.domain.Show;
import com.bookit.show.domain.ShowSeat;
import com.bookit.show.domain.ShowSeatState;
import com.bookit.show.domain.ShowStatus;
import com.bookit.show.infrastructure.repository.ShowRepository;
import com.bookit.show.infrastructure.repository.ShowSeatRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * High-Concurrency Integration Test Suite for Milestone M4.
 *
 * Attacks the system using 100 concurrent threads hitting real PostgreSQL
 * and Redis containers to prove:
 * 1. Zero Double-Bookings under stampede conditions (exact same seats requested).
 * 2. Zero Deadlocks (Postgres 40P01) when overlapping seats are requested in reverse order.
 * 3. Atomic Multi-Key Rollback when partial seat overlaps occur (no ghost locks in Redis).
 * 4. Redis fast-path absorbs the blast wave before row locks are even touched.
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("local")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SeatHoldConcurrencyIntegrationTest {

    @Autowired
    private SeatHoldService seatHoldService;

    @Autowired
    private ShowRepository showRepository;

    @Autowired
    private ShowSeatRepository showSeatRepository;

    @Autowired
    private SeatHoldRepository seatHoldRepository;

    @Autowired
    private SeatHoldItemRepository seatHoldItemRepository;

    @Autowired
    private CityRepository cityRepository;

    @Autowired
    private OperatorOrganizationRepository organizationRepository;

    @Autowired
    private CinemaRepository cinemaRepository;

    @Autowired
    private ScreenRepository screenRepository;

    @Autowired
    private SeatMapVersionRepository seatMapVersionRepository;

    @Autowired
    private SeatSectionRepository seatSectionRepository;

    @Autowired
    private PhysicalSeatRepository physicalSeatRepository;

    @Autowired
    private MovieRepository movieRepository;

    @Autowired
    private MovieFormatRepository movieFormatRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    // Shared venue & movie fixtures initialized ONCE per test class run
    private City city;
    private OperatorOrganization organization;
    private Cinema cinema;
    private Screen imaxScreen;
    private SeatMapVersion seatMap;
    private SeatSection primeSection;
    private List<PhysicalSeat> physicalSeats;
    private Movie movie;
    private MovieFormat imaxFormat;

    // Per-test fixtures
    private Show publishedShow;
    private List<ShowSeat> showSeats;
    private List<UUID> seatIds;
    private long showTimeOffsetHours = 500;

    @BeforeAll
    void initMasterData() {
        // 1. Isolated City & Org to prevent collision with other integration tests
        city = cityRepository.findByNameIgnoreCase("Delhi-Concurrency")
                .orElseGet(() -> cityRepository.save(City.builder()
                        .id(UUID.randomUUID())
                        .name("Delhi-Concurrency")
                        .state("Delhi")
                        .countryCode("IND")
                        .build()));

        organization = organizationRepository.findByLegalName("PVR INOX Concurrency")
                .orElseGet(() -> organizationRepository.save(OperatorOrganization.builder()
                        .id(UUID.randomUUID())
                        .legalName("PVR INOX Concurrency")
                        .displayName("PVR INOX")
                        .contactEmail("contact@pvr.com")
                        .build()));

        // 2. Cinema & Screen (PVR Select Citywalk Saket Concurrency, Audi 1 - IMAX)
        cinema = cinemaRepository.findByOrganizationIdAndName(organization.getId(), "PVR Select Citywalk Saket Concurrency")
                .orElseGet(() -> cinemaRepository.save(Cinema.builder()
                        .id(UUID.randomUUID())
                        .organizationId(organization.getId())
                        .cityId(city.getId())
                        .name("PVR Select Citywalk Saket Concurrency")
                        .address("A-3, District Centre, Saket, New Delhi")
                        .build()));

        imaxScreen = screenRepository.findByCinemaIdAndName(cinema.getId(), "Audi 1 - IMAX Concurrency")
                .orElseGet(() -> screenRepository.save(Screen.builder()
                        .id(UUID.randomUUID())
                        .cinemaId(cinema.getId())
                        .name("Audi 1 - IMAX Concurrency")
                        .build()));

        // 3. Seat Map, Section & 10 Physical Seats (A1 to A10)
        seatMap = seatMapVersionRepository.findFirstByScreenIdAndStatusOrderByVersionNumberDesc(imaxScreen.getId(), SeatMapStatus.PUBLISHED)
                .orElseGet(() -> seatMapVersionRepository.save(SeatMapVersion.builder()
                        .id(UUID.randomUUID())
                        .screenId(imaxScreen.getId())
                        .versionNumber(1)
                        .status(SeatMapStatus.PUBLISHED)
                        .totalSeats(10)
                        .build()));

        List<SeatSection> sections = seatSectionRepository.findAllBySeatMapVersionIdOrderByDisplayOrderAsc(seatMap.getId());
        if (sections.isEmpty()) {
            primeSection = seatSectionRepository.save(SeatSection.builder()
                    .id(UUID.randomUUID())
                    .seatMapVersionId(seatMap.getId())
                    .name("Prime")
                    .categoryCode("PRIME")
                    .displayOrder(1)
                    .build());
        } else {
            primeSection = sections.getFirst();
        }

        physicalSeats = physicalSeatRepository.findAllBySeatMapVersionIdOrderByRowLabelAscSeatNumberAsc(seatMap.getId());
        if (physicalSeats.isEmpty()) {
            physicalSeats = new ArrayList<>();
            for (int i = 1; i <= 10; i++) {
                physicalSeats.add(PhysicalSeat.builder()
                        .id(UUID.randomUUID())
                        .seatMapVersionId(seatMap.getId())
                        .sectionId(primeSection.getId())
                        .rowLabel("A")
                        .seatNumber(i)
                        .seatType(SeatType.REGULAR)
                        .build());
            }
            physicalSeatRepository.saveAll(physicalSeats);
        }

        // 4. Movie & Format
        imaxFormat = movieFormatRepository.findByCode("IMAX_2D_CONC")
                .orElseGet(() -> movieFormatRepository.save(MovieFormat.builder()
                        .id(UUID.randomUUID())
                        .code("IMAX_2D_CONC")
                        .displayName("IMAX 2D Concurrency")
                        .status("ACTIVE")
                        .build()));

        movie = movieRepository.findByTitleIgnoreCase("Interstellar Concurrency")
                .orElseGet(() -> movieRepository.save(Movie.builder()
                        .id(UUID.randomUUID())
                        .title("Interstellar Concurrency")
                        .synopsis("Mankind was born on Earth. It was never meant to die here.")
                        .runtimeMinutes(169)
                        .certificate(MovieCertificate.UA)
                        .releaseDate(LocalDate.of(2014, 11, 7))
                        .status(MovieStatus.RELEASED)
                        .languages(Set.of("ENGLISH", "HINDI"))
                        .genres(Set.of("SCI_FI", "ADVENTURE"))
                        .supportedFormatIds(Set.of(imaxFormat.getId()))
                        .build()));
    }

    @BeforeEach
    void setUp() {
        cleanRedis();

        // Unique start time slot per test execution to prevent GiST temporal exclusion clashes
        showTimeOffsetHours += 10;
        Instant startsAt = Instant.now().plus(showTimeOffsetHours, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);

        publishedShow = showRepository.save(Show.builder()
                .id(UUID.randomUUID())
                .movieId(movie.getId())
                .screenId(imaxScreen.getId())
                .seatMapVersionId(seatMap.getId())
                .formatId(imaxFormat.getId())
                .startsAt(startsAt)
                .endsAt(startsAt.plus(3, ChronoUnit.HOURS))
                .bookingOpensAt(startsAt.minus(3, ChronoUnit.DAYS))
                .bookingClosesAt(startsAt.plus(15, ChronoUnit.MINUTES))
                .status(ShowStatus.PUBLISHED)
                .build());

        // Materialize 10 ShowSeats in AVAILABLE state
        showSeats = new ArrayList<>();
        seatIds = new ArrayList<>();
        for (PhysicalSeat ps : physicalSeats) {
            ShowSeat seat = ShowSeat.builder()
                    .id(UUID.randomUUID())
                    .showId(publishedShow.getId())
                    .physicalSeatId(ps.getId())
                    .seatSectionId(primeSection.getId())
                    .rowLabel(ps.getRowLabel())
                    .seatNumber(ps.getSeatNumber())
                    .seatType(ps.getSeatType().name())
                    .state(ShowSeatState.AVAILABLE)
                    .build();
            showSeats.add(seat);
            seatIds.add(seat.getId());
        }
        showSeatRepository.saveAllAndFlush(showSeats);
    }

    @AfterEach
    void tearDown() {
        cleanRedis();
        if (publishedShow != null) {
            seatHoldItemRepository.deleteAll();
            seatHoldRepository.deleteAll();
            showSeatRepository.deleteAll();
            showRepository.deleteById(publishedShow.getId());
        }
    }

    @AfterAll
    void cleanAll() {
        cleanRedis();
        if (publishedShow != null) {
            seatHoldItemRepository.deleteAll();
            seatHoldRepository.deleteAll();
            showSeatRepository.deleteAll();
            showRepository.deleteById(publishedShow.getId());
        }
        if (seatMap != null) {
            physicalSeatRepository.deleteAll();
            seatSectionRepository.deleteAll();
            seatMapVersionRepository.deleteById(seatMap.getId());
        }
        if (imaxScreen != null) {
            screenRepository.deleteById(imaxScreen.getId());
        }
        if (cinema != null) {
            cinemaRepository.deleteById(cinema.getId());
        }
        if (organization != null) {
            organizationRepository.deleteById(organization.getId());
        }
        if (city != null) {
            cityRepository.deleteById(city.getId());
        }
        if (movie != null) {
            movieRepository.deleteById(movie.getId());
        }
        if (imaxFormat != null) {
            movieFormatRepository.deleteById(imaxFormat.getId());
        }
        userRepository.deleteAll();
    }

    private void cleanRedis() {
        Set<String> keys = stringRedisTemplate.keys("lock:seat:*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }

    // =========================================================================
    // SCENARIO 1: THE STAMPEDE (ZERO DOUBLE-BOOKINGS)
    // =========================================================================

    @Test
    @DisplayName("Stampede: 100 concurrent threads attack the exact same 10 seats -> Exactly 1 winner, 99 rejections, 0 double-bookings")
    void shouldAllowExactlyOneWinnerWhen100ConcurrentThreadsAttackSame10Seats() throws InterruptedException {
        int numberOfThreads = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);

        // Pre-create 100 valid customers in app_user table to satisfy fk_seat_hold_customer
        List<User> customers = new ArrayList<>();
        for (int i = 0; i < numberOfThreads; i++) {
            customers.add(User.builder()
                    .id(UUID.randomUUID())
                    .email("stampede-user-" + i + "-" + UUID.randomUUID() + "@bookit.com")
                    .fullName("Customer " + i)
                    .status(UserStatus.ACTIVE)
                    .build());
        }
        userRepository.saveAll(customers);

        CountDownLatch readyLatch = new CountDownLatch(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numberOfThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger seatUnavailableCount = new AtomicInteger(0);
        AtomicInteger unexpectedErrorCount = new AtomicInteger(0);
        List<Throwable> unexpectedErrors = Collections.synchronizedList(new ArrayList<>());

        CreateSeatHoldRequest request = new CreateSeatHoldRequest(publishedShow.getId(), seatIds);

        // 1. Line up all 100 threads at the starting block
        for (int i = 0; i < numberOfThreads; i++) {
            final UUID customerId = customers.get(i).getId();

            executorService.execute(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await(); // Wait for gunshot!

                    seatHoldService.holdSeats(request, customerId);
                    successCount.incrementAndGet();
                } catch (SeatUnavailableException e) {
                    seatUnavailableCount.incrementAndGet();
                } catch (Throwable t) {
                    unexpectedErrorCount.incrementAndGet();
                    unexpectedErrors.add(t);
                    log.error("Unexpected error in stampede test: ", t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // 2. Fire starting gunshot
        readyLatch.await(10, TimeUnit.SECONDS);
        startLatch.countDown();
        doneLatch.await(30, TimeUnit.SECONDS);
        executorService.shutdown();

        // 3. Mathematical proof of zero double-bookings
        assertThat(successCount.get())
                .as("Exactly ONE thread must succeed in holding the seats")
                .isEqualTo(1);

        assertThat(seatUnavailableCount.get())
                .as("All other 99 threads must be rejected with SeatUnavailableException")
                .isEqualTo(numberOfThreads - 1);

        assertThat(unexpectedErrorCount.get())
                .as("There must be zero unexpected errors or deadlocks. Errors: " + unexpectedErrors)
                .isZero();

        // 4. PostgreSQL state verification
        List<SeatHold> activeHolds = seatHoldRepository.findAllByShowIdAndStatus(publishedShow.getId(), SeatHoldStatus.HELD);
        assertThat(activeHolds)
                .as("PostgreSQL must contain exactly 1 active hold in HELD status")
                .hasSize(1);
        UUID winningHoldId = activeHolds.getFirst().getId();

        List<ShowSeat> persistedSeats = showSeatRepository.findByShowId(publishedShow.getId());
        assertThat(persistedSeats).hasSize(10);
        assertThat(persistedSeats)
                .as("All 10 seats in the show must be marked as HELD")
                .allMatch(seat -> seat.getState() == ShowSeatState.HELD);
        assertThat(persistedSeats)
                .as("All 10 seats must reference the winning hold ID")
                .allMatch(seat -> winningHoldId.equals(seat.getActiveHoldId()));

        // 5. Redis state verification
        Set<String> redisKeys = stringRedisTemplate.keys("lock:seat:*");
        assertThat(redisKeys)
                .as("Redis must hold exactly 10 distributed lock keys")
                .isNotNull()
                .hasSize(10);
    }

    // =========================================================================
    // SCENARIO 2: CROSS-SELECTION (ZERO DEADLOCKS)
    // =========================================================================

    @Test
    @DisplayName("Deadlock Elimination: 50 threads request [Seat A, Seat B] while 50 threads request [Seat B, Seat A] -> 0 deadlocks")
    void shouldPreventDeadlocksWhenConcurrentThreadsRequestOverlappingSeatsInReverseOrder() throws InterruptedException {
        int numberOfThreads = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);

        // Pre-create 100 valid customers in app_user table
        List<User> customers = new ArrayList<>();
        for (int i = 0; i < numberOfThreads; i++) {
            customers.add(User.builder()
                    .id(UUID.randomUUID())
                    .email("deadlock-user-" + i + "-" + UUID.randomUUID() + "@bookit.com")
                    .fullName("Customer " + i)
                    .status(UserStatus.ACTIVE)
                    .build());
        }
        userRepository.saveAll(customers);

        CountDownLatch readyLatch = new CountDownLatch(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numberOfThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger seatUnavailableCount = new AtomicInteger(0);
        AtomicInteger unexpectedErrorCount = new AtomicInteger(0);
        List<Throwable> unexpectedErrors = Collections.synchronizedList(new ArrayList<>());

        UUID seatA = seatIds.get(0);
        UUID seatB = seatIds.get(1);

        CreateSeatHoldRequest requestForward = new CreateSeatHoldRequest(publishedShow.getId(), List.of(seatA, seatB));
        CreateSeatHoldRequest requestReverse = new CreateSeatHoldRequest(publishedShow.getId(), List.of(seatB, seatA));

        // 1. Line up 50 threads requesting [A, B] and 50 threads requesting [B, A]
        for (int i = 0; i < numberOfThreads; i++) {
            final UUID customerId = customers.get(i).getId();
            final CreateSeatHoldRequest targetRequest = (i % 2 == 0) ? requestForward : requestReverse;

            executorService.execute(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();

                    seatHoldService.holdSeats(targetRequest, customerId);
                    successCount.incrementAndGet();
                } catch (SeatUnavailableException e) {
                    seatUnavailableCount.incrementAndGet();
                } catch (Throwable t) {
                    unexpectedErrorCount.incrementAndGet();
                    unexpectedErrors.add(t);
                    log.error("Unexpected error in deadlock test: ", t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // 2. Fire starting gunshot
        readyLatch.await(10, TimeUnit.SECONDS);
        startLatch.countDown();
        doneLatch.await(30, TimeUnit.SECONDS);
        executorService.shutdown();

        // 3. Mathematical proof of deadlock elimination
        assertThat(successCount.get())
                .as("Exactly ONE thread must succeed in holding [Seat A, Seat B]")
                .isEqualTo(1);

        assertThat(seatUnavailableCount.get())
                .as("All 99 competing threads must be rejected")
                .isEqualTo(numberOfThreads - 1);

        assertThat(unexpectedErrorCount.get())
                .as("PostgreSQL deadlock error 40P01 must be completely eliminated by deterministic sorting. Errors: " + unexpectedErrors)
                .isZero();

        // 4. Verify only Seat A and Seat B are HELD, other 8 seats remain AVAILABLE
        ShowSeat seat1 = showSeatRepository.findById(seatA).orElseThrow();
        ShowSeat seat2 = showSeatRepository.findById(seatB).orElseThrow();
        assertThat(seat1.getState()).isEqualTo(ShowSeatState.HELD);
        assertThat(seat2.getState()).isEqualTo(ShowSeatState.HELD);

        long availableSeatsCount = showSeatRepository.countByShowIdAndState(publishedShow.getId(), ShowSeatState.AVAILABLE);
        assertThat(availableSeatsCount).isEqualTo(8);

        // 5. Verify exactly 2 Redis lock keys exist
        Set<String> redisKeys = stringRedisTemplate.keys("lock:seat:*");
        assertThat(redisKeys).isNotNull().hasSize(2);
    }

    // =========================================================================
    // SCENARIO 3: PARTIAL CONTENTION ROLLBACK (NO GHOST LOCKS)
    // =========================================================================

    @Test
    @DisplayName("Partial Rollback: When 1 seat in batch is already held, previously acquired Redis locks are cleanly rolled back")
    void shouldRollbackPartialLocksWhenOneSeatInBatchIsUnavailable() {
        UUID seatA = seatIds.get(0);
        UUID seatB = seatIds.get(1);

        User customer1 = userRepository.save(User.builder()
                .id(UUID.randomUUID())
                .email("c1-" + UUID.randomUUID() + "@bookit.com")
                .fullName("Customer 1")
                .status(UserStatus.ACTIVE)
                .build());

        User customer2 = userRepository.save(User.builder()
                .id(UUID.randomUUID())
                .email("c2-" + UUID.randomUUID() + "@bookit.com")
                .fullName("Customer 2")
                .status(UserStatus.ACTIVE)
                .build());

        // Customer 1 holds Seat A successfully
        CreateSeatHoldRequest request1 = new CreateSeatHoldRequest(publishedShow.getId(), List.of(seatA));
        seatHoldService.holdSeats(request1, customer1.getId());

        // Verify Seat A is held in Redis
        assertThat(stringRedisTemplate.hasKey("lock:seat:" + seatA)).isTrue();
        assertThat(stringRedisTemplate.hasKey("lock:seat:" + seatB)).isFalse();

        // Customer 2 attempts to hold [Seat A, Seat B]
        CreateSeatHoldRequest request2 = new CreateSeatHoldRequest(publishedShow.getId(), List.of(seatA, seatB));

        org.junit.jupiter.api.Assertions.assertThrows(SeatUnavailableException.class, () ->
                seatHoldService.holdSeats(request2, customer2.getId())
        );

        // Verification: Seat B's lock MUST NOT be leaked in Redis ("Ghost Lock" prevention)
        assertThat(stringRedisTemplate.hasKey("lock:seat:" + seatB))
                .as("Seat B lock must not exist in Redis because Customer 2's transaction rolled back")
                .isFalse();

        // Verification: Seat B in PostgreSQL must remain AVAILABLE
        ShowSeat seatBEntity = showSeatRepository.findById(seatB).orElseThrow();
        assertThat(seatBEntity.getState()).isEqualTo(ShowSeatState.AVAILABLE);
    }
}

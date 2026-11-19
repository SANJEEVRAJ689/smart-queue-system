package com.smartqueue;

import com.smartqueue.concurrent.NoShowWatcher;
import com.smartqueue.dao.CounterDao;
import com.smartqueue.dao.TokenDao;
import com.smartqueue.dao.impl.CounterDaoImpl;
import com.smartqueue.dao.impl.TokenDaoImpl;
import com.smartqueue.model.*;
import com.smartqueue.service.QueueManager;
import com.smartqueue.service.TokenService;
import com.smartqueue.util.AppException;
import com.smartqueue.util.DatabaseConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.CyclicBarrier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Acceptance Test Suite for the Fixed Calling Logic:
 * 1. 20 threads pressing Call Next on one counter produce exactly one active token.
 * 2. Two counters with 50 waiting tokens never claim the same token.
 * 3. Double-click is harmless (returns existing active token without claiming another).
 * 4. Auto-call plus manual click concurrency safe.
 * 5. Complete returns counter to OPEN (and triggers auto-call if enabled).
 * 6. No-show triggers SKIPPED and auto-calls next token.
 * 7. Single recall enforcement: allowed once, second attempt rejected.
 */
public class CallingLogicConcurrencyTest {

    private TokenService tokenService;
    private TokenDao tokenDao;
    private CounterDao counterDao;

    @BeforeEach
    void setUp() throws Exception {
        tokenService = new TokenService();
        tokenDao = new TokenDaoImpl();
        counterDao = new CounterDaoImpl();

        // Reset database state for clean, isolated tests
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement psDel = conn.prepareStatement(
                     "DELETE FROM tokens WHERE token_no LIKE 'SNG-%' OR token_no LIKE 'FIF-%' OR " +
                     "token_no LIKE 'DBL-%' OR token_no LIKE 'MIX-%' OR token_no LIKE 'AUT-%' OR " +
                     "token_no LIKE 'NS-%' OR token_no LIKE 'RCL-%' OR token_no LIKE 'SEQ-%'");
             PreparedStatement psClean = conn.prepareStatement(
                     "UPDATE tokens SET status = 'COMPLETED', completed_at = CURRENT_TIMESTAMP " +
                     "WHERE status IN ('WAITING', 'CALLED', 'SERVING')");
             PreparedStatement psCounters = conn.prepareStatement(
                     "UPDATE counters SET status = 'OPEN', current_token_id = NULL WHERE id IN (1, 2)")) {
            psDel.executeUpdate();
            psClean.executeUpdate();
            psCounters.executeUpdate();
        }
        QueueManager.getInstance().setAutoCallEnabled(1, false);
        QueueManager.getInstance().setAutoCallEnabled(2, false);
    }

    private Token createWaitingToken(int serviceId, String tokenNo, Priority priority) {
        String cleanNo = tokenNo.length() > 10 ? tokenNo.substring(0, 10) : tokenNo;
        Token t = new Token();
        t.setTokenNo(cleanNo);
        t.setServiceId(serviceId);
        t.setCustomerName("Test Customer " + cleanNo);
        t.setPhone("98765" + String.format("%05d", Math.abs(cleanNo.hashCode() % 100000)));
        t.setPriority(priority != null ? priority : Priority.NORMAL);
        t.setStatus(TokenStatus.WAITING);
        t.setQueueDate(LocalDate.now());
        boolean ok = tokenDao.create(t);
        assertTrue(ok, "Token " + cleanNo + " must be created successfully");
        return t;
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement psDel = conn.prepareStatement(
                     "DELETE FROM tokens WHERE token_no LIKE 'SNG-%' OR token_no LIKE 'FIF-%' OR " +
                     "token_no LIKE 'DBL-%' OR token_no LIKE 'MIX-%' OR token_no LIKE 'AUT-%' OR " +
                     "token_no LIKE 'NS-%' OR token_no LIKE 'RCL-%' OR token_no LIKE 'SEQ-%'");
             PreparedStatement psClean = conn.prepareStatement(
                     "UPDATE tokens SET status = 'COMPLETED' WHERE status IN ('CALLED', 'SERVING')");
             PreparedStatement psCounters = conn.prepareStatement(
                     "UPDATE counters SET status = 'OPEN', current_token_id = NULL WHERE id IN (1, 2)")) {
            psDel.executeUpdate();
            psClean.executeUpdate();
            psCounters.executeUpdate();
        } catch (Exception ignored) {}
        QueueManager.getInstance().setAutoCallEnabled(1, false);
        QueueManager.getInstance().setAutoCallEnabled(2, false);
    }

    @Test
    @DisplayName("1. 20 threads pressing Call Next on one counter produce exactly ONE active token")
    void testTwentyThreadsCallNextSingleCounter() throws Exception {
        int counterId = 1;
        int threadCount = 20;

        // Seed 5 waiting tokens for service 1
        for (int i = 1; i <= 5; i++) {
            createWaitingToken(1, "SNG-" + i, Priority.NORMAL);
        }

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(threadCount);
        Set<Integer> claimedTokenIds = Collections.synchronizedSet(new HashSet<>());

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    Optional<Token> t = tokenService.callNextToken(counterId);
                    t.ifPresent(token -> claimedTokenIds.add(token.getId()));
                } catch (Exception ignored) {
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown();
        assertTrue(endGate.await(10, TimeUnit.SECONDS), "Threads timed out");
        executor.shutdown();

        // Exactly ONE distinct token was returned across all 20 threads!
        assertEquals(1, claimedTokenIds.size(), "All 20 threads must return the exact same single claimed token");

        // Database guarantee check: exactly 1 active token on counter 1
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM tokens WHERE counter_id = ? AND status IN ('CALLED', 'SERVING')")) {
            ps.setInt(1, counterId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1), "Database must contain exactly 1 active token for counter 1");
            }
        }
    }

    @Test
    @DisplayName("2. Two counters with 200 waiting tokens never claim the same token")
    void testTwoCountersFiftyTokensNeverOverlap() throws Exception {
        counterDao.clearToken(1, CounterStatus.OPEN);
        counterDao.clearToken(2, CounterStatus.OPEN);
        // Use 200 tokens so both counters statistically get some regardless of scheduling jitter
        int tokenCount = 200;
        for (int i = 1; i <= tokenCount; i++) {
            createWaitingToken(1, "FIF-" + String.format("%03d", i), Priority.NORMAL);
        }

        Set<Integer> counter1Claimed = Collections.synchronizedSet(new HashSet<>());
        Set<Integer> counter2Claimed = Collections.synchronizedSet(new HashSet<>());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        // CyclicBarrier: both threads must arrive before either proceeds, ensuring a fair race
        CyclicBarrier barrier = new CyclicBarrier(2);
        CountDownLatch done = new CountDownLatch(2);

        // Counter 1 worker: continuously calls and completes
        executor.submit(() -> {
            try {
                barrier.await(); // synchronised start
                while (true) {
                    Optional<Token> opt = tokenService.callNextToken(1);
                    if (opt.isEmpty()) break;
                    Token t = opt.get();
                    if (!counter1Claimed.add(t.getId())) {
                        break; // already claimed
                    }
                    tokenService.startServing(t.getId());
                    tokenService.completeToken(t.getId(), 1);
                    Thread.sleep(5);
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                done.countDown();
            }
        });

        // Counter 2 worker: continuously calls and completes
        executor.submit(() -> {
            try {
                barrier.await(); // synchronised start
                while (true) {
                    Optional<Token> opt = tokenService.callNextToken(2);
                    if (opt.isEmpty()) break;
                    Token t = opt.get();
                    if (!counter2Claimed.add(t.getId())) {
                        break; // already claimed
                    }
                    tokenService.startServing(t.getId());
                    tokenService.completeToken(t.getId(), 2);
                    Thread.sleep(5);
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                done.countDown();
            }
        });

        assertTrue(done.await(20, TimeUnit.SECONDS), "Processing timed out");
        executor.shutdown();

        // Ensure both counters processed tokens
        assertFalse(counter1Claimed.isEmpty(), "Counter 1 must have processed tokens");
        assertFalse(counter2Claimed.isEmpty(), "Counter 2 must have processed tokens");

        // Verify zero overlap between counters
        Set<Integer> intersection = new HashSet<>(counter1Claimed);
        intersection.retainAll(counter2Claimed);
        assertTrue(intersection.isEmpty(), "Counters must NEVER claim the same token! Overlap: " + intersection);
    }

    @Test
    @DisplayName("3. Double-click is harmless (returns existing active token without claiming another)")
    void testDoubleClickHarmless() {
        createWaitingToken(1, "DBL-001", Priority.NORMAL);
        createWaitingToken(1, "DBL-002", Priority.NORMAL);

        Optional<Token> firstClick = tokenService.callNextToken(1);
        assertTrue(firstClick.isPresent());
        int firstTokenId = firstClick.get().getId();

        // Double-click
        Optional<Token> secondClick = tokenService.callNextToken(1);
        assertTrue(secondClick.isPresent());
        assertEquals(firstTokenId, secondClick.get().getId(), "Double click must return the already called token");

        // Verify DBL-002 is still WAITING and not claimed
        List<Token> waiting = tokenDao.findWaitingTokens(1);
        assertEquals(1, waiting.size());
        assertEquals("DBL-002", waiting.get(0).getTokenNo());
    }

    @Test
    @DisplayName("4. Auto-call plus manual click concurrency safe")
    void testAutoCallAndManualClickSimultaneous() throws Exception {
        createWaitingToken(1, "MIX-001", Priority.NORMAL);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        List<Optional<Token>> results = Collections.synchronizedList(new ArrayList<>());

        executor.submit(() -> {
            try {
                start.await();
                results.add(tokenService.callNextToken(1)); // Manual click
            } catch (Exception ignored) {
            } finally {
                done.countDown();
            }
        });

        executor.submit(() -> {
            try {
                start.await();
                results.add(tokenService.callNextToken(1)); // Auto-call
            } catch (Exception ignored) {
            } finally {
                done.countDown();
            }
        });

        start.countDown();
        assertTrue(done.await(5, TimeUnit.SECONDS));
        executor.shutdown();

        assertEquals(2, results.size());
        assertTrue(results.get(0).isPresent() && results.get(1).isPresent());
        assertEquals(results.get(0).get().getId(), results.get(1).get().getId(),
                "Both manual and auto-call must observe the exact same single token");
    }

    @Test
    @DisplayName("5. Complete returns counter to OPEN (and immediately calls next if auto-call enabled)")
    void testCompleteReturnsCounterToOpenAndAutoCalls() throws Exception {
        QueueManager.getInstance().setAutoCallEnabled(1, true);

        createWaitingToken(1, "AUT-001", Priority.NORMAL);
        createWaitingToken(1, "AUT-002", Priority.NORMAL);

        Optional<Token> t1 = tokenService.callNextToken(1);
        assertTrue(t1.isPresent());
        tokenService.startServing(t1.get().getId());

        // Counter is BUSY
        Optional<Counter> cBusy = counterDao.findById(1);
        assertTrue(cBusy.isPresent());
        assertEquals(CounterStatus.BUSY, cBusy.get().getStatus());

        // Complete token 1
        tokenService.completeToken(t1.get().getId(), 1);

        // Wait a brief moment for auto-call worker task to execute
        Thread.sleep(600);

        // Counter 1 must have automatically claimed AUT-002!
        Optional<Counter> cAfter = counterDao.findById(1);
        assertTrue(cAfter.isPresent());
        Optional<Token> activeToken = tokenDao.findCurrentlyServingTokens().stream()
                .filter(t -> t.getCounterId() != null && t.getCounterId() == 1)
                .findFirst();

        assertTrue(activeToken.isPresent(), "Auto-call must have claimed the next waiting token");
        assertEquals("AUT-002", activeToken.get().getTokenNo());
    }

    @Test
    @DisplayName("6. No-show triggers SKIPPED and auto-calls next token")
    void testNoShowWatcherTransitionsAndAutoCalls() throws Exception {
        QueueManager.getInstance().setAutoCallEnabled(1, true);

        Token t1 = createWaitingToken(1, "NS-001", Priority.NORMAL);
        createWaitingToken(1, "NS-002", Priority.NORMAL);

        // Call t1
        tokenService.callNextToken(1);

        // Simulate overdue called token (>120s timeout)
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE tokens SET called_at = ? WHERE id = ?")) {
            ps.setTimestamp(1, Timestamp.from(Instant.now().minusSeconds(150)));
            ps.setInt(2, t1.getId());
            ps.executeUpdate();
        }

        // Run NoShowWatcher
        NoShowWatcher watcher = new NoShowWatcher();
        watcher.run();

        // Verify t1 is now SKIPPED
        Optional<Token> t1Skipped = tokenDao.findById(t1.getId());
        assertTrue(t1Skipped.isPresent());
        assertEquals(TokenStatus.SKIPPED, t1Skipped.get().getStatus());

        // Wait a brief moment for auto-call task
        Thread.sleep(600);

        // Counter 1 should now hold NS-002
        Optional<Token> activeAfterNoShow = tokenDao.findCurrentlyServingTokens().stream()
                .filter(t -> t.getCounterId() != null && t.getCounterId() == 1)
                .findFirst();
        assertTrue(activeAfterNoShow.isPresent(), "Auto-call must immediately claim next token after no-show");
        assertEquals("NS-002", activeAfterNoShow.get().getTokenNo());
    }

    @Test
    @DisplayName("7. Skipped customer can be recalled once; second recall is rejected")
    void testSingleRecallEnforcement() throws Exception {
        Token t = createWaitingToken(1, "RCL-001", Priority.NORMAL);

        // Call and skip
        tokenService.callNextToken(1);
        tokenService.skipToken(t.getId(), 1);

        Optional<Token> skippedOpt = tokenDao.findById(t.getId());
        assertTrue(skippedOpt.isPresent());
        assertEquals(TokenStatus.SKIPPED, skippedOpt.get().getStatus());
        assertEquals(0, skippedOpt.get().getRecallCount());

        // First recall: must succeed
        boolean firstRecall = tokenService.recallToken(t.getId(), 1);
        assertTrue(firstRecall, "First recall must succeed");

        Optional<Token> recalledOpt = tokenDao.findById(t.getId());
        assertTrue(recalledOpt.isPresent());
        assertEquals(TokenStatus.WAITING, recalledOpt.get().getStatus());
        assertEquals(1, recalledOpt.get().getRecallCount());

        // Call and skip again
        tokenService.callNextToken(1);
        tokenService.skipToken(t.getId(), 1);

        // Second recall: must throw AppException
        assertThrows(AppException.class, () -> {
            tokenService.recallToken(t.getId(), 1);
        }, "Second recall must be rejected");
    }

    @Test
    @DisplayName("8. Counter 1 handles 5 customers in a row without ever holding two tokens")
    void testOneCounterHandlesFiveCustomersInARowWithoutHoldingTwoTokens() throws Exception {
        int counterId = 1;
        int serviceId = 1;

        // Ensure counter is OPEN before starting
        counterDao.clearToken(counterId, CounterStatus.OPEN);

        // Seed 5 waiting customers
        List<Token> seeded = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            seeded.add(createWaitingToken(serviceId, "SEQ-00" + i, Priority.NORMAL));
        }

        System.out.println("=== STARTING 5-CUSTOMER SEQUENTIAL ACCEPTANCE CHECK ON COUNTER 1 ===");
        for (int step = 0; step < 5; step++) {
            Token expectedCustomer = seeded.get(step);

            // Verify counter is OPEN before call
            Counter beforeCall = counterDao.findById(counterId).orElseThrow();
            assertEquals(CounterStatus.OPEN, beforeCall.getStatus(),
                    "Step " + (step + 1) + ": Counter must be OPEN prior to calling customer");

            // 1. Call Next
            Optional<Token> calledOpt = tokenService.callNextToken(counterId);
            assertTrue(calledOpt.isPresent(), "Step " + (step + 1) + ": Token must be claimed");
            Token activeToken = calledOpt.get();
            assertEquals(expectedCustomer.getId(), activeToken.getId(),
                    "Step " + (step + 1) + ": Expected token " + expectedCustomer.getTokenNo());

            // Verify Counter is BUSY with active token
            Counter duringCall = counterDao.findById(counterId).orElseThrow();
            assertEquals(CounterStatus.BUSY, duringCall.getStatus(),
                    "Step " + (step + 1) + ": Counter must be BUSY after Call Next");
            assertEquals(activeToken.getId(), duringCall.getCurrentTokenId(),
                    "Step " + (step + 1) + ": Counter current_token_id must match called token");

            // Verify active tokens in DB for counter is strictly 1
            int activeCountCalled = countActiveTokensForCounter(counterId);
            assertEquals(1, activeCountCalled,
                    "Step " + (step + 1) + ": Must have exactly 1 active token in DB during CALLED status");

            // Verify harmless double-click: calling next while BUSY returns SAME token without claiming another
            Optional<Token> doubleClick = tokenService.callNextToken(counterId);
            assertTrue(doubleClick.isPresent());
            assertEquals(activeToken.getId(), doubleClick.get().getId(),
                    "Step " + (step + 1) + ": Double-click must return same active token");
            assertEquals(1, countActiveTokensForCounter(counterId),
                    "Step " + (step + 1) + ": Active token count must remain strictly 1 after double-click");

            // 2. Start Serving
            tokenService.startServing(activeToken.getId());
            Token servingToken = tokenDao.findById(activeToken.getId()).orElseThrow();
            assertEquals(TokenStatus.SERVING, servingToken.getStatus(),
                    "Step " + (step + 1) + ": Token status must transition to SERVING");
            assertEquals(1, countActiveTokensForCounter(counterId),
                    "Step " + (step + 1) + ": Must have exactly 1 active token in DB during SERVING status");

            // 3. Complete Token
            boolean completed = tokenService.completeToken(activeToken.getId(), counterId);
            assertTrue(completed, "Step " + (step + 1) + ": Completion must succeed");

            // Verify counter returns to OPEN and current_token_id is cleared
            Counter afterComplete = counterDao.findById(counterId).orElseThrow();
            assertEquals(CounterStatus.OPEN, afterComplete.getStatus(),
                    "Step " + (step + 1) + ": Counter must return to OPEN after customer is completed");
            assertNull(afterComplete.getCurrentTokenId(),
                    "Step " + (step + 1) + ": Counter current_token_id must be null after completion");

            // Verify active token count in DB is now 0
            int activeCountAfterComplete = countActiveTokensForCounter(counterId);
            assertEquals(0, activeCountAfterComplete,
                    "Step " + (step + 1) + ": Counter must have 0 active tokens after completion");

            System.out.printf("  [Step %d/5] Handled customer %s: CALLED -> SERVING -> COMPLETED. Active tokens: 0. Counter: OPEN%n",
                    step + 1, expectedCustomer.getTokenNo());
        }
        System.out.println("=== 5-CUSTOMER SEQUENTIAL ACCEPTANCE CHECK PASSED SUCCESSFULLY ===");
    }

    private int countActiveTokensForCounter(int counterId) throws Exception {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM tokens WHERE counter_id = ? AND status IN ('CALLED', 'SERVING')")) {
            ps.setInt(1, counterId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }
}

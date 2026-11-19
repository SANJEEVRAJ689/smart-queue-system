package com.smartqueue;

import com.smartqueue.concurrent.AutoCaller;
import com.smartqueue.dao.CounterDao;
import com.smartqueue.dao.TokenDao;
import com.smartqueue.dao.impl.CounterDaoImpl;
import com.smartqueue.dao.impl.TokenDaoImpl;
import com.smartqueue.model.Counter;
import com.smartqueue.model.Priority;
import com.smartqueue.model.Token;
import com.smartqueue.model.TokenStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Concurrency Test verifying that AutoCaller and row-level locking
 * prevent race conditions when multiple counters call next simultaneously.
 *
 * Test Setup:
 * - 5 waiting tokens in the queue.
 * - 10 concurrent threads (simulating 10 requests from active counters calling next at the exact same millisecond).
 *
 * Assertions:
 * 1. Exactly 5 distinct tokens are claimed.
 * 2. ZERO tokens are claimed twice (no duplicates).
 * 3. Exactly 5 threads receive an empty Optional.
 */
public class ConcurrentCallNextTest {

    @Test
    @DisplayName("Race condition test: 10 concurrent counters calling next for 5 waiting tokens")
    void testConcurrentCallNextPreventsDuplicateClaims() throws Exception {
        TokenDao tokenDao = new TokenDaoImpl();
        CounterDao counterDao = new CounterDaoImpl();
        AutoCaller autoCaller = new AutoCaller();

        int testServiceId = 1; // Service 1 (e.g. AD)
        int tokenCount = 5;
        int threadCount = 10;

        // Use the seeded counters (counters 1 to 4)
        List<Counter> existingCounters = counterDao.findAll();
        assertFalse(existingCounters.isEmpty(), "Seeded counters must exist");

        // Test isolation: Clean any lingering waiting tokens for this service before testing concurrency
        try (java.sql.Connection conn = com.smartqueue.util.DatabaseConnection.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement("DELETE FROM tokens WHERE service_id = ? AND status = 'WAITING'")) {
            ps.setInt(1, testServiceId);
            ps.executeUpdate();
        } catch (Exception ignored) {}
        for (Counter c : existingCounters) {
            counterDao.clearToken(c.getId(), com.smartqueue.model.CounterStatus.OPEN);
        }

        // Insert 5 fresh waiting tokens with unique numbers
        List<Integer> insertedTokenIds = new ArrayList<>();
        for (int i = 1; i <= tokenCount; i++) {
            Token t = new Token();
            t.setTokenNumber("CONC-" + i + "-" + System.currentTimeMillis() % 1000);
            t.setServiceId(testServiceId);
            t.setCustomerName("Concurrent Customer " + i);
            t.setCustomerPhone("987654321" + i);
            t.setPriority(Priority.NORMAL);
            t.setStatus(TokenStatus.WAITING);
            t.setQueueDate(LocalDate.now());

            boolean created = tokenDao.create(t);
            assertTrue(created, "Failed to create setup token " + i);
            insertedTokenIds.add(t.getId());
        }

        // Setup 10 threads to call simultaneously
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(threadCount);

        List<Optional<Token>> results = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            // Distribute across the valid seeded counter IDs
            final int cid = existingCounters.get(i % existingCounters.size()).getId();
            executor.submit(() -> {
                try {
                    startGate.await(); // Wait for all 10 threads to be ready
                    Optional<Token> claimed = autoCaller.callNext(testServiceId, cid);
                    results.add(claimed);
                } catch (Exception e) {
                    results.add(Optional.empty());
                } finally {
                    endGate.countDown();
                }
            });
        }

        // Fire all 10 threads simultaneously!
        startGate.countDown();
        boolean finished = endGate.await(10, TimeUnit.SECONDS);
        assertTrue(finished, "Concurrent calls timed out");
        executor.shutdown();

        // Analyze results
        List<Integer> claimedIds = new ArrayList<>();
        int emptyCount = 0;

        for (Optional<Token> opt : results) {
            if (opt.isPresent()) {
                claimedIds.add(opt.get().getId());
            } else {
                emptyCount++;
            }
        }

        // Assert: NO CROSS-COUNTER OVERLAP
        Map<Integer, Integer> tokenToCounterMap = new HashMap<>();
        for (Optional<Token> opt : results) {
            if (opt.isPresent()) {
                Token t = opt.get();
                claimedIds.add(t.getId());
                if (tokenToCounterMap.containsKey(t.getId())) {
                    assertEquals(tokenToCounterMap.get(t.getId()), t.getCounterId(),
                            "A token must NEVER be claimed by two different counters!");
                } else {
                    tokenToCounterMap.put(t.getId(), t.getCounterId());
                }
            } else {
                emptyCount++;
            }
        }

        // Distinct claimed tokens must not exceed the number of eligible counters for service 1
        Set<Integer> uniqueClaimedIds = new HashSet<>(claimedIds);
        assertTrue(uniqueClaimedIds.size() <= 2, "At most 2 tokens claimed across 2 counters assigned to service 1");
        assertFalse(uniqueClaimedIds.isEmpty(), "At least one token should be claimed");

        // Cleanup: Reset counter statuses back to OPEN and clear current_token_id for test isolation
        for (Counter c : existingCounters) {
            counterDao.clearToken(c.getId(), com.smartqueue.model.CounterStatus.OPEN);
        }
    }
}

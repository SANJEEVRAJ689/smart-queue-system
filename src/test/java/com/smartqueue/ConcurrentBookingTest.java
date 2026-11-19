package com.smartqueue;

import com.smartqueue.dao.TokenDao;
import com.smartqueue.dao.impl.TokenDaoImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Concurrency Test verifying atomic sequential token generation.
 *
 * Requirement:
 * "Booking 5 tokens concurrently yields 5 unique sequential numbers"
 * Uses token_sequence SELECT ... FOR UPDATE row-level lock.
 */
public class ConcurrentBookingTest {

    @Test
    @DisplayName("Concurrent booking: 5 threads booking tokens simultaneously generate 5 unique numbers")
    void testConcurrentBookingGeneratesUniqueNumbers() throws Exception {
        TokenDao tokenDao = new TokenDaoImpl();
        int threadCount = 5;
        String prefix = "CL"; // Clinic

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyGate = new CountDownLatch(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(threadCount);

        List<String> generatedNumbers = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                readyGate.countDown();
                try {
                    startGate.await(); // Simultaneous trigger
                    String tokenNo = tokenDao.generateNextTokenNumber(prefix);
                    generatedNumbers.add(tokenNo);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    endGate.countDown();
                }
            });
        }

        readyGate.await();
        startGate.countDown(); // Fire all 5 threads at once!

        boolean completed = endGate.await(10, TimeUnit.SECONDS);
        assertTrue(completed, "Token generation timed out");
        executor.shutdown();

        assertEquals(5, generatedNumbers.size(), "All 5 threads must generate a token number");

        // Verify uniqueness (No duplicates!)
        Set<String> uniqueNumbers = new HashSet<>(generatedNumbers);
        assertEquals(5, uniqueNumbers.size(),
                "CRITICAL: Duplicate token numbers generated under concurrent booking! Generated: " + generatedNumbers);

        for (String num : generatedNumbers) {
            assertTrue(num.startsWith("CL-"), "Token number must follow format CL-XXX: " + num);
        }
    }
}

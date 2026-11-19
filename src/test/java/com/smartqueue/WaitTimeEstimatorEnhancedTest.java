package com.smartqueue;

import com.smartqueue.service.WaitTimeEstimator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests verifying the enhanced Wait Time Estimator formula (Phase 1):
 *
 * Formula:
 *   Wait Estimate = (remainingActiveMinutes + peopleAhead * avgServiceTime) / activeCounters
 *
 * Requirements:
 * 1. Takes into account currently SERVING tokens via remainingActiveMinutes.
 * 2. Divides total queue workload across all active (OPEN or BUSY) counters for the service.
 * 3. Enforces minimum 1 active counter.
 * 4. Returns 0 if 0 people ahead and 0 active serving time.
 * 5. Returns minimum 1 minute if anyone is serving or waiting.
 */
public class WaitTimeEstimatorEnhancedTest {

    private WaitTimeEstimator estimator;

    @BeforeEach
    public void setup() {
        estimator = new WaitTimeEstimator();
    }

    @Test
    @DisplayName("Empty queue and idle counters should yield 0 wait time")
    public void testEmptyQueueYieldsZero() {
        int wait = estimator.estimateMinutes(0.0, 0, 5.0, 1);
        assertEquals(0, wait, "Wait time should be 0 when queue is empty and no one is being served");
    }

    @Test
    @DisplayName("Customer is next in line (0 ahead) while 1 token is currently serving")
    public void testZeroAheadWithActiveServingToken() {
        // Customer just arrived; counter 1 is currently serving someone with 3.4 minutes remaining.
        // Formula: (3.4 + 0 * 5.0) / 1 = 3.4 -> rounds to 3 minutes.
        int wait = estimator.estimateMinutes(3.4, 0, 5.0, 1);
        assertEquals(3, wait, "Customer should wait for the active serving token to complete");
    }

    @Test
    @DisplayName("Single counter with 3 people ahead and 2 minutes active remaining")
    public void testSingleCounterMultipleWaiting() {
        // 3 people ahead * 5 mins avg + 2 mins remaining = 17 mins total / 1 counter = 17 mins
        int wait = estimator.estimateMinutes(2.0, 3, 5.0, 1);
        assertEquals(17, wait, "(2.0 + 3 * 5.0) / 1 = 17 minutes");
    }

    @Test
    @DisplayName("Multi-counter concurrency: Workload is divided across 2 active counters")
    public void testMultiCounterDistributionTwoCounters() {
        // 3 people ahead * 5 mins avg + 2 mins remaining = 17 mins total / 2 counters = 8.5 -> rounds to 9 mins
        int wait = estimator.estimateMinutes(2.0, 3, 5.0, 2);
        assertEquals(9, wait, "(2.0 + 3 * 5.0) / 2 = 8.5 -> 9 minutes");
    }

    @Test
    @DisplayName("Multi-counter concurrency: Workload is divided across 3 active counters")
    public void testMultiCounterDistributionThreeCounters() {
        // 3 people ahead * 5 mins avg + 2 mins remaining = 17 mins total / 3 counters = 5.67 -> rounds to 6 mins
        int wait = estimator.estimateMinutes(2.0, 3, 5.0, 3);
        assertEquals(6, wait, "(2.0 + 3 * 5.0) / 3 = 5.67 -> 6 minutes");
    }

    @Test
    @DisplayName("Counter count minimum guard: 0 active counters defaults to 1")
    public void testZeroCountersDefaultsToOne() {
        int wait = estimator.estimateMinutes(0.0, 2, 5.0, 0);
        assertEquals(10, wait, "0 counters should default to 1, yielding (0 + 2 * 5) / 1 = 10 minutes");
    }

    @Test
    @DisplayName("Historical duration average calculation with sample fallback")
    public void testHistoricalDurationAverage() {
        // Fewer than 3 samples: fallback to default (6.0)
        double avgFallback = estimator.calculateAverage(List.of(4.0, 5.0), 6);
        assertEquals(6.0, avgFallback, 0.01);

        // 3 or more samples: uses actual mean (4 + 6 + 8) / 3 = 6.0
        double avgComputed = estimator.calculateAverage(List.of(4.0, 6.0, 8.0), 10);
        assertEquals(6.0, avgComputed, 0.01);
    }
}

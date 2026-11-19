package com.smartqueue;

import com.smartqueue.service.WaitTimeEstimator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for Wait Time Estimation logic.
 */
public class WaitTimeEstimatorTest {

    private WaitTimeEstimator estimator;

    @BeforeEach
    public void setup() {
        estimator = new WaitTimeEstimator(null, null);
    }

    @Test
    public void testZeroPeopleAheadReturnsZeroWait() {
        int wait = estimator.estimateMinutes(0, Arrays.asList(5.0, 6.0, 7.0), 5);
        assertEquals(0, wait, "Wait time must be 0 if no customers are ahead.");
    }

    @Test
    public void testFallbackToDefaultWhenNoHistory() {
        // Less than 3 historical samples -> falls back to default 6 mins
        List<Double> shortHistory = Arrays.asList(4.0, 5.0);
        int wait = estimator.estimateMinutes(2, shortHistory, 6);
        // 2 people * 6 mins default = 12 mins
        assertEquals(12, wait, "Should fall back to default average when history < 3 items.");
    }

    @Test
    public void testCalculationWithSufficientHistory() {
        // 3 samples: 6.0, 8.0, 10.0 -> average = 8.0 minutes
        List<Double> history = Arrays.asList(6.0, 8.0, 10.0);
        int wait = estimator.estimateMinutes(3, history, 5);
        // 3 people * 8.0 mins = 24 mins
        assertEquals(24, wait, "Estimated wait must equal peopleAhead * historical average.");
    }

    @Test
    public void testMinimumOneMinuteIfSomeoneAhead() {
        // Extremely fast durations (e.g. 0.2 min) with 1 person ahead
        List<Double> fastHistory = Arrays.asList(0.2, 0.2, 0.2);
        int wait = estimator.estimateMinutes(1, fastHistory, 1);
        assertEquals(1, wait, "Wait time should be at least 1 minute if someone is ahead.");
    }
}

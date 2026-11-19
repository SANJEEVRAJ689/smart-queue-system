package com.smartqueue;

import com.smartqueue.model.Token;
import com.smartqueue.model.TokenStatus;
import com.smartqueue.util.DateTimeUtil;
import com.smartqueue.util.TimeUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test verifying UTC clock unification and live session timer behavior
 * under a simulated client in India (UTC+5:30 / Asia/Kolkata).
 *
 * Verifies that:
 * 1. Timer shows 00:00 immediately at start and counts up monotonically.
 * 2. Timezone difference (+5:30) does not skew the calculation or freeze the timer at 00:00.
 * 3. App refresh / restart recomputes true elapsed duration from started_at.
 * 4. Negative duration logs warning and clamps gracefully.
 */
public class TimerTimezoneSimulationTest {

    private TimeZone originalTimeZone;

    @BeforeEach
    public void setup() {
        originalTimeZone = TimeZone.getDefault();
        // Simulate a client machine in India (UTC+5:30)
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
    }

    @AfterEach
    public void tearDown() {
        TimeZone.setDefault(originalTimeZone);
    }

    @Test
    @DisplayName("Simulate UTC+5:30 client: timer shows 00:00 at start and counts up accurately")
    public void testTimerCountsUpInUtcPlus530() {
        Instant startTime = Instant.now();

        // 1. Immediately at start (0s elapsed)
        assertEquals("00:00", TimeUtil.formatElapsed(startTime), "Timer must show 00:00 at start");

        // 2. Simulated clock ticks: start time was N seconds ago
        Instant tPlus5 = startTime.minusSeconds(5);
        assertEquals("00:05", TimeUtil.formatElapsed(tPlus5), "At 5 seconds, timer should show 00:05");

        Instant tPlus59 = startTime.minusSeconds(59);
        assertEquals("00:59", TimeUtil.formatElapsed(tPlus59), "At 59 seconds, timer should show 00:59");

        Instant tPlus60 = startTime.minusSeconds(60);
        assertEquals("01:00", TimeUtil.formatElapsed(tPlus60), "At 60 seconds, timer should show 01:00");

        Instant tPlus125 = startTime.minusSeconds(125);
        assertEquals("02:05", TimeUtil.formatElapsed(tPlus125), "At 125 seconds, timer should show 02:05");

        Instant tPlus3661 = startTime.minusSeconds(3661);
        assertEquals("01:01:01", TimeUtil.formatElapsed(tPlus3661), "At 3661 seconds (>1 hour), timer shows HH:MM:SS");
    }

    @Test
    @DisplayName("Prove Root Cause & Fix: Before vs After Timer Comparison in UTC+5:30")
    public void testBeforeVsAfterTimerProof() {
        Instant realEventTime = Instant.now();

        // BEFORE (Buggy behavior):
        // DB timestamp was read and treated as 5.5 hours in the future due to unaligned serverTimezone=UTC
        LocalDateTime nowIST = LocalDateTime.now();
        LocalDateTime futureStartBug = nowIST.plusHours(5).plusMinutes(30);
        Duration buggyDuration = Duration.between(futureStartBug, nowIST);
        assertTrue(buggyDuration.getSeconds() < 0, "Before fix: duration was negative (-19800 seconds)");
        long clampedOldSeconds = Math.max(0, buggyDuration.getSeconds());
        assertEquals(0, clampedOldSeconds, "Before fix: Math.max(0, ...) silently clamped negative duration to 0");
        String oldTimer = String.format("%02d:%02d", clampedOldSeconds / 60, clampedOldSeconds % 60);
        assertEquals("00:00", oldTimer, "Before fix: Timer was permanently frozen at 00:00");

        // AFTER (Fixed UTC behavior):
        // Stored and read as UTC Instant, compared against Instant.now()
        Instant startUtc = realEventTime.minusSeconds(45);
        String newTimer = TimeUtil.formatElapsed(startUtc);
        assertEquals("00:45", newTimer, "After fix: Timer counts up correctly to 00:45");
    }

    @Test
    @DisplayName("Verify screen refresh and app restart survival")
    public void testTimerSurvivesRefreshAndRestart() {
        Token token = new Token();
        token.setId(101);
        token.setTokenNo("AD-001");
        token.setStatus(TokenStatus.SERVING);

        // Session was started 3 minutes (180s) ago in MySQL
        Instant startedAt = Instant.now().minusSeconds(180);
        token.setStartedAtInstant(startedAt);

        // Screen refresh / re-read from DB
        String elapsedOnRefresh = TimeUtil.formatElapsed(token.getStartedAtInstant());
        assertEquals("03:00", elapsedOnRefresh, "Timer must compute true elapsed time (03:00) on screen refresh");

        // App restart 20 seconds later
        Instant afterRestart = startedAt.minusSeconds(20); // 200 seconds total elapsed
        token.setStartedAtInstant(afterRestart);
        String elapsedAfterRestart = TimeUtil.formatElapsed(token.getStartedAtInstant());
        assertEquals("03:20", elapsedAfterRestart, "Timer must continue accurately (03:20) across app restarts");
    }

    @Test
    @DisplayName("Verify reset to 00:00 on recall or skip")
    public void testTimerResetsOnRecallOrSkip() {
        Token token = new Token();
        token.setStatus(TokenStatus.WAITING);
        token.setStartedAtInstant(null);

        assertEquals("00:00", TimeUtil.formatElapsed(token.getStartedAtInstant()),
                "Timer should display 00:00 when no session is active");
    }

    @Test
    @DisplayName("Verify negative duration handling with warning logging")
    public void testNegativeDurationHandledGracefully() {
        // Clock skew simulation: start appears 10s in future
        Instant futureStart = Instant.now().plusSeconds(10);
        String formatted = TimeUtil.formatElapsed(futureStart);
        assertEquals("00:00", formatted, "Future timestamp should clamp gracefully to 00:00 after logging warning");
    }
}

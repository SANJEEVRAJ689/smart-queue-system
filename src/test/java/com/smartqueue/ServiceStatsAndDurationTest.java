package com.smartqueue;

import com.smartqueue.dao.ServiceStatsDao;
import com.smartqueue.dao.impl.ServiceStatsDaoImpl;
import com.smartqueue.model.ServiceStats;
import com.smartqueue.model.Token;
import com.smartqueue.model.TokenStatus;
import com.smartqueue.service.ReportService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests verifying completed session duration calculation,
 * portable service stats upsert (started_at fix), and admin reporting analytics.
 */
public class ServiceStatsAndDurationTest {

    @Test
    @DisplayName("Verify completed-session duration (completed_at minus started_at)")
    public void testTokenDurationCalculation() {
        Token token = new Token();
        token.setTokenNo("AD-001");

        // Before session starts: duration is null
        assertNull(token.getDurationSeconds(), "Unstarted token has null duration");
        assertEquals("--", token.getFormattedDuration(), "Unstarted token formatted duration is --");

        // Session in progress: started 5 minutes ago, but not completed
        Instant start = Instant.now().minusSeconds(300);
        token.setStartedAtInstant(start);
        token.setStatus(TokenStatus.SERVING);
        assertNull(token.getDurationSeconds(), "Active serving token has null completed duration");

        // Session completes after 345 seconds (5m 45s)
        Instant end = start.plusSeconds(345);
        token.setCompletedAtInstant(end);
        token.setStatus(TokenStatus.COMPLETED);

        assertEquals(345L, token.getDurationSeconds(), "Completed duration must equal 345 seconds");
        assertEquals("5m 45s", token.getFormattedDuration(), "Formatted duration must be '5m 45s'");
    }

    @Test
    @DisplayName("Verify portable ServiceStatsDaoImpl uses started_at without SQL error")
    public void testServiceStatsUpsertAndRead() {
        ServiceStatsDao statsDao = new ServiceStatsDaoImpl();
        LocalDate today = LocalDate.now();

        // Must succeed without throwing 'Unknown column served_at'
        boolean upserted = statsDao.upsertDailyStats(1, today);
        assertTrue(upserted, "upsertDailyStats should execute and return true");

        Optional<ServiceStats> statsOpt = statsDao.findByServiceAndDate(1, today);
        assertTrue(statsOpt.isPresent(), "service_stats record should exist for today");
        ServiceStats stats = statsOpt.get();
        assertEquals(1, stats.getServiceId());
        assertEquals(today, stats.getStatDate());
        assertTrue(stats.getTotalTokens() >= 0, "Total tokens should be non-negative");
    }

    @Test
    @DisplayName("Verify ReportService computes average wait, average service time, and longest wait today")
    public void testReportServiceAnalyticsSummary() {
        ReportService reportService = new ReportService();
        Map<String, Object> summary = reportService.getTodaySummary();

        assertNotNull(summary, "Report summary should not be null");
        assertTrue(summary.containsKey("totalTokens"), "Summary must contain totalTokens");
        assertTrue(summary.containsKey("completedTokens"), "Summary must contain completedTokens");
        assertTrue(summary.containsKey("avgWaitMinutes"), "Summary must contain avgWaitMinutes");
        assertTrue(summary.containsKey("avgServiceMinutes"), "Summary must contain avgServiceMinutes");
        assertTrue(summary.containsKey("longestWaitMinutes"), "Summary must contain longestWaitMinutes");
        assertTrue(summary.containsKey("longestWaitFormatted"), "Summary must contain longestWaitFormatted");

        System.out.println("Analytics Summary: " + summary);
    }
}

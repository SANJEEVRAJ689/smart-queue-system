package com.smartqueue.service;

import com.smartqueue.dao.ServiceDao;
import com.smartqueue.dao.ServiceStatsDao;
import com.smartqueue.dao.TokenDao;
import com.smartqueue.dao.impl.ServiceDaoImpl;
import com.smartqueue.dao.impl.ServiceStatsDaoImpl;
import com.smartqueue.dao.impl.TokenDaoImpl;
import com.smartqueue.model.ServiceEntity;
import com.smartqueue.model.ServiceStats;
import com.smartqueue.model.Token;
import com.smartqueue.util.DateTimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for analytical reporting, chart metrics aggregation, and CSV export.
 */
public class ReportService {
    private static final Logger logger = LoggerFactory.getLogger(ReportService.class);

    private final TokenDao tokenDao;
    private final ServiceDao serviceDao;
    private final ServiceStatsDao serviceStatsDao;

    public ReportService() {
        this.tokenDao = new TokenDaoImpl();
        this.serviceDao = new ServiceDaoImpl();
        this.serviceStatsDao = new ServiceStatsDaoImpl();
    }

    public ReportService(TokenDao tokenDao, ServiceDao serviceDao, ServiceStatsDao serviceStatsDao) {
        this.tokenDao = tokenDao;
        this.serviceDao = serviceDao;
        this.serviceStatsDao = serviceStatsDao;
    }

    /**
     * Aggregates summary statistics for today.
     */
    public Map<String, Object> getTodaySummary() {
        Map<String, Object> summary = new HashMap<>();
        List<Token> todayTokens = tokenDao.findTokensByDate(LocalDate.now());

        int total = todayTokens.size();
        int completed = 0;
        int skipped = 0;
        int waiting = 0;
        int serving = 0;

        long totalWaitSeconds = 0;
        int waitCount = 0;
        long longestWaitSeconds = 0;

        long totalServiceSeconds = 0;
        int serviceCount = 0;
        long longestServiceSeconds = 0;

        for (Token t : todayTokens) {
            switch (t.getStatus()) {
                case COMPLETED -> {
                    completed++;
                    if (t.getCreatedAtInstant() != null && t.getCalledAtInstant() != null) {
                        long w = java.time.Duration.between(t.getCreatedAtInstant(), t.getCalledAtInstant()).getSeconds();
                        if (w >= 0) {
                            totalWaitSeconds += w;
                            waitCount++;
                            if (w > longestWaitSeconds) longestWaitSeconds = w;
                        }
                    } else if (t.getCreatedAt() != null && t.getCalledAt() != null) {
                        long w = java.time.Duration.between(t.getCreatedAt(), t.getCalledAt()).getSeconds();
                        if (w >= 0) {
                            totalWaitSeconds += w;
                            waitCount++;
                            if (w > longestWaitSeconds) longestWaitSeconds = w;
                        }
                    }

                    if (t.getStartedAtInstant() != null && t.getCompletedAtInstant() != null) {
                        long s = java.time.Duration.between(t.getStartedAtInstant(), t.getCompletedAtInstant()).getSeconds();
                        if (s >= 0) {
                            totalServiceSeconds += s;
                            serviceCount++;
                            if (s > longestServiceSeconds) longestServiceSeconds = s;
                        }
                    } else if (t.getServedAt() != null && t.getCompletedAt() != null) {
                        long s = java.time.Duration.between(t.getServedAt(), t.getCompletedAt()).getSeconds();
                        if (s >= 0) {
                            totalServiceSeconds += s;
                            serviceCount++;
                            if (s > longestServiceSeconds) longestServiceSeconds = s;
                        }
                    }
                }
                case SKIPPED -> skipped++;
                case WAITING -> waiting++;
                case CALLED, SERVING -> serving++;
                default -> {}
            }
        }

        double avgWaitMins = waitCount > 0 ? (totalWaitSeconds / (double) waitCount) / 60.0 : 0.0;
        double avgServiceMins = serviceCount > 0 ? (totalServiceSeconds / (double) serviceCount) / 60.0 : 0.0;
        double longestWaitMins = longestWaitSeconds / 60.0;
        double longestServiceMins = longestServiceSeconds / 60.0;
        double noShowRate = total > 0 ? ((double) skipped / total) * 100.0 : 0.0;

        summary.put("totalTokens", total);
        summary.put("completedTokens", completed);
        summary.put("skippedTokens", skipped);
        summary.put("waitingTokens", waiting);
        summary.put("servingTokens", serving);
        summary.put("avgWaitMinutes", Math.round(avgWaitMins * 10.0) / 10.0);
        summary.put("avgServiceMinutes", Math.round(avgServiceMins * 10.0) / 10.0);
        summary.put("longestWaitMinutes", Math.round(longestWaitMins * 10.0) / 10.0);
        summary.put("longestWaitFormatted", DateTimeUtil.formatDuration((int) longestWaitSeconds));
        summary.put("longestServiceMinutes", Math.round(longestServiceMins * 10.0) / 10.0);
        summary.put("longestServiceFormatted", DateTimeUtil.formatDuration((int) longestServiceSeconds));
        summary.put("noShowRate", Math.round(noShowRate * 10.0) / 10.0);

        return summary;
    }

    /**
     * Exports detailed token activity for a date to a CSV file.
     */
    public boolean exportTokensToCsv(File targetFile, LocalDate date) {
        if (targetFile == null || date == null) return false;

        List<Token> tokens = tokenDao.findTokensByDate(date);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(targetFile))) {
            // Write CSV header
            writer.write("Token Number,Service,Customer Name,Phone,Priority,Status,Counter,Created Time,Called Time,Served Time,Completed Time,Wait Time,Service Duration");
            writer.newLine();

            for (Token t : tokens) {
                String waitDuration = "--";
                if (t.getCreatedAt() != null && t.getCalledAt() != null) {
                    long sec = java.time.Duration.between(t.getCreatedAt(), t.getCalledAt()).getSeconds();
                    waitDuration = DateTimeUtil.formatDuration((int) Math.max(0, sec));
                }

                String servDuration = "--";
                if (t.getServedAt() != null && t.getCompletedAt() != null) {
                    long sec = java.time.Duration.between(t.getServedAt(), t.getCompletedAt()).getSeconds();
                    servDuration = DateTimeUtil.formatDuration((int) Math.max(0, sec));
                }

                String line = String.format("%s,\"%s\",\"%s\",%s,%s,%s,%s,\"%s\",\"%s\",\"%s\",\"%s\",%s,%s",
                        escapeCsv(t.getTokenNumber()),
                        escapeCsv(t.getServiceName() != null ? t.getServiceName() : "Service " + t.getServiceId()),
                        escapeCsv(t.getCustomerName()),
                        escapeCsv(t.getCustomerPhone()),
                        t.isPriority() ? "YES" : "NO",
                        t.getStatus().name(),
                        t.getCounterNumber() != null ? "Counter " + t.getCounterNumber() : "--",
                        DateTimeUtil.formatDateTime(t.getCreatedAt()),
                        DateTimeUtil.formatDateTime(t.getCalledAt()),
                        DateTimeUtil.formatDateTime(t.getServedAt()),
                        DateTimeUtil.formatDateTime(t.getCompletedAt()),
                        waitDuration,
                        servDuration
                );
                writer.write(line);
                writer.newLine();
            }
            logger.info("Successfully exported {} tokens to CSV: {}", tokens.size(), targetFile.getAbsolutePath());
            return true;
        } catch (IOException e) {
            logger.error("Failed to export report to CSV", e);
            return false;
        }
    }

    private String escapeCsv(String str) {
        if (str == null) return "";
        return str.replace("\"", "\"\"");
    }
}

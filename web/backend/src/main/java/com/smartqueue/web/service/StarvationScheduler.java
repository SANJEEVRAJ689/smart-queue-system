package com.smartqueue.web.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs every 60 seconds. Promotes NORMAL-priority WAITING tokens that have been
 * waiting longer than app.queue.promotion-minutes (default 30) to SENIOR priority.
 * Sends a WebSocket broadcast after each promotion batch.
 */
@Component
public class StarvationScheduler {

    private static final Logger log = LoggerFactory.getLogger(StarvationScheduler.class);

    private final DataSource dataSource;
    private final WebSocketService webSocketService;

    @Value("${app.queue.promotion-minutes:${app.starvation_threshold_minutes:30}}")
    private int promotionMinutes;

    public StarvationScheduler(DataSource dataSource, WebSocketService webSocketService) {
        this.dataSource = dataSource;
        this.webSocketService = webSocketService;
    }

    @Scheduled(fixedDelayString = "60000", initialDelayString = "30000")
    @Transactional
    public void promoteStarvedTokens() {
        try (Connection conn = dataSource.getConnection()) {
            // Find NORMAL WAITING tokens older than threshold
            String selectSql =
                "SELECT id, service_id FROM tokens " +
                "WHERE status = 'WAITING' AND priority = 'NORMAL' " +
                "AND created_at <= DATE_SUB(NOW(), INTERVAL ? MINUTE)";

            List<int[]> toPromote = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                ps.setInt(1, promotionMinutes);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        toPromote.add(new int[]{rs.getInt("id"), rs.getInt("service_id")});
                    }
                }
            }

            if (toPromote.isEmpty()) return;

            String updateSql = "UPDATE tokens SET priority = 'SENIOR' WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                for (int[] row : toPromote) {
                    ps.setInt(1, row[0]);
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            log.info("[StarvationScheduler] Promoted {} token(s) to SENIOR after {} min wait: {}",
                    toPromote.size(), promotionMinutes, toPromote.stream().map(r -> r[0]).toList());

            // Notify all affected service queues via WebSocket
            toPromote.stream().mapToInt(r -> r[1]).distinct().forEach(serviceId -> {
                try {
                    webSocketService.notifyQueueUpdated(serviceId);
                } catch (Exception e) {
                    log.warn("Failed to notify queue for service {}", serviceId, e);
                }
            });

        } catch (Exception e) {
            log.error("[StarvationScheduler] Error during starvation promotion", e);
        }
    }
}

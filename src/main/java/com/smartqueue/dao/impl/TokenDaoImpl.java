package com.smartqueue.dao.impl;

import com.smartqueue.dao.AuditLogDao;
import com.smartqueue.dao.CustomerDao;
import com.smartqueue.dao.TokenDao;
import com.smartqueue.dao.TokenEventDao;
import com.smartqueue.model.Customer;
import com.smartqueue.model.Priority;
import com.smartqueue.model.Token;
import com.smartqueue.model.TokenStatus;
import com.smartqueue.util.AppConfig;
import com.smartqueue.util.DatabaseConnection;
import com.smartqueue.util.TrackingCodeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC Implementation of TokenDao with Concurrency & Race-Condition Controls.
 */
public class TokenDaoImpl implements TokenDao {
    private static final Logger logger = LoggerFactory.getLogger(TokenDaoImpl.class);

    private final CustomerDao customerDao = new CustomerDaoImpl();
    private final TokenEventDao tokenEventDao = new TokenEventDaoImpl();
    private final AuditLogDao auditLogDao = new AuditLogDaoImpl();

    private static final String BASE_SELECT =
            "SELECT t.id, t.token_no, t.service_id, t.customer_name, t.phone, " +
            "       t.priority, t.status, t.counter_id, " +
            "       t.customer_id, t.tracking_code, t.duration_seconds, t.recall_count, " +
            "       t.created_at, t.called_at, t.started_at, t.completed_at, t.queue_date, " +
            "       s.name AS service_name, c.name AS counter_name " +
            "FROM tokens t " +
            "LEFT JOIN services s ON t.service_id = s.id " +
            "LEFT JOIN counters c ON t.counter_id = c.id ";

    @Override
    public Optional<Token> findById(int id) {
        String sql = BASE_SELECT + "WHERE t.id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToToken(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding token by id: {}", id, e);
        }
        return Optional.empty();
    }

    @Override
    public Optional<Token> findByTrackingCode(String trackingCode) {
        String sql = BASE_SELECT + "WHERE t.tracking_code = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, trackingCode);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToToken(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding token by tracking code: {}", trackingCode, e);
        }
        return Optional.empty();
    }

    @Override
    public Optional<Token> findByTokenNoAndPhone(String tokenNo, String phone, LocalDate queueDate) {
        String sql = BASE_SELECT + "WHERE t.token_no = ? AND t.phone = ? AND t.queue_date = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tokenNo.trim().toUpperCase());
            ps.setString(2, phone.trim());
            ps.setDate(3, Date.valueOf(queueDate));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToToken(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding token by number and phone", e);
        }
        return Optional.empty();
    }

    @Override
    public boolean create(Token token) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return create(conn, token);
        } catch (SQLException e) {
            logger.error("Error creating token: {}", token.getTokenNo(), e);
            return false;
        }
    }

    @Override
    public boolean create(Connection conn, Token token) throws SQLException {
        if (token.getTrackingCode() == null || token.getTrackingCode().isBlank()) {
            token.setTrackingCode(TrackingCodeUtil.generate());
        }
        if (token.getCustomerId() == null && token.getPhone() != null && !token.getPhone().isBlank()) {
            try {
                Customer c = customerDao.findOrCreate(conn, token.getCustomerName(), token.getPhone(), null);
                token.setCustomerId(c.getId());
            } catch (Exception e) {
                logger.warn("Could not link customer profile: {}", e.getMessage());
            }
        }

        String sql = "INSERT INTO tokens (token_no, service_id, customer_name, phone, " +
                     "                    priority, status, counter_id, queue_date, created_at, customer_id, tracking_code) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, token.getTokenNo());
            ps.setInt(2, token.getServiceId());
            ps.setString(3, token.getCustomerName());
            ps.setString(4, token.getPhone());
            ps.setString(5, token.getPriority().name());
            ps.setString(6, token.getStatus().name());
            if (token.getCounterId() != null) {
                ps.setInt(7, token.getCounterId());
            } else {
                ps.setNull(7, Types.INTEGER);
            }
            ps.setDate(8, Date.valueOf(token.getQueueDate()));

            Instant createdInstant = token.getCreatedAtInstant() != null ? token.getCreatedAtInstant() : Instant.now();
            token.setCreatedAtInstant(createdInstant);
            ps.setTimestamp(9, Timestamp.from(createdInstant));

            if (token.getCustomerId() != null) {
                ps.setInt(10, token.getCustomerId());
            } else {
                ps.setNull(10, Types.INTEGER);
            }
            ps.setString(11, token.getTrackingCode());

            int affected = ps.executeUpdate();
            if (affected > 0) {
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        token.setId(keys.getInt(1));
                    }
                }
                tokenEventDao.recordEvent(conn, token.getId(), null, TokenStatus.WAITING.name(), null, null);
                return true;
            }
        }
        return false;
    }

    /**
     * Atomically locks and claims the next waiting token inside an active transaction.
     * Prevents race conditions using SELECT ... FOR UPDATE.
     * Prioritizes EMERGENCY (0) -> SENIOR or NORMAL waited >= threshold (1) -> NORMAL (2).
     */
    @Override
    public Optional<Token> callNextToken(int serviceId, int counterId) {
        return callNextTokenForCounter(counterId);
    }

    @Override
    public Optional<Token> callNextTokenForCounter(int counterId) {
        int threshold = AppConfig.getStarvationThresholdMinutes();
        int maxRetries = 3;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try (Connection conn = DatabaseConnection.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    Optional<Token> result = claimNextTokenInternal(conn, counterId, threshold);
                    conn.commit();
                    return result;
                } catch (SQLException ex) {
                    conn.rollback();
                    if ((ex.getErrorCode() == 1213 || "40001".equals(ex.getSQLState())) && attempt < maxRetries) {
                        logger.warn("Deadlock encountered on counter {}, retrying attempt {}/{}", counterId, attempt, maxRetries);
                        try {
                            Thread.sleep(15L * attempt);
                        } catch (InterruptedException ignored) {}
                        continue;
                    }
                    throw ex;
                } finally {
                    conn.setAutoCommit(true);
                }
            } catch (SQLException e) {
                if (attempt == maxRetries) {
                    logger.error("Error in callNextTokenForCounter for counter {}", counterId, e);
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<Token> claimNextToken(Connection conn, int serviceId, int counterId, int starvationThresholdMinutes) throws SQLException {
        return claimNextTokenInternal(conn, counterId, starvationThresholdMinutes);
    }

    @Override
    public String generateNextTokenNumber(String prefix) {
        String cleanPrefix = (prefix != null ? prefix.trim().toUpperCase() : "AD");
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int serviceId = 1;
                String findServiceSql = "SELECT id FROM services WHERE prefix = ?";
                try (PreparedStatement ps = conn.prepareStatement(findServiceSql)) {
                    ps.setString(1, cleanPrefix);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) serviceId = rs.getInt("id");
                    }
                }
                com.smartqueue.dao.TokenSequenceDao seqDao = new com.smartqueue.dao.impl.TokenSequenceDaoImpl();
                int nextSeq = seqDao.getNextSequenceNumber(conn, serviceId, LocalDate.now());
                conn.commit();
                return String.format("%s-%03d", cleanPrefix, nextSeq);
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            logger.error("Error generating token number for prefix {}", prefix, e);
            return String.format("%s-%03d", cleanPrefix, 1);
        }
    }

    /**
     * Atomically claims the next waiting token for the given counter in an active transaction.
     * Enforces:
     * 1. Lock the counter row (SELECT ... FOR UPDATE).
     * 2. Active token check: if a CALLED or SERVING token is already assigned to this counter, returns it harmlessly.
     * 3. Selection rule: EMERGENCY -> SENIOR -> (NORMAL waited >= threshold) -> NORMAL, oldest first.
     * 4. Updates counter to BUSY and token to CALLED in the SAME transaction.
     */
    public Optional<Token> claimNextTokenInternal(Connection conn, int counterId, int starvationThresholdMinutes) throws SQLException {
        // 1. Lock counter row
        String lockCounterSql = "SELECT id, service_id, status FROM counters WHERE id = ? FOR UPDATE";
        int serviceId = -1;
        String counterStatus = null;
        try (PreparedStatement ps = conn.prepareStatement(lockCounterSql)) {
            ps.setInt(1, counterId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    serviceId = rs.getInt("service_id");
                    counterStatus = rs.getString("status");
                }
            }
        }

        if (serviceId == -1 || "CLOSED".equalsIgnoreCase(counterStatus)) {
            logger.warn("Counter {} not found or CLOSED. Cannot call next token.", counterId);
            return Optional.empty();
        }

        // 2. Check if this counter already has an active CALLED or SERVING token
        String checkActiveSql = "SELECT id FROM tokens WHERE counter_id = ? AND status IN ('CALLED', 'SERVING') LIMIT 1 FOR UPDATE";
        Integer activeTokenId = null;
        try (PreparedStatement ps = conn.prepareStatement(checkActiveSql)) {
            ps.setInt(1, counterId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    activeTokenId = rs.getInt("id");
                }
            }
        }

        if (activeTokenId != null) {
            logger.info("Counter {} already has active token {}. Returning existing active token without claiming new one.",
                    counterId, activeTokenId);
            return findByIdWithConnection(conn, activeTokenId);
        }

        // 3. Selection rule: EMERGENCY -> SENIOR -> (NORMAL waited >= threshold) -> NORMAL, oldest first
        String selectWaitingSql =
                "SELECT id FROM tokens " +
                "WHERE service_id = ? AND status = 'WAITING' " +
                "ORDER BY " +
                "  CASE " +
                "    WHEN priority = 'EMERGENCY' THEN 0 " +
                "    WHEN priority = 'SENIOR' THEN 1 " +
                "    WHEN priority = 'NORMAL' AND created_at <= DATE_SUB(NOW(), INTERVAL ? MINUTE) THEN 1 " +
                "    ELSE 2 " +
                "  END ASC, " +
                "  created_at ASC " +
                "LIMIT 1 FOR UPDATE SKIP LOCKED";

        int claimedId = -1;
        try (PreparedStatement lockPs = conn.prepareStatement(selectWaitingSql)) {
            lockPs.setInt(1, serviceId);
            lockPs.setInt(2, starvationThresholdMinutes);
            try (ResultSet rs = lockPs.executeQuery()) {
                if (rs.next()) {
                    claimedId = rs.getInt("id");
                }
            }
        }

        if (claimedId <= 0) {
            return Optional.empty(); // Queue is empty for this service
        }

        // 4. Update claimed token to CALLED
        String updateTokenSql =
                "UPDATE tokens " +
                "SET status = 'CALLED', counter_id = ?, called_at = ? " +
                "WHERE id = ? AND status = 'WAITING'";

        try (PreparedStatement updatePs = conn.prepareStatement(updateTokenSql)) {
            updatePs.setInt(1, counterId);
            updatePs.setTimestamp(2, Timestamp.from(Instant.now()));
            updatePs.setInt(3, claimedId);
            int updated = updatePs.executeUpdate();
            if (updated == 0) {
                return Optional.empty();
            }
        }

        // 5. Update counter status to BUSY and set current_token_id in the SAME transaction
        String updateCounterSql = "UPDATE counters SET status = 'BUSY', current_token_id = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(updateCounterSql)) {
            ps.setInt(1, claimedId);
            ps.setInt(2, counterId);
            ps.executeUpdate();
        }

        // 6. Record state transition event in token_events
        tokenEventDao.recordEvent(conn, claimedId, "WAITING", "CALLED", null, counterId);

        // 7. Audit log in same transaction
        auditLogDao.log(conn, null, "CALL_NEXT", claimedId);

        return findByIdWithConnection(conn, claimedId);
    }

    private Optional<Token> findByIdWithConnection(Connection conn, int id) throws SQLException {
        String sql = BASE_SELECT + "WHERE t.id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToToken(rs));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean updateStatus(int tokenId, TokenStatus newStatus) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return updateStatus(conn, tokenId, newStatus);
        } catch (SQLException e) {
            logger.error("Error updating status for token {}", tokenId, e);
            return false;
        }
    }

    @Override
    public boolean updateStatus(Connection conn, int tokenId, TokenStatus newStatus) throws SQLException {
        Optional<Token> curOpt = findByIdWithConnection(conn, tokenId);
        String fromStatus = curOpt.map(t -> t.getStatus().name()).orElse(null);
        Integer curCounterId = curOpt.map(Token::getCounterId).orElse(null);

        Instant now = Instant.now();
        StringBuilder sql = new StringBuilder("UPDATE tokens SET status = ? ");
        boolean hasTimestamp = false;
        Long durationSec = null;

        if (newStatus == TokenStatus.CALLED) {
            sql.append(", called_at = ? ");
            hasTimestamp = true;
        } else if (newStatus == TokenStatus.SERVING) {
            sql.append(", started_at = ? ");
            hasTimestamp = true;
        } else if (newStatus == TokenStatus.COMPLETED) {
            sql.append(", completed_at = ?, duration_seconds = ? ");
            hasTimestamp = true;
            if (curOpt.isPresent() && curOpt.get().getStartedAtInstant() != null) {
                durationSec = Math.max(0L, Duration.between(curOpt.get().getStartedAtInstant(), now).getSeconds());
            }
        } else if (newStatus == TokenStatus.SKIPPED || newStatus == TokenStatus.CANCELLED) {
            sql.append(", completed_at = ? ");
            hasTimestamp = true;
        }
        sql.append("WHERE id = ?");

        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.setString(1, newStatus.name());
            int idx = 2;
            if (hasTimestamp) {
                ps.setTimestamp(idx++, Timestamp.from(now));
                if (newStatus == TokenStatus.COMPLETED) {
                    if (durationSec != null) {
                        ps.setLong(idx++, durationSec);
                    } else {
                        ps.setNull(idx++, Types.INTEGER);
                    }
                }
            }
            ps.setInt(idx, tokenId);
            int updated = ps.executeUpdate();
            if (updated > 0) {
                tokenEventDao.recordEvent(conn, tokenId, fromStatus, newStatus.name(), null, curCounterId);
                return true;
            }
            return false;
        }
    }

    @Override
    public boolean transferService(int tokenId, int newServiceId) {
        String sql = "UPDATE tokens SET service_id = ?, status = 'WAITING', counter_id = NULL, " +
                     "                 called_at = NULL, started_at = NULL WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, newServiceId);
            ps.setInt(2, tokenId);
            int updated = ps.executeUpdate();
            if (updated > 0) {
                tokenEventDao.recordEvent(tokenId, "SKIPPED", "WAITING", null, null);
                return true;
            }
            return false;
        } catch (SQLException e) {
            logger.error("Error transferring token {} to service {}", tokenId, newServiceId, e);
            return false;
        }
    }

    @Override
    public boolean recallToken(int tokenId) {
        // SKIPPED -> WAITING (recall, goes to back of normal queue with reset created_at, max 1 recall)
        String sql = "UPDATE tokens SET status = 'WAITING', recall_count = recall_count + 1, counter_id = NULL, " +
                     "                 called_at = NULL, started_at = NULL, completed_at = NULL, " +
                     "                 created_at = ? " +
                     "WHERE id = ? AND status = 'SKIPPED' AND recall_count = 0";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.from(Instant.now()));
            ps.setInt(2, tokenId);
            int updated = ps.executeUpdate();
            if (updated > 0) {
                tokenEventDao.recordEvent(tokenId, "SKIPPED", "WAITING", null, null);
                return true;
            }
            return false;
        } catch (SQLException e) {
            logger.error("Error recalling token {}", tokenId, e);
            return false;
        }
    }

    @Override
    public List<Token> findWaitingTokens(int serviceId) {
        int threshold = AppConfig.getStarvationThresholdMinutes();
        List<Token> list = new ArrayList<>();
        String sql = BASE_SELECT +
                     "WHERE t.service_id = ? AND t.status = 'WAITING' " +
                     "ORDER BY " +
                     "  CASE " +
                     "    WHEN t.priority = 'EMERGENCY' THEN 0 " +
                     "    WHEN t.priority = 'SENIOR' THEN 1 " +
                     "    WHEN t.priority = 'NORMAL' AND t.created_at <= DATE_SUB(NOW(), INTERVAL ? MINUTE) THEN 1 " +
                     "    ELSE 2 " +
                     "  END ASC, " +
                     "  t.created_at ASC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, serviceId);
            ps.setInt(2, threshold);
            try (ResultSet rs = ps.executeQuery()) {
                int pos = 1;
                while (rs.next()) {
                    Token t = mapResultSetToToken(rs);
                    t.setQueuePosition(pos++);
                    list.add(t);
                }
            }
        } catch (SQLException e) {
            logger.error("Error fetching waiting tokens for service: {}", serviceId, e);
        }
        return list;
    }

    @Override
    public List<Token> findAllWaitingTokens() {
        int threshold = AppConfig.getStarvationThresholdMinutes();
        List<Token> list = new ArrayList<>();
        String sql = BASE_SELECT +
                     "WHERE t.status = 'WAITING' " +
                     "ORDER BY " +
                     "  CASE " +
                     "    WHEN t.priority = 'EMERGENCY' THEN 0 " +
                     "    WHEN t.priority = 'SENIOR' THEN 1 " +
                     "    WHEN t.priority = 'NORMAL' AND t.created_at <= DATE_SUB(NOW(), INTERVAL ? MINUTE) THEN 1 " +
                     "    ELSE 2 " +
                     "  END ASC, " +
                     "  t.created_at ASC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, threshold);
            try (ResultSet rs = ps.executeQuery()) {
                int pos = 1;
                while (rs.next()) {
                    Token t = mapResultSetToToken(rs);
                    t.setQueuePosition(pos++);
                    list.add(t);
                }
            }
        } catch (SQLException e) {
            logger.error("Error fetching all waiting tokens", e);
        }
        return list;
    }

    @Override
    public List<Token> findCurrentlyServingTokens() {
        List<Token> list = new ArrayList<>();
        String sql = BASE_SELECT +
                     "WHERE t.status IN ('CALLED', 'SERVING') " +
                     "ORDER BY t.called_at DESC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapResultSetToToken(rs));
            }
        } catch (SQLException e) {
            logger.error("Error fetching currently serving tokens", e);
        }
        return list;
    }

    @Override
    public List<Token> findNextWaitingTokensForDisplay(int limit) {
        int threshold = AppConfig.getStarvationThresholdMinutes();
        List<Token> list = new ArrayList<>();
        String sql = BASE_SELECT +
                     "WHERE t.status = 'WAITING' " +
                     "ORDER BY " +
                     "  CASE " +
                     "    WHEN t.priority = 'EMERGENCY' THEN 0 " +
                     "    WHEN t.priority = 'SENIOR' THEN 1 " +
                     "    WHEN t.priority = 'NORMAL' AND t.created_at <= DATE_SUB(NOW(), INTERVAL ? MINUTE) THEN 1 " +
                     "    ELSE 2 " +
                     "  END ASC, " +
                     "  t.created_at ASC " +
                     "LIMIT ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, threshold);
            ps.setInt(2, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                int pos = 1;
                while (rs.next()) {
                    Token t = mapResultSetToToken(rs);
                    t.setQueuePosition(pos++);
                    list.add(t);
                }
            }
        } catch (SQLException e) {
            logger.error("Error fetching display queue tokens", e);
        }
        return list;
    }

    @Override
    public List<Token> findOverdueCalledTokens(int timeoutSeconds) {
        List<Token> list = new ArrayList<>();
        // Finds CALLED tokens where called_at was more than timeoutSeconds ago
        String sql = BASE_SELECT +
                     "WHERE t.status = 'CALLED' AND t.called_at IS NOT NULL " +
                     "  AND TIMESTAMPDIFF(SECOND, t.called_at, CURRENT_TIMESTAMP) >= ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, timeoutSeconds);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToToken(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error fetching overdue tokens", e);
        }
        return list;
    }

    @Override
    public List<Token> findCompletedTokensForCounterToday(int counterId) {
        List<Token> list = new ArrayList<>();
        String sql = BASE_SELECT +
                     "WHERE t.counter_id = ? AND t.status = 'COMPLETED' " +
                     "  AND t.queue_date = CURRENT_DATE " +
                     "ORDER BY t.completed_at DESC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, counterId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToToken(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error fetching completed tokens for counter {}", counterId, e);
        }
        return list;
    }

    @Override
    public int countWaitingAhead(int tokenId, int serviceId) {
        return countWaitingAhead(tokenId, serviceId, AppConfig.getStarvationThresholdMinutes());
    }

    @Override
    public int countWaitingAhead(int tokenId, int serviceId, int starvationThresholdMinutes) {
        List<Token> waiting = findWaitingTokens(serviceId);
        int pos = 0;
        for (Token t : waiting) {
            if (t.getId() == tokenId) {
                return pos;
            }
            pos++;
        }
        return waiting.size();
    }

    @Override
    public List<Double> getRecentServiceDurationsMinutes(int serviceId, int limitN) {
        List<Double> durations = new ArrayList<>();
        String sql = "SELECT started_at, completed_at FROM tokens " +
                     "WHERE service_id = ? AND status = 'COMPLETED' " +
                     "  AND started_at IS NOT NULL AND completed_at IS NOT NULL " +
                     "  AND TIMESTAMPDIFF(SECOND, started_at, completed_at) >= 3 " +
                     "ORDER BY completed_at DESC LIMIT ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, serviceId);
            ps.setInt(2, Math.max(1, limitN));

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Timestamp start = rs.getTimestamp("started_at");
                    Timestamp end = rs.getTimestamp("completed_at");
                    if (start != null && end != null) {
                        long diffMillis = Duration.between(start.toInstant(), end.toInstant()).toMillis();
                        double minutes = diffMillis / 60000.0;
                        if (minutes > 0.05 && minutes < 300.0) {
                            durations.add(minutes);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Error fetching recent service durations for service {}", serviceId, e);
        }
        return durations;
    }

    @Override
    public double getRemainingActiveServiceMinutes(int serviceId, double avgServiceMinutes) {
        String sql = "SELECT started_at FROM tokens " +
                     "WHERE service_id = ? AND status = 'SERVING' AND started_at IS NOT NULL";
        double totalRemaining = 0.0;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, serviceId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Timestamp start = rs.getTimestamp("started_at");
                    if (start != null) {
                        long elapsedMillis = Duration.between(start.toInstant(), Instant.now()).toMillis();
                        double elapsedMins = elapsedMillis / 60000.0;
                        double remaining = Math.max(0.0, avgServiceMinutes - elapsedMins);
                        totalRemaining += remaining;
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Error calculating remaining active service time for service {}", serviceId, e);
        }
        return totalRemaining;
    }

    @Override
    public List<Token> findTokensByDate(LocalDate date) {
        List<Token> list = new ArrayList<>();
        String sql = BASE_SELECT + "WHERE t.queue_date = ? ORDER BY t.created_at ASC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(date));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToToken(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error fetching tokens for date {}", date, e);
        }
        return list;
    }

    private Token mapResultSetToToken(ResultSet rs) throws SQLException {
        Token t = new Token();
        t.setId(rs.getInt("id"));
        t.setTokenNo(rs.getString("token_no"));
        t.setServiceId(rs.getInt("service_id"));
        t.setCustomerName(rs.getString("customer_name"));
        t.setPhone(rs.getString("phone"));
        t.setPriority(Priority.fromString(rs.getString("priority")));
        t.setStatus(TokenStatus.fromString(rs.getString("status")));

        int counterId = rs.getInt("counter_id");
        if (!rs.wasNull()) {
            t.setCounterId(counterId);
        }

        try {
            int custId = rs.getInt("customer_id");
            if (!rs.wasNull()) t.setCustomerId(custId);
        } catch (SQLException ignored) {}

        try {
            t.setTrackingCode(rs.getString("tracking_code"));
        } catch (SQLException ignored) {}

        try {
            int dur = rs.getInt("duration_seconds");
            if (!rs.wasNull()) t.setDurationSeconds(dur);
        } catch (SQLException ignored) {}

        try {
            int rc = rs.getInt("recall_count");
            if (!rs.wasNull()) t.setRecallCount(rc);
        } catch (SQLException ignored) {}

        Timestamp tsCreated = rs.getTimestamp("created_at");
        if (tsCreated != null) {
            t.setCreatedAtInstant(tsCreated.toInstant());
        }

        Timestamp tsCalled = rs.getTimestamp("called_at");
        if (tsCalled != null) {
            t.setCalledAtInstant(tsCalled.toInstant());
        }

        Timestamp tsStarted = rs.getTimestamp("started_at");
        if (tsStarted != null) {
            t.setStartedAtInstant(tsStarted.toInstant());
        }

        Timestamp tsCompleted = rs.getTimestamp("completed_at");
        if (tsCompleted != null) {
            t.setCompletedAtInstant(tsCompleted.toInstant());
        }

        Date qDate = rs.getDate("queue_date");
        if (qDate != null) t.setQueueDate(qDate.toLocalDate());

        t.setServiceName(rs.getString("service_name"));
        t.setCounterName(rs.getString("counter_name"));
        return t;
    }
}

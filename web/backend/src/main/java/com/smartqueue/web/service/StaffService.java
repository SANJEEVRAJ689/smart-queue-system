package com.smartqueue.web.service;

import com.smartqueue.web.dto.StaffDtos.StaffTokenResponse;
import com.smartqueue.web.entity.*;
import com.smartqueue.web.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Service
public class StaffService {

    private static final Logger logger = LoggerFactory.getLogger(StaffService.class);

    private final DataSource dataSource;
    private final TokenRepository tokenRepository;
    private final CounterRepository counterRepository;
    private final ServiceRepository serviceRepository;
    private final TokenEventRepository tokenEventRepository;
    private final AuditLogRepository auditLogRepository;
    private final WebSocketService webSocketService;

    @Value("${app.starvation_threshold_minutes:30}")
    private int starvationThresholdMinutes;

    private final Map<Integer, Boolean> autoCallMap = new ConcurrentHashMap<>();
    private final ScheduledExecutorService autoCallExecutor = Executors.newSingleThreadScheduledExecutor();

    public StaffService(
            DataSource dataSource,
            TokenRepository tokenRepository,
            CounterRepository counterRepository,
            ServiceRepository serviceRepository,
            TokenEventRepository tokenEventRepository,
            AuditLogRepository auditLogRepository,
            WebSocketService webSocketService) {
        this.dataSource = dataSource;
        this.tokenRepository = tokenRepository;
        this.counterRepository = counterRepository;
        this.serviceRepository = serviceRepository;
        this.tokenEventRepository = tokenEventRepository;
        this.auditLogRepository = auditLogRepository;
        this.webSocketService = webSocketService;
    }

    public boolean isAutoCallEnabled(int counterId) {
        return autoCallMap.getOrDefault(counterId, false);
    }

    public void setAutoCallEnabled(int counterId, boolean enabled) {
        autoCallMap.put(counterId, enabled);
        if (enabled) {
            checkAndTriggerAutoCall(counterId);
        }
    }

    public void checkAndTriggerAutoCall(int counterId) {
        if (!isAutoCallEnabled(counterId)) return;
        autoCallExecutor.submit(() -> {
            try {
                Thread.sleep(100);
                callNextToken(counterId, null);
            } catch (Exception e) {
                logger.error("Error in background auto-call for counter {}", counterId, e);
            }
        });
    }

    /**
     * CANONICAL CALL PATH:
     * Atomic transaction with counter row locking and double-click harmless check.
     */
    public Optional<StaffTokenResponse> callNextToken(int counterId, Integer staffUserId) {
        for (int attempt = 1; attempt <= 3; attempt++) {
            try (Connection conn = dataSource.getConnection()) {
                boolean prevAutoCommit = conn.getAutoCommit();
                conn.setAutoCommit(false);
                try {
                    // 1. Lock counter row
                    String lockCounterSql = "SELECT id, service_id, status FROM counters WHERE id = ? FOR UPDATE";
                    int serviceId = -1;
                    String status = null;
                    try (PreparedStatement ps = conn.prepareStatement(lockCounterSql)) {
                        ps.setInt(1, counterId);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                serviceId = rs.getInt("service_id");
                                status = rs.getString("status");
                            }
                        }
                    }

                    if (serviceId == -1 || "CLOSED".equalsIgnoreCase(status)) {
                        conn.rollback();
                        return Optional.empty();
                    }

                    // 2. Active token check: if already has CALLED or SERVING token, return it harmlessly
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
                        conn.commit();
                        return tokenRepository.findById(activeTokenId).map(this::mapToStaffTokenResponse);
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
                    try (PreparedStatement ps = conn.prepareStatement(selectWaitingSql)) {
                        ps.setInt(1, serviceId);
                        ps.setInt(2, starvationThresholdMinutes);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                claimedId = rs.getInt("id");
                            }
                        }
                    }

                    if (claimedId == -1) {
                        conn.rollback();
                        return Optional.empty();
                    }

                    // 4. Update Token to CALLED and Counter to BUSY in the SAME transaction
                    String updateTokenSql = "UPDATE tokens SET status = 'CALLED', called_at = NOW(), counter_id = ? WHERE id = ?";
                    try (PreparedStatement ps = conn.prepareStatement(updateTokenSql)) {
                        ps.setInt(1, counterId);
                        ps.setInt(2, claimedId);
                        ps.executeUpdate();
                    }

                    String updateCounterSql = "UPDATE counters SET status = 'BUSY', current_token_id = ? WHERE id = ?";
                    try (PreparedStatement ps = conn.prepareStatement(updateCounterSql)) {
                        ps.setInt(1, claimedId);
                        ps.setInt(2, counterId);
                        ps.executeUpdate();
                    }

                    // 5. Record Token Event and Audit Log
                    String insertEventSql = "INSERT INTO token_events (token_id, from_status, to_status, user_id, counter_id) VALUES (?, 'WAITING', 'CALLED', ?, ?)";
                    try (PreparedStatement ps = conn.prepareStatement(insertEventSql)) {
                        ps.setInt(1, claimedId);
                        if (staffUserId != null) ps.setInt(2, staffUserId); else ps.setNull(2, java.sql.Types.INTEGER);
                        ps.setInt(3, counterId);
                        ps.executeUpdate();
                    }

                    conn.commit();

                    webSocketService.notifyQueueUpdated(serviceId);
                    Optional<TokenEntity> claimed = tokenRepository.findById(claimedId);
                    claimed.ifPresent(t -> webSocketService.notifyTokenUpdated(t.getTrackingCode(), mapToStaffTokenResponse(t)));
                    return claimed.map(this::mapToStaffTokenResponse);

                } catch (SQLException ex) {
                    conn.rollback();
                    if (ex.getErrorCode() == 1213 && attempt < 3) {
                        Thread.sleep(50L * attempt);
                        continue;
                    }
                    throw ex;
                } finally {
                    conn.setAutoCommit(prevAutoCommit);
                }
            } catch (Exception e) {
                logger.error("Error claiming next token for counter {}", counterId, e);
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    @Transactional
    public StaffTokenResponse startServing(int tokenId, Integer staffUserId) {
        TokenEntity token = tokenRepository.findById(tokenId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Token not found."));

        if (!"CALLED".equals(token.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token must be CALLED before starting service.");
        }

        token.setStatus("SERVING");
        token.setStartedAt(Instant.now());
        TokenEntity saved = tokenRepository.save(token);

        tokenEventRepository.save(new TokenEventEntity(tokenId, "CALLED", "SERVING", staffUserId, token.getCounterId()));
        webSocketService.notifyQueueUpdated(token.getServiceId());
        webSocketService.notifyTokenUpdated(token.getTrackingCode(), mapToStaffTokenResponse(saved));

        return mapToStaffTokenResponse(saved);
    }

    @Transactional
    public StaffTokenResponse completeToken(int tokenId, int counterId, Integer staffUserId) {
        TokenEntity token = tokenRepository.findById(tokenId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Token not found."));

        if (!"SERVING".equals(token.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token must be in SERVING state to complete.");
        }

        Instant now = Instant.now();
        token.setStatus("COMPLETED");
        token.setCompletedAt(now);
        if (token.getStartedAt() != null) {
            long duration = Duration.between(token.getStartedAt(), now).getSeconds();
            token.setDurationSeconds((int) Math.max(0, duration));
        }
        TokenEntity saved = tokenRepository.save(token);

        tokenEventRepository.save(new TokenEventEntity(tokenId, "SERVING", "COMPLETED", staffUserId, counterId));

        // Reset Counter to OPEN and clear current_token_id
        counterRepository.findById(counterId).ifPresent(c -> {
            c.setStatus("OPEN");
            c.setCurrentTokenId(null);
            counterRepository.save(c);
        });

        webSocketService.notifyQueueUpdated(token.getServiceId());
        webSocketService.notifyTokenUpdated(token.getTrackingCode(), mapToStaffTokenResponse(saved));

        checkAndTriggerAutoCall(counterId);
        return mapToStaffTokenResponse(saved);
    }

    @Transactional
    public StaffTokenResponse skipToken(int tokenId, int counterId, Integer staffUserId) {
        TokenEntity token = tokenRepository.findById(tokenId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Token not found."));

        if (!"CALLED".equals(token.getStatus()) && !"SERVING".equals(token.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token must be CALLED or SERVING to skip.");
        }

        String prevStatus = token.getStatus();
        token.setStatus("SKIPPED");
        token.setCompletedAt(Instant.now());
        TokenEntity saved = tokenRepository.save(token);

        tokenEventRepository.save(new TokenEventEntity(tokenId, prevStatus, "SKIPPED", staffUserId, counterId));

        // Free Counter to OPEN
        counterRepository.findById(counterId).ifPresent(c -> {
            c.setStatus("OPEN");
            c.setCurrentTokenId(null);
            counterRepository.save(c);
        });

        webSocketService.notifyQueueUpdated(token.getServiceId());
        webSocketService.notifyTokenUpdated(token.getTrackingCode(), mapToStaffTokenResponse(saved));

        checkAndTriggerAutoCall(counterId);
        return mapToStaffTokenResponse(saved);
    }

    @Transactional
    public StaffTokenResponse recallToken(int tokenId, int counterId, Integer staffUserId) {
        TokenEntity token = tokenRepository.findById(tokenId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Token not found."));

        if (!"SKIPPED".equals(token.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only SKIPPED tokens can be recalled.");
        }

        if (token.getRecallCount() != null && token.getRecallCount() > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    String.format("Token %s has already been recalled once. Maximum recall limit reached.", token.getTokenNo()));
        }

        token.setStatus("WAITING");
        token.setRecallCount((token.getRecallCount() != null ? token.getRecallCount() : 0) + 1);
        token.setCounterId(null);
        token.setCalledAt(null);
        token.setStartedAt(null);
        token.setCompletedAt(null);
        token.setCreatedAt(Instant.now()); // Re-enter at back of queue

        TokenEntity saved = tokenRepository.save(token);

        tokenEventRepository.save(new TokenEventEntity(tokenId, "SKIPPED", "WAITING", staffUserId, counterId));
        webSocketService.notifyQueueUpdated(token.getServiceId());

        return mapToStaffTokenResponse(saved);
    }

    @Transactional
    public StaffTokenResponse transferToken(int tokenId, int targetServiceId, Integer staffUserId) {
        TokenEntity token = tokenRepository.findById(tokenId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Token not found."));

        ServiceEntity targetService = serviceRepository.findById(targetServiceId)
                .filter(s -> Boolean.TRUE.equals(s.getActive()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Target service not found."));

        int oldServiceId = token.getServiceId();
        token.setServiceId(targetServiceId);
        token.setStatus("WAITING");
        token.setCounterId(null);
        token.setCalledAt(null);
        token.setStartedAt(null);

        TokenEntity saved = tokenRepository.save(token);
        tokenEventRepository.save(new TokenEventEntity(tokenId, "TRANSFER", "WAITING", staffUserId, null));

        webSocketService.notifyQueueUpdated(oldServiceId);
        webSocketService.notifyQueueUpdated(targetServiceId);

        return mapToStaffTokenResponse(saved);
    }

    @Transactional
    public boolean setCounterStatus(int counterId, String newStatus) {
        String clean = newStatus.toUpperCase();
        if (!"OPEN".equals(clean) && !"CLOSED".equals(clean)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Counter status must be OPEN or CLOSED.");
        }

        CounterEntity counter = counterRepository.findById(counterId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Counter not found."));

        counter.setStatus(clean);
        if ("CLOSED".equals(clean)) {
            counter.setCurrentTokenId(null);
        }
        counterRepository.save(counter);
        webSocketService.notifyDisplayBoard(Map.of("event", "COUNTER_STATUS_CHANGE", "counterId", counterId, "status", clean));
        return true;
    }

    public List<StaffTokenResponse> getWaitingQueue(Integer serviceId) {
        List<TokenEntity> tokens = serviceId != null ?
                tokenRepository.findWaitingTokensForService(serviceId) :
                tokenRepository.findByQueueDateOrderByCreatedAtDesc(LocalDate.now());

        return tokens.stream().map(this::mapToStaffTokenResponse).toList();
    }

    public List<StaffTokenResponse> getRecentHistory() {
        return tokenRepository.findByQueueDateOrderByCreatedAtDesc(LocalDate.now()).stream()
                .filter(t -> "COMPLETED".equals(t.getStatus()) || "SKIPPED".equals(t.getStatus()))
                .limit(30)
                .map(this::mapToStaffTokenResponse)
                .toList();
    }

    public StaffTokenResponse mapToStaffTokenResponse(TokenEntity t) {
        StaffTokenResponse resp = new StaffTokenResponse();
        resp.setId(t.getId());
        resp.setTokenNo(t.getTokenNo());
        resp.setServiceId(t.getServiceId());
        resp.setCustomerName(t.getCustomerName());
        resp.setPhone(t.getPhone());
        resp.setStatus(t.getStatus());
        resp.setPriority(t.getPriority());
        resp.setCounterId(t.getCounterId());
        resp.setRecallCount(t.getRecallCount());
        resp.setDurationSeconds(t.getDurationSeconds());

        resp.setCalledAtUtc(t.getCalledAt() != null ? t.getCalledAt().toString() : null);
        resp.setStartedAtUtc(t.getStartedAt() != null ? t.getStartedAt().toString() : null);
        resp.setCompletedAtUtc(t.getCompletedAt() != null ? t.getCompletedAt().toString() : null);
        resp.setServerTimeUtc(Instant.now().toString());

        serviceRepository.findById(t.getServiceId()).ifPresent(s -> resp.setServiceName(s.getName()));
        if (t.getCounterId() != null) {
            counterRepository.findById(t.getCounterId()).ifPresent(c -> resp.setCounterName(c.getName()));
        }

        return resp;
    }
}

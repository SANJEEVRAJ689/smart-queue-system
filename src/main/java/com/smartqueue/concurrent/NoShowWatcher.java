package com.smartqueue.concurrent;

import com.smartqueue.dao.AuditLogDao;
import com.smartqueue.dao.CounterDao;
import com.smartqueue.dao.TokenDao;
import com.smartqueue.dao.impl.AuditLogDaoImpl;
import com.smartqueue.dao.impl.CounterDaoImpl;
import com.smartqueue.dao.impl.TokenDaoImpl;
import com.smartqueue.model.AuditLog;
import com.smartqueue.model.CounterStatus;
import com.smartqueue.model.Token;
import com.smartqueue.model.TokenStatus;
import com.smartqueue.service.QueueManager;
import com.smartqueue.service.TokenService;
import com.smartqueue.util.AppConfig;
import com.smartqueue.util.DatabaseConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * NoShowWatcher periodically inspects the queue for CALLED tokens that have not
 * reported to their service desk within the configured timeout window (default: 2 minutes).
 *
 * When a customer does not show up:
 * 1. The token status is transitioned from CALLED to SKIPPED.
 * 2. The associated counter is freed back to OPEN status.
 * 3. An audit log entry is recorded.
 * 4. A queue change is triggered so the counter can auto-call the next waiting customer.
 */
public class NoShowWatcher implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(NoShowWatcher.class);

    private final TokenDao tokenDao;
    private final CounterDao counterDao;
    private final AuditLogDao auditLogDao;
    private final TokenService tokenService;

    public NoShowWatcher() {
        this(new TokenDaoImpl(), new CounterDaoImpl(), new AuditLogDaoImpl());
    }

    public NoShowWatcher(TokenDao tokenDao, CounterDao counterDao, AuditLogDao auditLogDao) {
        this.tokenDao = tokenDao;
        this.counterDao = counterDao;
        this.auditLogDao = auditLogDao;
        this.tokenService = new TokenService();
    }

    @Override
    public void run() {
        try {
            int timeoutSeconds = AppConfig.getNoShowTimeoutSeconds();
            List<Token> overdueTokens = tokenDao.findOverdueCalledTokens(timeoutSeconds);

            if (!overdueTokens.isEmpty()) {
                logger.info("NoShowWatcher found {} overdue called token(s) exceeding {}s timeout.",
                        overdueTokens.size(), timeoutSeconds);

                for (Token token : overdueTokens) {
                    processNoShow(token);
                }

                QueueManager.getInstance().triggerQueueUpdate();
            }
        } catch (Exception e) {
            logger.error("Error during NoShowWatcher execution", e);
        }
    }

    private void processNoShow(Token token) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 1. Transition token to SKIPPED
                tokenDao.updateStatus(conn, token.getId(), TokenStatus.SKIPPED);

                // 2. Free the counter and clear current token: return to OPEN (idle) within transaction
                if (token.getCounterId() != null) {
                    counterDao.clearToken(conn, token.getCounterId(), CounterStatus.OPEN);
                }

                // 3. Record audit log within transaction
                auditLogDao.log(conn, null, "NO_SHOW_TIMEOUT", token.getId());

                conn.commit();
                logger.info("Token {} marked as SKIPPED due to customer no-show (Counter {} freed).",
                        token.getTokenNumber(), token.getCounterId());

                // Apply auto-call rule: If auto-call is ON for that counter, immediately call next token
                if (token.getCounterId() != null && QueueManager.getInstance().isAutoCallEnabled(token.getCounterId())) {
                    int cid = token.getCounterId();
                    ThreadPoolManager.getInstance().submitWorkerTask(() -> tokenService.callNextToken(cid));
                }
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            logger.error("Failed to mark token {} as SKIPPED", token.getTokenNumber(), e);
        }
    }
}

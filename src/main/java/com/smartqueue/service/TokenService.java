package com.smartqueue.service;

import com.smartqueue.concurrent.ThreadPoolManager;
import com.smartqueue.dao.CounterDao;
import com.smartqueue.dao.ServiceDao;
import com.smartqueue.dao.ServiceStatsDao;
import com.smartqueue.dao.TokenDao;
import com.smartqueue.dao.impl.CounterDaoImpl;
import com.smartqueue.dao.impl.ServiceDaoImpl;
import com.smartqueue.dao.impl.ServiceStatsDaoImpl;
import com.smartqueue.dao.impl.TokenDaoImpl;
import com.smartqueue.model.Counter;
import com.smartqueue.model.CounterStatus;
import com.smartqueue.model.ServiceEntity;
import com.smartqueue.model.Token;
import com.smartqueue.model.TokenStatus;
import com.smartqueue.util.AppException;
import com.smartqueue.util.SoundUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Service managing token generation, lifecycle state machine transitions, and queue metrics.
 *
 * TOKEN STATE MACHINE RULES:
 * WAITING  -> CALLED     (by Staff or AutoCaller)
 * WAITING  -> CANCELLED  (by Customer or Admin)
 * CALLED   -> SERVING    (when customer arrives at counter)
 * CALLED   -> SKIPPED    (no-show after timeout)
 * SERVING  -> COMPLETED  (service finished)
 * SKIPPED  -> WAITING    (recall, goes to back of normal queue)
 *
 * All illegal transitions throw AppException.
 */
public class TokenService {
    private static final Logger logger = LoggerFactory.getLogger(TokenService.class);

    private final TokenDao tokenDao;
    private final ServiceDao serviceDao;
    private final CounterDao counterDao;
    private final ServiceStatsDao serviceStatsDao;
    private final WaitTimeEstimator waitTimeEstimator;

    public TokenService() {
        this.tokenDao = new TokenDaoImpl();
        this.serviceDao = new ServiceDaoImpl();
        this.counterDao = new CounterDaoImpl();
        this.serviceStatsDao = new ServiceStatsDaoImpl();
        this.waitTimeEstimator = new WaitTimeEstimator(tokenDao, serviceDao);
    }

    public TokenService(TokenDao tokenDao, ServiceDao serviceDao, CounterDao counterDao,
                        ServiceStatsDao serviceStatsDao, WaitTimeEstimator waitTimeEstimator) {
        this.tokenDao = tokenDao;
        this.serviceDao = serviceDao;
        this.counterDao = counterDao;
        this.serviceStatsDao = serviceStatsDao;
        this.waitTimeEstimator = waitTimeEstimator;
    }

    /**
     * Enforces the token state machine. Throws AppException if transition is illegal.
     */
    public void validateTransition(TokenStatus current, TokenStatus target) throws AppException {
        if (current == null || target == null) {
            throw new AppException("Current and target token status cannot be null.");
        }
        boolean allowed = false;
        switch (current) {
            case WAITING:
                allowed = (target == TokenStatus.CALLED || target == TokenStatus.CANCELLED);
                break;
            case CALLED:
                allowed = (target == TokenStatus.SERVING || target == TokenStatus.SKIPPED);
                break;
            case SERVING:
                allowed = (target == TokenStatus.COMPLETED);
                break;
            case SKIPPED:
                allowed = (target == TokenStatus.WAITING);
                break;
            case COMPLETED:
            case CANCELLED:
                allowed = false; // Terminal states
                break;
        }
        if (!allowed) {
            String errorMsg = String.format("Illegal state transition from %s to %s.", current, target);
            logger.warn(errorMsg);
            throw new AppException(errorMsg);
        }
    }

    /**
     * Books a new token from the customer kiosk.
     */
    public Optional<Token> bookToken(int serviceId, String name, String phone,
                                     boolean isPriority, String priorityReason) {
        Optional<ServiceEntity> sOpt = serviceDao.findById(serviceId);
        if (sOpt.isEmpty()) {
            logger.warn("Cannot book token: service {} not found.", serviceId);
            return Optional.empty();
        }
        ServiceEntity service = sOpt.get();

        String tokenNumber = tokenDao.generateNextTokenNumber(service.getCodePrefix());
        Token token = new Token();
        token.setTokenNumber(tokenNumber);
        token.setServiceId(serviceId);
        token.setCustomerName(name != null ? name.trim() : "Guest");
        token.setCustomerPhone(phone != null ? phone.trim() : "");
        token.setPriority(isPriority);
        token.setPriorityReason(isPriority ? priorityReason : null);
        token.setStatus(TokenStatus.WAITING);

        boolean created = tokenDao.create(token);
        if (created) {
            token.setServiceName(service.getName());
            int ahead = tokenDao.countWaitingAhead(token.getId(), serviceId);
            token.setQueuePosition(ahead + 1);
            int estWait = waitTimeEstimator.estimateForToken(token.getId(), serviceId);
            token.setEstimatedWaitMinutes(estWait);

            logger.info("New token booked: {} for {} (Position: {}, Est. Wait: {}m)",
                    tokenNumber, token.getCustomerName(), token.getQueuePosition(), estWait);

            QueueManager.getInstance().triggerQueueUpdate();
            return Optional.of(token);
        }
        return Optional.empty();
    }

    /**
     * Counter staff calls the next customer in queue.
     * Prevents race conditions via atomic DB transaction with row locking.
     */
    /**
     * CANONICAL CALL PATH: Single entry point to claim and call the next eligible token for a counter.
     * Enforces:
     * - Counter row lock (SELECT ... FOR UPDATE)
     * - Counter status check & service category restriction
     * - Active token check (returns existing CALLED/SERVING token harmlessly on double-click)
     * - Priority selection with starvation prevention threshold
     * - Atomic update of counter to BUSY and token to CALLED in one transaction
     */
    public Optional<Token> callNextToken(int counterId) {
        Optional<Token> tokenOpt = tokenDao.callNextTokenForCounter(counterId);
        if (tokenOpt.isPresent()) {
            Token token = tokenOpt.get();
            Optional<Counter> counterOpt = counterDao.findById(counterId);
            int counterNum = counterOpt.map(Counter::getCounterNumber).orElse(counterId);

            SoundUtil.speakAnnouncementAsync(token.getTokenNumber(), counterNum);
            QueueManager.getInstance().triggerQueueUpdate();
            return Optional.of(token);
        }
        return Optional.empty();
    }

    /**
     * Overload for backward compatibility delegating to the single canonical call path.
     */
    public Optional<Token> callNextToken(int serviceId, int counterId) {
        return callNextToken(counterId);
    }

    /**
     * Counter staff begins serving the customer.
     * Enforces CALLED -> SERVING transition.
     */
    public boolean startServing(int tokenId) throws AppException {
        Token token = tokenDao.findById(tokenId)
                .orElseThrow(() -> new AppException("Token ID " + tokenId + " not found."));

        validateTransition(token.getStatus(), TokenStatus.SERVING);

        boolean ok = tokenDao.updateStatus(tokenId, TokenStatus.SERVING);
        if (ok) {
            logger.info("Token {} transitioned to SERVING.", token.getTokenNumber());
            QueueManager.getInstance().triggerQueueUpdate();
        }
        return ok;
    }

    /**
     * Counter staff marks the service as completed.
     * Enforces SERVING -> COMPLETED transition.
     */
    public boolean completeToken(int tokenId, int counterId) throws AppException {
        Token token = tokenDao.findById(tokenId)
                .orElseThrow(() -> new AppException("Token ID " + tokenId + " not found."));

        validateTransition(token.getStatus(), TokenStatus.COMPLETED);

        boolean ok = tokenDao.updateStatus(tokenId, TokenStatus.COMPLETED);
        if (ok) {
            // Counter status rule: clear current token and return counter to OPEN (idle)
            counterDao.clearToken(counterId, CounterStatus.OPEN);

            ThreadPoolManager.getInstance().submitWorkerTask(() ->
                    serviceStatsDao.upsertDailyStats(token.getServiceId(), LocalDate.now())
            );

            logger.info("Token {} completed at Counter {}.", token.getTokenNumber(), counterId);
            QueueManager.getInstance().triggerQueueUpdate();

            // Efficiency: If auto-call is ON for that counter, immediately call the next token through the single call path
            if (QueueManager.getInstance().isAutoCallEnabled(counterId)) {
                ThreadPoolManager.getInstance().submitWorkerTask(() -> callNextToken(counterId));
            }
        }
        return ok;
    }

    /**
     * Customer did not show up when called.
     * Enforces CALLED -> SKIPPED transition.
     */
    public boolean skipToken(int tokenId, int counterId) throws AppException {
        Token token = tokenDao.findById(tokenId)
                .orElseThrow(() -> new AppException("Token ID " + tokenId + " not found."));

        validateTransition(token.getStatus(), TokenStatus.SKIPPED);

        boolean ok = tokenDao.updateStatus(tokenId, TokenStatus.SKIPPED);
        if (ok) {
            // Counter status rule: clear current token and return counter to OPEN (idle)
            counterDao.clearToken(counterId, CounterStatus.OPEN);

            ThreadPoolManager.getInstance().submitWorkerTask(() ->
                    serviceStatsDao.upsertDailyStats(token.getServiceId(), LocalDate.now())
            );

            logger.info("Token {} skipped (no-show) at Counter {}.", token.getTokenNumber(), counterId);
            QueueManager.getInstance().triggerQueueUpdate();

            // Efficiency: If auto-call is ON for that counter, immediately call the next token through the single call path
            if (QueueManager.getInstance().isAutoCallEnabled(counterId)) {
                ThreadPoolManager.getInstance().submitWorkerTask(() -> callNextToken(counterId));
            }
        }
        return ok;
    }

    /**
     * Recalls a skipped customer, returning them to the back of the waiting queue.
     * Enforces SKIPPED -> WAITING transition.
     */
    public boolean recallToken(int tokenId, int counterNumber) throws AppException {
        Token token = tokenDao.findById(tokenId)
                .orElseThrow(() -> new AppException("Token ID " + tokenId + " not found."));

        validateTransition(token.getStatus(), TokenStatus.WAITING);

        if (token.getRecallCount() > 0) {
            String msg = "Token " + token.getTokenNumber() + " has already been recalled once. Maximum recall limit reached.";
            logger.warn(msg);
            throw new AppException(msg);
        }

        boolean ok = tokenDao.recallToken(tokenId);
        if (ok) {
            SoundUtil.speakAnnouncementAsync(token.getTokenNumber(), counterNumber);
            logger.info("Token {} recalled to back of queue.", token.getTokenNumber());
            QueueManager.getInstance().triggerQueueUpdate();
        }
        return ok;
    }

    /**
     * Cancels a waiting token by customer request or admin action.
     * Enforces WAITING -> CANCELLED transition.
     */
    public boolean cancelToken(int tokenId) throws AppException {
        Token token = tokenDao.findById(tokenId)
                .orElseThrow(() -> new AppException("Token ID " + tokenId + " not found."));

        validateTransition(token.getStatus(), TokenStatus.CANCELLED);

        boolean ok = tokenDao.updateStatus(tokenId, TokenStatus.CANCELLED);
        if (ok) {
            logger.info("Token {} cancelled.", token.getTokenNumber());
            QueueManager.getInstance().triggerQueueUpdate();
        }
        return ok;
    }

    /**
     * Transfers a customer to another department/service desk.
     */
    public boolean transferService(int tokenId, int newServiceId) throws AppException {
        Token token = tokenDao.findById(tokenId)
                .orElseThrow(() -> new AppException("Token ID " + tokenId + " not found."));

        if (token.getStatus() == TokenStatus.COMPLETED || token.getStatus() == TokenStatus.CANCELLED) {
            throw new AppException("Cannot transfer finished token in state: " + token.getStatus());
        }

        boolean ok = tokenDao.transferService(tokenId, newServiceId);
        if (ok) {
            logger.info("Token {} transferred to Service {}.", token.getTokenNumber(), newServiceId);
            QueueManager.getInstance().triggerQueueUpdate();
        }
        return ok;
    }

    /**
     * Looks up an existing token by token number and customer phone.
     */
    public Optional<Token> checkTokenStatus(String tokenNo, String phone, LocalDate date) {
        return tokenDao.findByTokenNoAndPhone(tokenNo, phone, date != null ? date : LocalDate.now());
    }

    public List<Token> getWaitingTokens(int serviceId) {
        return tokenDao.findWaitingTokens(serviceId);
    }

    public List<Token> getCurrentlyServingTokens() {
        return tokenDao.findCurrentlyServingTokens();
    }

    public List<Token> getNextWaitingTokens(int limit) {
        return tokenDao.findNextWaitingTokensForDisplay(limit);
    }

    public List<Token> getCompletedTokensForCounterToday(int counterId) {
        return tokenDao.findCompletedTokensForCounterToday(counterId);
    }

    public WaitTimeEstimator getWaitTimeEstimator() {
        return waitTimeEstimator;
    }
}

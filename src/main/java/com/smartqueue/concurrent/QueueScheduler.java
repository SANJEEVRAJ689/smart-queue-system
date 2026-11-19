package com.smartqueue.concurrent;

import com.smartqueue.dao.CounterDao;
import com.smartqueue.dao.TokenDao;
import com.smartqueue.dao.impl.CounterDaoImpl;
import com.smartqueue.dao.impl.TokenDaoImpl;
import com.smartqueue.model.Counter;
import com.smartqueue.model.Token;
import com.smartqueue.service.QueueManager;
import com.smartqueue.util.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * QueueScheduler is the primary background orchestrator running on a 3-second cycle.
 *
 * Responsibilities:
 * 1. Checks for OPEN counters that have auto-call enabled and are currently idle.
 * 2. Invokes AutoCaller to atomically fetch and call the next waiting customer.
 * 3. Triggers the NoShowWatcher to clean up customers who abandoned their call.
 * 4. Broadcasts periodic queue updates to all open JavaFX screens.
 */
public class QueueScheduler implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(QueueScheduler.class);

    private final CounterDao counterDao;
    private final TokenDao tokenDao;
    private final AutoCaller autoCaller;
    private final NoShowWatcher noShowWatcher;
    private ScheduledFuture<?> scheduledTask;

    public QueueScheduler() {
        this(new CounterDaoImpl(), new TokenDaoImpl(), new AutoCaller(), new NoShowWatcher());
    }

    public QueueScheduler(CounterDao counterDao, TokenDao tokenDao, AutoCaller autoCaller, NoShowWatcher noShowWatcher) {
        this.counterDao = counterDao;
        this.tokenDao = tokenDao;
        this.autoCaller = autoCaller;
        this.noShowWatcher = noShowWatcher;
    }

    /**
     * Starts the periodic scheduling cycle on ThreadPoolManager.
     */
    public synchronized void start() {
        if (scheduledTask == null || scheduledTask.isCancelled()) {
            int interval = AppConfig.getQueueSchedulerIntervalSeconds();
            scheduledTask = ThreadPoolManager.getInstance().scheduleAtFixedRate(
                    this, 1, interval, TimeUnit.SECONDS
            );
            logger.info("QueueScheduler started. Polling every {} seconds.", interval);
        }
    }

    /**
     * Stops the scheduler.
     */
    public synchronized void stop() {
        if (scheduledTask != null) {
            scheduledTask.cancel(false);
            scheduledTask = null;
            logger.info("QueueScheduler stopped.");
        }
    }

    @Override
    public void run() {
        try {
            // Step 1: Check and clean up no-shows
            noShowWatcher.run();

            // Step 2: Auto-call for free counters
            List<Counter> activeCounters = counterDao.findAll();
            List<Token> servingTokens = tokenDao.findCurrentlyServingTokens();

            for (Counter counter : activeCounters) {
                // If counter is marked OPEN/ONLINE and auto-call is turned on
                boolean autoCallOn = QueueManager.getInstance().isAutoCallEnabled(counter.getId());
                boolean isIdle = servingTokens.stream()
                        .noneMatch(t -> t.getCounterId() != null && t.getCounterId() == counter.getId());

                if (autoCallOn && isIdle && !"CLOSED".equalsIgnoreCase(counter.getStatus().name())) {
                    // Check if there are tokens waiting for this service
                    List<Token> waiting = tokenDao.findWaitingTokens(counter.getServiceId());
                    if (!waiting.isEmpty()) {
                        logger.info("QueueScheduler auto-calling for idle Counter {} (Service {})...",
                                counter.getCounterNumber(), counter.getServiceId());
                        autoCaller.callNext(counter.getServiceId(), counter.getId());
                    }
                }
            }

            // Step 3: Trigger live update for wait-time recalculation
            QueueManager.getInstance().triggerQueueUpdate();
        } catch (Exception e) {
            logger.error("Error in QueueScheduler cycle", e);
        }
    }
}

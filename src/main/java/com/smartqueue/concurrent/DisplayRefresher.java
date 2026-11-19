package com.smartqueue.concurrent;

import com.smartqueue.dao.CounterDao;
import com.smartqueue.dao.TokenDao;
import com.smartqueue.dao.impl.CounterDaoImpl;
import com.smartqueue.dao.impl.TokenDaoImpl;
import com.smartqueue.model.Counter;
import com.smartqueue.model.Token;
import com.smartqueue.util.AppConfig;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * DisplayRefresher executes a 2-second background refresh cycle for public display boards.
 *
 * Why this is needed:
 * Display boards installed on waiting-hall TV screens need continuous, smooth, real-time
 * updates without user interaction.
 *
 * This class runs strictly on a background worker thread, fetches current serving counters
 * and the next 5 waiting tokens, and dispatches UI updates via Platform.runLater().
 */
public class DisplayRefresher implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(DisplayRefresher.class);

    public interface DisplayListener {
        void onDisplayRefreshed(List<Counter> counters, List<Token> activeTokens, List<Token> nextWaiting);
    }

    private final TokenDao tokenDao;
    private final CounterDao counterDao;
    private final List<DisplayListener> listeners = new CopyOnWriteArrayList<>();
    private ScheduledFuture<?> scheduledTask;

    public DisplayRefresher() {
        this(new TokenDaoImpl(), new CounterDaoImpl());
    }

    public DisplayRefresher(TokenDao tokenDao, CounterDao counterDao) {
        this.tokenDao = tokenDao;
        this.counterDao = counterDao;
    }

    public void addListener(DisplayListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(DisplayListener listener) {
        listeners.remove(listener);
    }

    public synchronized void start() {
        if (scheduledTask == null || scheduledTask.isCancelled()) {
            int interval = AppConfig.getDisplayRefreshSeconds();
            scheduledTask = ThreadPoolManager.getInstance().scheduleAtFixedRate(
                    this, 0, interval, TimeUnit.SECONDS
            );
            logger.info("DisplayRefresher started. Refreshing every {} seconds.", interval);
        }
    }

    public synchronized void stop() {
        if (scheduledTask != null) {
            scheduledTask.cancel(false);
            scheduledTask = null;
            logger.info("DisplayRefresher stopped.");
        }
    }

    @Override
    public void run() {
        if (listeners.isEmpty()) return;

        try {
            List<Counter> counters = counterDao.findAll();
            List<Token> active = tokenDao.findCurrentlyServingTokens();
            List<Token> nextWaiting = tokenDao.findNextWaitingTokensForDisplay(5);

            Platform.runLater(() -> {
                for (DisplayListener listener : listeners) {
                    try {
                        listener.onDisplayRefreshed(counters, active, nextWaiting);
                    } catch (Exception e) {
                        logger.debug("Error notifying display listener: {}", e.getMessage());
                    }
                }
            });
        } catch (Exception e) {
            logger.error("Error during DisplayRefresher query cycle", e);
        }
    }
}

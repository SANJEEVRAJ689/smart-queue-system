package com.smartqueue.service;

import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Multithreaded Queue Manager.
 *
 * Multithreading Architecture:
 * 1. ScheduledExecutorService: Runs background jobs for wait-time recalculation
 *    and auto-calling when counters become idle.
 * 2. FixedThreadPool (WorkerPool): Offloads all database operations from the JavaFX thread.
 * 3. Thread-Safe Observer Pattern: Broadcasts queue change events to registered UI controllers
 *    strictly dispatched via Platform.runLater().
 * 4. Graceful Shutdown: Cleanly terminates thread pools during application exit.
 */
public class QueueManager {
    private static final Logger logger = LoggerFactory.getLogger(QueueManager.class);
    private static QueueManager instance;

    public interface QueueUpdateListener {
        void onQueueUpdated();
    }

    private final ScheduledExecutorService scheduler;
    private final ExecutorService workerPool;
    private final List<QueueUpdateListener> listeners = new CopyOnWriteArrayList<>();

    // Map of counterId -> autoCallEnabled (boolean)
    private final Map<Integer, Boolean> autoCallCounters = new ConcurrentHashMap<>();

    private QueueManager() {
        this.scheduler = Executors.newScheduledThreadPool(2, new ThreadFactory() {
            private int count = 0;
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "QueueManager-Scheduler-" + (++count));
                t.setDaemon(true);
                return t;
            }
        });

        this.workerPool = Executors.newFixedThreadPool(4, new ThreadFactory() {
            private int count = 0;
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "QueueManager-Worker-" + (++count));
                t.setDaemon(true);
                return t;
            }
        });

        startBackgroundSchedulers();
    }

    public static synchronized QueueManager getInstance() {
        if (instance == null) {
            instance = new QueueManager();
        }
        return instance;
    }

    private void startBackgroundSchedulers() {
        // Scheduler 1: Broadcasts queue tick every 3 seconds to keep UI tables and timers fresh
        scheduler.scheduleAtFixedRate(() -> {
            try {
                triggerQueueUpdate();
            } catch (Exception e) {
                logger.debug("Error during periodic queue update: {}", e.getMessage());
            }
        }, 3, 3, TimeUnit.SECONDS);

        logger.info("QueueManager background schedulers successfully started.");
    }

    /**
     * Submits a non-blocking asynchronous task to the worker pool.
     */
    public void submitWorkerTask(Runnable task) {
        if (!workerPool.isShutdown()) {
            workerPool.submit(task);
        }
    }

    /**
     * Submits a callable task and returns a Future.
     */
    public <T> Future<T> submitCallable(Callable<T> callable) {
        if (!workerPool.isShutdown()) {
            return workerPool.submit(callable);
        }
        return CompletableFuture.failedFuture(new RejectedExecutionException("Worker pool is shutdown"));
    }

    /**
     * Registers a UI listener to receive queue changes.
     */
    public void addListener(QueueUpdateListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(QueueUpdateListener listener) {
        listeners.remove(listener);
    }

    /**
     * Broadcasts queue update to all UI controllers on the JavaFX Application Thread.
     */
    public void triggerQueueUpdate() {
        if (listeners.isEmpty()) return;

        Runnable broadcast = () -> {
            for (QueueUpdateListener listener : listeners) {
                try {
                    listener.onQueueUpdated();
                } catch (Exception e) {
                    logger.debug("Error notifying queue listener: {}", e.getMessage());
                }
            }
        };

        if (Platform.isFxApplicationThread()) {
            broadcast.run();
        } else {
            Platform.runLater(broadcast);
        }
    }

    /**
     * Sets auto-call state for a counter.
     */
    public void setAutoCall(int counterId, boolean enabled) {
        autoCallCounters.put(counterId, enabled);
        logger.info("Counter {} auto-calling set to: {}", counterId, enabled);
    }

    public void setAutoCallEnabled(int counterId, boolean enabled) {
        setAutoCall(counterId, enabled);
    }

    public boolean isAutoCallEnabled(int counterId) {
        return autoCallCounters.getOrDefault(counterId, false);
    }

    /**
     * Graceful shutdown of all executor thread pools on application exit.
     */
    public synchronized void shutdown() {
        logger.info("Initiating graceful shutdown of QueueManager thread pools...");

        scheduler.shutdown();
        workerPool.shutdown();

        try {
            if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
            if (!workerPool.awaitTermination(2, TimeUnit.SECONDS)) {
                workerPool.shutdownNow();
            }
            logger.info("QueueManager executors terminated successfully.");
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            workerPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}

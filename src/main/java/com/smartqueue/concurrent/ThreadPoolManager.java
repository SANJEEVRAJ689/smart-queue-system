package com.smartqueue.concurrent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;

/**
 * ThreadPoolManager manages all background worker threads and scheduled tasks
 * across the Smart Queue application.
 *
 * Why this is needed:
 * In desktop applications (like JavaFX), performing database operations or long
 * tasks directly on the JavaFX Application Thread will cause the entire GUI to
 * freeze and become unresponsive.
 *
 * This class provides:
 * 1. A shared FixedThreadPool for offloading database/network tasks.
 * 2. A ScheduledExecutorService for periodic timers (QueueScheduler, DisplayRefresher, NoShowWatcher).
 * 3. A clean shutdown mechanism to prevent zombie threads on application exit.
 */
public class ThreadPoolManager {
    private static final Logger logger = LoggerFactory.getLogger(ThreadPoolManager.class);
    private static ThreadPoolManager instance;

    // Fixed pool of worker threads for asynchronous DB tasks
    private final ExecutorService workerPool;

    // Scheduled pool for periodic background polling and auto-call cycles
    private final ScheduledExecutorService scheduler;

    private ThreadPoolManager() {
        this.workerPool = Executors.newFixedThreadPool(6, new ThreadFactory() {
            private int count = 0;
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "SmartQueue-Worker-" + (++count));
                t.setDaemon(true); // Daemon threads exit automatically when app closes
                return t;
            }
        });

        this.scheduler = Executors.newScheduledThreadPool(4, new ThreadFactory() {
            private int count = 0;
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "SmartQueue-Scheduler-" + (++count));
                t.setDaemon(true);
                return t;
            }
        });

        logger.info("ThreadPoolManager initialized with 6 worker threads and 4 scheduler threads.");
    }

    public static synchronized ThreadPoolManager getInstance() {
        if (instance == null) {
            instance = new ThreadPoolManager();
        }
        return instance;
    }

    /**
     * Submits a non-blocking asynchronous task to the worker pool.
     */
    public void submitWorkerTask(Runnable task) {
        if (!workerPool.isShutdown()) {
            workerPool.submit(task);
        } else {
            logger.warn("Worker pool is shut down; task rejected.");
        }
    }

    /**
     * Submits a callable task and returns a Future.
     */
    public <T> Future<T> submitCallable(Callable<T> callable) {
        if (!workerPool.isShutdown()) {
            return workerPool.submit(callable);
        }
        return CompletableFuture.failedFuture(new RejectedExecutionException("Worker pool is shut down"));
    }

    /**
     * Schedules a periodic task with a fixed interval.
     */
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        if (!scheduler.isShutdown()) {
            return scheduler.scheduleAtFixedRate(command, initialDelay, period, unit);
        }
        return null;
    }

    /**
     * Gracefully terminates all background thread pools on application shutdown.
     */
    public synchronized void shutdown() {
        logger.info("ThreadPoolManager initiating graceful shutdown...");
        scheduler.shutdown();
        workerPool.shutdown();

        try {
            if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
            if (!workerPool.awaitTermination(2, TimeUnit.SECONDS)) {
                workerPool.shutdownNow();
            }
            logger.info("ThreadPoolManager threads shut down successfully.");
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            workerPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}

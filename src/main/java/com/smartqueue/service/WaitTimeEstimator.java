package com.smartqueue.service;

import com.smartqueue.dao.CounterDao;
import com.smartqueue.dao.ServiceDao;
import com.smartqueue.dao.TokenDao;
import com.smartqueue.dao.impl.CounterDaoImpl;
import com.smartqueue.dao.impl.ServiceDaoImpl;
import com.smartqueue.dao.impl.TokenDaoImpl;
import com.smartqueue.model.ServiceEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * Service for calculating dynamic estimated waiting times.
 *
 * Upgraded Formula (Phase 1):
 * Estimated Wait (minutes) =
 *   (Remaining active service time of tokens currently SERVING + People ahead * Avg service time)
 *   ---------------------------------------------------------------------------------------------
 *                        Number of OPEN or BUSY counters for that service (min 1)
 *
 * Fallback Behavior:
 * If fewer than MIN_SAMPLES (3) completed tokens exist today for this service,
 * the algorithm falls back to the configured service default time (e.g. 5 minutes).
 */
public class WaitTimeEstimator {
    private static final Logger logger = LoggerFactory.getLogger(WaitTimeEstimator.class);
    private static final int DEFAULT_HISTORY_SAMPLE_SIZE = 10;
    private static final int MIN_SAMPLES_FOR_HISTORY = 3;

    private final TokenDao tokenDao;
    private final ServiceDao serviceDao;
    private final CounterDao counterDao;

    public WaitTimeEstimator() {
        this.tokenDao = new TokenDaoImpl();
        this.serviceDao = new ServiceDaoImpl();
        this.counterDao = new CounterDaoImpl();
    }

    public WaitTimeEstimator(TokenDao tokenDao, ServiceDao serviceDao) {
        this(tokenDao, serviceDao, new CounterDaoImpl());
    }

    public WaitTimeEstimator(TokenDao tokenDao, ServiceDao serviceDao, CounterDao counterDao) {
        this.tokenDao = tokenDao;
        this.serviceDao = serviceDao;
        this.counterDao = counterDao;
    }

    /**
     * Pure calculation function implementing Phase 1 formula:
     * Wait Estimate = (Remaining time of tokens currently SERVING + People ahead * Avg Service Time)
     *                 / Number of OPEN or BUSY counters for that service (minimum 1).
     *
     * @param remainingActiveMinutes remaining service time of currently SERVING tokens
     * @param peopleAhead number of waiting customers ahead in queue
     * @param avgServiceTime average service duration in minutes
     * @param activeCounters number of counters currently OPEN or BUSY for this service
     * @return estimated wait time in minutes
     */
    public int estimateMinutes(double remainingActiveMinutes, int peopleAhead, double avgServiceTime, int activeCounters) {
        if (peopleAhead <= 0 && remainingActiveMinutes <= 0.0) {
            return 0;
        }

        int counters = Math.max(1, activeCounters);
        double totalWorkMinutes = Math.max(0.0, remainingActiveMinutes) + (Math.max(0, peopleAhead) * avgServiceTime);
        double wait = totalWorkMinutes / counters;

        // Ensure minimum 1 minute if there is work ahead (someone serving or waiting)
        return Math.max(1, (int) Math.round(wait));
    }

    /**
     * Overload for backwards compatibility and sample-based calculation.
     */
    public int estimateMinutes(int peopleAhead, List<Double> recentDurations, int defaultAvgMins) {
        double avgServiceTime = calculateAverage(recentDurations, defaultAvgMins);
        return estimateMinutes(0.0, peopleAhead, avgServiceTime, 1);
    }

    public double calculateAverage(List<Double> recentDurations, int defaultAvgMins) {
        if (recentDurations != null && recentDurations.size() >= MIN_SAMPLES_FOR_HISTORY) {
            double sum = 0.0;
            for (Double d : recentDurations) {
                sum += d;
            }
            return sum / recentDurations.size();
        } else {
            return (defaultAvgMins > 0 ? defaultAvgMins : 5.0);
        }
    }

    /**
     * Dynamically calculates wait time for a specific waiting token from the database.
     */
    public int estimateForToken(int tokenId, int serviceId) {
        int peopleAhead = tokenDao.countWaitingAhead(tokenId, serviceId);
        List<Double> recentDurations = tokenDao.getRecentServiceDurationsMinutes(serviceId, DEFAULT_HISTORY_SAMPLE_SIZE);

        int defaultTime = 5;
        Optional<ServiceEntity> sOpt = serviceDao.findById(serviceId);
        if (sOpt.isPresent()) {
            defaultTime = sOpt.get().getDefaultAvgTimeMins();
        }

        double avgServiceTime = calculateAverage(recentDurations, defaultTime);
        double remainingActiveMins = tokenDao.getRemainingActiveServiceMinutes(serviceId, avgServiceTime);
        int activeCounters = counterDao.findOpenOrBusyCountersByService(serviceId).size();

        return estimateMinutes(remainingActiveMins, peopleAhead, avgServiceTime, activeCounters);
    }

    /**
     * Estimates wait time for a prospective new customer booking a token right now.
     */
    public int estimateForNewBooking(int serviceId, boolean isPriority) {
        int waitingCount = tokenDao.findWaitingTokens(serviceId).size();
        // Priority customers skip past regular waiting tokens
        int peopleAhead = isPriority ? 0 : waitingCount;

        List<Double> recentDurations = tokenDao.getRecentServiceDurationsMinutes(serviceId, DEFAULT_HISTORY_SAMPLE_SIZE);

        int defaultTime = 5;
        Optional<ServiceEntity> sOpt = serviceDao.findById(serviceId);
        if (sOpt.isPresent()) {
            defaultTime = sOpt.get().getDefaultAvgTimeMins();
        }

        double avgServiceTime = calculateAverage(recentDurations, defaultTime);
        double remainingActiveMins = tokenDao.getRemainingActiveServiceMinutes(serviceId, avgServiceTime);
        int activeCounters = counterDao.findOpenOrBusyCountersByService(serviceId).size();

        return estimateMinutes(remainingActiveMins, peopleAhead, avgServiceTime, activeCounters);
    }
}

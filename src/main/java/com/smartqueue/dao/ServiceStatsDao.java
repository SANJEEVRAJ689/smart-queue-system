package com.smartqueue.dao;

import com.smartqueue.model.ServiceStats;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object interface for daily queue statistics and analytics.
 */
public interface ServiceStatsDao {
    Optional<ServiceStats> findByServiceAndDate(int serviceId, LocalDate date);
    List<ServiceStats> findStatsByDateRange(LocalDate startDate, LocalDate endDate);
    List<ServiceStats> findDailyStatsForService(int serviceId, int pastDays);
    boolean upsertDailyStats(int serviceId, LocalDate date);
}

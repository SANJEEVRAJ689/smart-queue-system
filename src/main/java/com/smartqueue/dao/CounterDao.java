package com.smartqueue.dao;

import com.smartqueue.model.Counter;
import com.smartqueue.model.CounterStatus;

import java.util.List;
import java.util.Optional;

/**
 * Data Access Object interface for Counter operations.
 */
public interface CounterDao {
    Optional<Counter> findById(int id);
    Optional<Counter> findByName(String name);
    Optional<Counter> findByStaffId(int staffId);
    List<Counter> findAll();
    List<Counter> findActiveOnlineCounters();
    List<Counter> findOpenOrBusyCountersByService(int serviceId);
    List<Counter> findOpenIdleCounters();
    boolean create(Counter counter);
    boolean update(Counter counter);
    boolean updateStatus(int counterId, CounterStatus status);
    boolean updateStatus(int counterId, String status);
    boolean assignToken(int counterId, Integer tokenId, CounterStatus status);
    boolean assignToken(java.sql.Connection conn, int counterId, Integer tokenId, CounterStatus status);
    boolean clearToken(int counterId, CounterStatus newStatus);
    boolean clearToken(java.sql.Connection conn, int counterId, CounterStatus newStatus);
    boolean delete(int id);
}

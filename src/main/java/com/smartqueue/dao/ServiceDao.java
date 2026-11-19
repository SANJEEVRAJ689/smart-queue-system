package com.smartqueue.dao;

import com.smartqueue.model.ServiceEntity;

import java.util.List;
import java.util.Optional;

/**
 * Data Access Object interface for Services.
 */
public interface ServiceDao {
    Optional<ServiceEntity> findById(int id);
    Optional<ServiceEntity> findByPrefix(String prefix);
    List<ServiceEntity> findAll();
    List<ServiceEntity> findActiveServices();
    boolean create(ServiceEntity service);
    boolean update(ServiceEntity service);
    boolean delete(int id);
}

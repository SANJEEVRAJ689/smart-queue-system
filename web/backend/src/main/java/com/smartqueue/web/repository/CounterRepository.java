package com.smartqueue.web.repository;

import com.smartqueue.web.entity.CounterEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CounterRepository extends JpaRepository<CounterEntity, Integer> {
    List<CounterEntity> findByServiceId(Integer serviceId);

    @Query("SELECT c FROM CounterEntity c WHERE c.serviceId = :serviceId AND c.status IN ('OPEN', 'BUSY')")
    List<CounterEntity> findOpenOrBusyCountersByService(@Param("serviceId") Integer serviceId);

    @Query("SELECT c FROM CounterEntity c WHERE c.status IN ('OPEN', 'BUSY')")
    List<CounterEntity> findAllActiveCounters();
}

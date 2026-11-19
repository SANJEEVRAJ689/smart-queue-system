package com.smartqueue.web.repository;

import com.smartqueue.web.entity.AuditLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLogEntity, Integer> {
    List<AuditLogEntity> findTop100ByOrderByTimestampDesc();
}

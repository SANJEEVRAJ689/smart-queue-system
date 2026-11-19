package com.smartqueue.web.repository;

import com.smartqueue.web.entity.ServiceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ServiceRepository extends JpaRepository<ServiceEntity, Integer> {
    List<ServiceEntity> findByActiveTrue();
    Optional<ServiceEntity> findByPrefix(String prefix);
}

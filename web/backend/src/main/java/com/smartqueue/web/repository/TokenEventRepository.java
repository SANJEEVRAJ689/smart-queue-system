package com.smartqueue.web.repository;

import com.smartqueue.web.entity.TokenEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TokenEventRepository extends JpaRepository<TokenEventEntity, Integer> {
    List<TokenEventEntity> findByTokenIdOrderByCreatedAtAsc(Integer tokenId);
}

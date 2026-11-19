package com.smartqueue.web.repository;

import com.smartqueue.web.entity.RefreshTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, Integer> {
    Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);
    void deleteByUserId(Integer userId);
}

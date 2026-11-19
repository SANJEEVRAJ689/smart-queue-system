package com.smartqueue.web.repository;

import com.smartqueue.web.entity.TokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TokenRepository extends JpaRepository<TokenEntity, Integer> {

    Optional<TokenEntity> findByTrackingCode(String trackingCode);

    Optional<TokenEntity> findByTokenNoAndPhoneAndQueueDate(String tokenNo, String phone, LocalDate queueDate);

    @Query("SELECT t FROM TokenEntity t WHERE t.counterId = :counterId AND t.status IN ('CALLED', 'SERVING')")
    Optional<TokenEntity> findActiveTokenForCounter(@Param("counterId") Integer counterId);

    @Query("SELECT COUNT(t) FROM TokenEntity t WHERE t.serviceId = :serviceId AND t.status = 'WAITING' " +
           "AND t.queueDate = :queueDate AND t.createdAt < (SELECT t2.createdAt FROM TokenEntity t2 WHERE t2.id = :tokenId)")
    int countWaitingAhead(@Param("tokenId") Integer tokenId, @Param("serviceId") Integer serviceId, @Param("queueDate") LocalDate queueDate);

    @Query("SELECT COUNT(t) FROM TokenEntity t WHERE t.serviceId = :serviceId AND t.status = 'WAITING'")
    int countWaitingByService(@Param("serviceId") Integer serviceId);

    @Query("SELECT t FROM TokenEntity t WHERE t.serviceId = :serviceId AND t.status = 'WAITING' " +
           "ORDER BY " +
           "  CASE " +
           "    WHEN t.priority = 'EMERGENCY' THEN 0 " +
           "    WHEN t.priority = 'SENIOR' THEN 1 " +
           "    ELSE 2 " +
           "  END ASC, " +
           "  t.createdAt ASC")
    List<TokenEntity> findWaitingTokensForService(@Param("serviceId") Integer serviceId);

    @Query("SELECT t FROM TokenEntity t WHERE t.status IN ('CALLED', 'SERVING')")
    List<TokenEntity> findCurrentlyServingTokens();

    @Query(value = "SELECT * FROM tokens WHERE service_id = :serviceId AND status = 'COMPLETED' " +
                   "AND started_at IS NOT NULL AND completed_at IS NOT NULL " +
                   "AND TIMESTAMPDIFF(SECOND, started_at, completed_at) >= 3 " +
                   "ORDER BY completed_at DESC LIMIT 20", nativeQuery = true)
    List<TokenEntity> findRecentCompletedTokens(@Param("serviceId") Integer serviceId);

    @Query("SELECT t FROM TokenEntity t WHERE t.queueDate = :date ORDER BY t.createdAt DESC")
    List<TokenEntity> findByQueueDateOrderByCreatedAtDesc(@Param("date") LocalDate date);

    @Query("SELECT COUNT(t) FROM TokenEntity t WHERE t.queueDate = :date")
    long countTodayTokens(@Param("date") LocalDate date);

    @Query("SELECT COUNT(t) FROM TokenEntity t WHERE t.status = 'WAITING'")
    long countTotalWaiting();
}

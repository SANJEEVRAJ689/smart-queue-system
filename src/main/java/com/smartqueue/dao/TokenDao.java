package com.smartqueue.dao;

import com.smartqueue.model.Token;
import com.smartqueue.model.TokenStatus;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object interface for Token operations.
 */
public interface TokenDao {
    Optional<Token> findById(int id);
    Optional<Token> findByTrackingCode(String trackingCode);
    Optional<Token> findByTokenNoAndPhone(String tokenNo, String phone, LocalDate queueDate);

    boolean create(Token token);
    boolean create(Connection conn, Token token) throws SQLException;

    /**
     * Atomically locks and claims the highest-priority eligible waiting token for a service.
     * Applies starvation prevention rule inside the SQL ordering logic.
     */
    Optional<Token> claimNextToken(Connection conn, int serviceId, int counterId, int starvationThresholdMinutes) throws SQLException;
    Optional<Token> callNextToken(int serviceId, int counterId);
    Optional<Token> callNextTokenForCounter(int counterId);

    String generateNextTokenNumber(String prefix);

    boolean updateStatus(int tokenId, TokenStatus newStatus);
    boolean updateStatus(Connection conn, int tokenId, TokenStatus newStatus) throws SQLException;

    boolean transferService(int tokenId, int newServiceId);
    boolean recallToken(int tokenId);

    List<Token> findWaitingTokens(int serviceId);
    List<Token> findAllWaitingTokens();
    List<Token> findCurrentlyServingTokens();
    List<Token> findNextWaitingTokensForDisplay(int limit);
    List<Token> findOverdueCalledTokens(int timeoutSeconds);
    List<Token> findCompletedTokensForCounterToday(int counterId);

    int countWaitingAhead(int tokenId, int serviceId);
    int countWaitingAhead(int tokenId, int serviceId, int starvationThresholdMinutes);
    List<Double> getRecentServiceDurationsMinutes(int serviceId, int limitN);
    double getRemainingActiveServiceMinutes(int serviceId, double avgServiceMinutes);

    List<Token> findTokensByDate(LocalDate date);
}

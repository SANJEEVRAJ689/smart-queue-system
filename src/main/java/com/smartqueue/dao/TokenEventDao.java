package com.smartqueue.dao;

import com.smartqueue.model.TokenEvent;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Data Access Object for recording and retrieving token transition events.
 */
public interface TokenEventDao {
    boolean recordEvent(int tokenId, String fromStatus, String toStatus, Integer userId, Integer counterId);
    boolean recordEvent(Connection conn, int tokenId, String fromStatus, String toStatus, Integer userId, Integer counterId) throws SQLException;
    List<TokenEvent> findByTokenId(int tokenId);
    List<TokenEvent> findRecentEvents(int limit);
}

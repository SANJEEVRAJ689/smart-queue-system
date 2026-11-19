package com.smartqueue.dao.impl;

import com.smartqueue.dao.TokenEventDao;
import com.smartqueue.model.TokenEvent;
import com.smartqueue.util.DatabaseConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBC Implementation of TokenEventDao.
 */
public class TokenEventDaoImpl implements TokenEventDao {
    private static final Logger logger = LoggerFactory.getLogger(TokenEventDaoImpl.class);

    @Override
    public boolean recordEvent(int tokenId, String fromStatus, String toStatus, Integer userId, Integer counterId) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return recordEvent(conn, tokenId, fromStatus, toStatus, userId, counterId);
        } catch (SQLException e) {
            logger.error("Error recording token event for token {}: {} -> {}", tokenId, fromStatus, toStatus, e);
            return false;
        }
    }

    @Override
    public boolean recordEvent(Connection conn, int tokenId, String fromStatus, String toStatus,
                               Integer userId, Integer counterId) throws SQLException {
        String sql = "INSERT INTO token_events (token_id, from_status, to_status, user_id, counter_id, created_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, tokenId);
            if (fromStatus != null) {
                ps.setString(2, fromStatus);
            } else {
                ps.setNull(2, Types.VARCHAR);
            }
            ps.setString(3, toStatus);
            if (userId != null) {
                ps.setInt(4, userId);
            } else {
                ps.setNull(4, Types.INTEGER);
            }
            if (counterId != null) {
                ps.setInt(5, counterId);
            } else {
                ps.setNull(5, Types.INTEGER);
            }
            ps.setTimestamp(6, Timestamp.from(Instant.now()));
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public List<TokenEvent> findByTokenId(int tokenId) {
        List<TokenEvent> list = new ArrayList<>();
        String sql = "SELECT id, token_id, from_status, to_status, user_id, counter_id, created_at " +
                     "FROM token_events WHERE token_id = ? ORDER BY created_at ASC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, tokenId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSet(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error fetching token events for token {}", tokenId, e);
        }
        return list;
    }

    @Override
    public List<TokenEvent> findRecentEvents(int limit) {
        List<TokenEvent> list = new ArrayList<>();
        String sql = "SELECT id, token_id, from_status, to_status, user_id, counter_id, created_at " +
                     "FROM token_events ORDER BY id DESC LIMIT ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSet(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error fetching recent token events", e);
        }
        return list;
    }

    private TokenEvent mapResultSet(ResultSet rs) throws SQLException {
        TokenEvent e = new TokenEvent();
        e.setId(rs.getInt("id"));
        e.setTokenId(rs.getInt("token_id"));
        e.setFromStatus(rs.getString("from_status"));
        e.setToStatus(rs.getString("to_status"));
        int uid = rs.getInt("user_id");
        if (!rs.wasNull()) e.setUserId(uid);
        int cid = rs.getInt("counter_id");
        if (!rs.wasNull()) e.setCounterId(cid);
        Timestamp created = rs.getTimestamp("created_at");
        if (created != null) e.setCreatedAt(created.toInstant());
        return e;
    }
}

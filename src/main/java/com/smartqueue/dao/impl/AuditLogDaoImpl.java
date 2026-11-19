package com.smartqueue.dao.impl;

import com.smartqueue.dao.AuditLogDao;
import com.smartqueue.model.AuditLog;
import com.smartqueue.util.DatabaseConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBC Implementation of AuditLogDao.
 */
public class AuditLogDaoImpl implements AuditLogDao {
    private static final Logger logger = LoggerFactory.getLogger(AuditLogDaoImpl.class);

    private static final String BASE_SELECT =
            "SELECT a.id, a.user_id, a.action, a.token_id, a.timestamp, " +
            "       u.username, t.token_no " +
            "FROM audit_log a " +
            "LEFT JOIN users u ON a.user_id = u.id " +
            "LEFT JOIN tokens t ON a.token_id = t.id ";

    @Override
    public boolean log(Integer userId, String action, Integer tokenId) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return log(conn, userId, action, tokenId);
        } catch (SQLException e) {
            logger.error("Error acquiring connection for audit log: {}", action, e);
            return false;
        }
    }

    @Override
    public boolean log(Connection conn, Integer userId, String action, Integer tokenId) {
        String sql = "INSERT INTO audit_log (user_id, action, token_id) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (userId != null) {
                ps.setInt(1, userId);
            } else {
                ps.setNull(1, Types.INTEGER);
            }
            ps.setString(2, action);
            if (tokenId != null) {
                ps.setInt(3, tokenId);
            } else {
                ps.setNull(3, Types.INTEGER);
            }
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("Error writing audit log: {}", action, e);
        }
        return false;
    }

    @Override
    public List<AuditLog> findRecentLogs(int limit) {
        List<AuditLog> list = new ArrayList<>();
        String sql = BASE_SELECT + "ORDER BY a.timestamp DESC, a.id DESC LIMIT ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToAuditLog(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error fetching recent audit logs", e);
        }
        return list;
    }

    @Override
    public List<AuditLog> findLogsByTokenId(int tokenId) {
        List<AuditLog> list = new ArrayList<>();
        String sql = BASE_SELECT + "WHERE a.token_id = ? ORDER BY a.timestamp ASC, a.id ASC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, tokenId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToAuditLog(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error fetching audit logs for token: {}", tokenId, e);
        }
        return list;
    }

    private AuditLog mapResultSetToAuditLog(ResultSet rs) throws SQLException {
        AuditLog a = new AuditLog();
        a.setId(rs.getInt("id"));
        int uId = rs.getInt("user_id");
        if (!rs.wasNull()) a.setUserId(uId);
        a.setAction(rs.getString("action"));
        int tId = rs.getInt("token_id");
        if (!rs.wasNull()) a.setTokenId(tId);
        Timestamp ts = rs.getTimestamp("timestamp");
        if (ts != null) a.setTimestamp(ts.toLocalDateTime());
        a.setUsername(rs.getString("username"));
        a.setTokenNo(rs.getString("token_no"));
        return a;
    }
}

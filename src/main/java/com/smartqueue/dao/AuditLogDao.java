package com.smartqueue.dao;

import com.smartqueue.model.AuditLog;

import java.util.List;

/**
 * Data Access Object interface for system audit logging.
 */
public interface AuditLogDao {
    boolean log(Integer userId, String action, Integer tokenId);
    boolean log(java.sql.Connection conn, Integer userId, String action, Integer tokenId);
    List<AuditLog> findRecentLogs(int limit);
    List<AuditLog> findLogsByTokenId(int tokenId);
}

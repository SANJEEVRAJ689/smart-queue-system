package com.smartqueue.model;

import java.time.LocalDateTime;

/**
 * Entity representing an action recorded in the system audit trail.
 */
public class AuditLog {
    private int id;
    private Integer userId;
    private String action;
    private Integer tokenId;
    private LocalDateTime timestamp;

    // Display fields for UI tables
    private String username;
    private String tokenNo;

    public AuditLog() {
        this.timestamp = LocalDateTime.now();
    }

    public AuditLog(int id, Integer userId, String action, Integer tokenId, LocalDateTime timestamp) {
        this.id = id;
        this.userId = userId;
        this.action = action;
        this.tokenId = tokenId;
        this.timestamp = timestamp != null ? timestamp : LocalDateTime.now();
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public Integer getTokenId() {
        return tokenId;
    }

    public void setTokenId(Integer tokenId) {
        this.tokenId = tokenId;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getTokenNo() {
        return tokenNo;
    }

    public void setTokenNo(String tokenNo) {
        this.tokenNo = tokenNo;
    }

    @Override
    public String toString() {
        return timestamp + " | " + action + " | User: " + username + " | Token: " + tokenNo;
    }
}

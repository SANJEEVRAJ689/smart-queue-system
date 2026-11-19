package com.smartqueue.model;

import java.time.Instant;

/**
 * Immutable entity capturing a token state transition event.
 * Provides a full event-sourced audit trail for every status change.
 */
public class TokenEvent {
    private int id;
    private int tokenId;
    private String fromStatus;
    private String toStatus;
    private Integer userId;
    private Integer counterId;
    private Instant createdAt;

    public TokenEvent() {
        this.createdAt = Instant.now();
    }

    public TokenEvent(int tokenId, String fromStatus, String toStatus, Integer userId, Integer counterId) {
        this.tokenId = tokenId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.userId = userId;
        this.counterId = counterId;
        this.createdAt = Instant.now();
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getTokenId() {
        return tokenId;
    }

    public void setTokenId(int tokenId) {
        this.tokenId = tokenId;
    }

    public String getFromStatus() {
        return fromStatus;
    }

    public void setFromStatus(String fromStatus) {
        this.fromStatus = fromStatus;
    }

    public String getToStatus() {
        return toStatus;
    }

    public void setToStatus(String toStatus) {
        this.toStatus = toStatus;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public Integer getCounterId() {
        return counterId;
    }

    public void setCounterId(Integer counterId) {
        this.counterId = counterId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

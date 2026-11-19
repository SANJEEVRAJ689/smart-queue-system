package com.smartqueue.web.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "token_events")
public class TokenEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "token_id", nullable = false)
    private Integer tokenId;

    @Column(name = "from_status", length = 20)
    private String fromStatus;

    @Column(name = "to_status", nullable = false, length = 20)
    private String toStatus;

    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "counter_id")
    private Integer counterId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    public TokenEventEntity() {}

    public TokenEventEntity(Integer tokenId, String fromStatus, String toStatus, Integer userId, Integer counterId) {
        this.tokenId = tokenId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.userId = userId;
        this.counterId = counterId;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getTokenId() {
        return tokenId;
    }

    public void setTokenId(Integer tokenId) {
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

package com.smartqueue.web.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "audit_log")
public class AuditLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "user_id")
    private Integer userId;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(name = "event_type", length = 50)
    private String eventType;

    @Column(name = "token_id")
    private Integer tokenId;

    @Column(name = "entity_type", length = 50)
    private String entityType;

    @Column(name = "entity_id")
    private Integer entityId;

    @Column(columnDefinition = "json")
    private String metadata;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "timestamp", insertable = false, updatable = false)
    private Instant timestamp;

    public AuditLogEntity() {}

    public AuditLogEntity(Integer userId, String action, String eventType, Integer tokenId, String entityType, Integer entityId, String metadata, String ipAddress) {
        this.userId = userId;
        this.action = action;
        this.eventType = eventType;
        this.tokenId = tokenId;
        this.entityType = entityType;
        this.entityId = entityId;
        this.metadata = metadata;
        this.ipAddress = ipAddress;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
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

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public Integer getTokenId() {
        return tokenId;
    }

    public void setTokenId(Integer tokenId) {
        this.tokenId = tokenId;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public Integer getEntityId() {
        return entityId;
    }

    public void setEntityId(Integer entityId) {
        this.entityId = entityId;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getCreatedAt() {
        return timestamp != null ? timestamp.toString() : "";
    }

    public String getUsername() {
        return userId != null ? "User #" + userId : "SYSTEM";
    }

    public String getDetails() {
        return metadata != null && !metadata.isBlank() ? metadata : (eventType != null ? eventType : (action != null ? action : ""));
    }
}

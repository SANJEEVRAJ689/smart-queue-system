package com.smartqueue.web.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "counters")
public class CounterEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "service_id", nullable = false)
    private Integer serviceId;

    @Column(name = "staff_id")
    private Integer staffId;

    @Column(nullable = false)
    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.CHAR)
    private String status = "CLOSED"; // OPEN, CLOSED, BUSY

    @Column(name = "current_token_id")
    private Integer currentTokenId;

    public CounterEntity() {}

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getServiceId() {
        return serviceId;
    }

    public void setServiceId(Integer serviceId) {
        this.serviceId = serviceId;
    }

    public Integer getStaffId() {
        return staffId;
    }

    public void setStaffId(Integer staffId) {
        this.staffId = staffId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getCurrentTokenId() {
        return currentTokenId;
    }

    public void setCurrentTokenId(Integer currentTokenId) {
        this.currentTokenId = currentTokenId;
    }
}

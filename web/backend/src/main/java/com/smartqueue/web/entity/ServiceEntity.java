package com.smartqueue.web.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "services")
public class ServiceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 2)
    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.CHAR)
    private String prefix;

    @Column(name = "default_avg_minutes", nullable = false)
    private Integer defaultAvgMinutes = 5;

    @Column(nullable = false)
    private Boolean active = true;

    public ServiceEntity() {}

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

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public Integer getDefaultAvgMinutes() {
        return defaultAvgMinutes;
    }

    public void setDefaultAvgMinutes(Integer defaultAvgMinutes) {
        this.defaultAvgMinutes = defaultAvgMinutes;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}

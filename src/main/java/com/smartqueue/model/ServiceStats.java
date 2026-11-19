package com.smartqueue.model;

import java.time.LocalDate;

/**
 * Model representing daily statistical metrics for reporting and charts.
 */
public class ServiceStats {
    private int id;
    private int serviceId;
    private String serviceName;
    private LocalDate statDate;
    private int totalTokens;
    private int completedTokens;
    private int skippedTokens;
    private int avgWaitSeconds;
    private int avgServiceSeconds;

    public ServiceStats() {}

    public ServiceStats(int id, int serviceId, String serviceName, LocalDate statDate,
                        int totalTokens, int completedTokens, int skippedTokens,
                        int avgWaitSeconds, int avgServiceSeconds) {
        this.id = id;
        this.serviceId = serviceId;
        this.serviceName = serviceName;
        this.statDate = statDate;
        this.totalTokens = totalTokens;
        this.completedTokens = completedTokens;
        this.skippedTokens = skippedTokens;
        this.avgWaitSeconds = avgWaitSeconds;
        this.avgServiceSeconds = avgServiceSeconds;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getServiceId() {
        return serviceId;
    }

    public void setServiceId(int serviceId) {
        this.serviceId = serviceId;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public LocalDate getStatDate() {
        return statDate;
    }

    public void setStatDate(LocalDate statDate) {
        this.statDate = statDate;
    }

    public int getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(int totalTokens) {
        this.totalTokens = totalTokens;
    }

    public int getCompletedTokens() {
        return completedTokens;
    }

    public void setCompletedTokens(int completedTokens) {
        this.completedTokens = completedTokens;
    }

    public int getSkippedTokens() {
        return skippedTokens;
    }

    public void setSkippedTokens(int skippedTokens) {
        this.skippedTokens = skippedTokens;
    }

    public int getAvgWaitSeconds() {
        return avgWaitSeconds;
    }

    public void setAvgWaitSeconds(int avgWaitSeconds) {
        this.avgWaitSeconds = avgWaitSeconds;
    }

    public int getAvgServiceSeconds() {
        return avgServiceSeconds;
    }

    public void setAvgServiceSeconds(int avgServiceSeconds) {
        this.avgServiceSeconds = avgServiceSeconds;
    }

    public double getAvgWaitMinutes() {
        return Math.round((avgWaitSeconds / 60.0) * 10.0) / 10.0;
    }

    public double getAvgServiceMinutes() {
        return Math.round((avgServiceSeconds / 60.0) * 10.0) / 10.0;
    }

    public double getNoShowRate() {
        if (totalTokens == 0) return 0.0;
        return Math.round(((double) skippedTokens / totalTokens * 100.0) * 10.0) / 10.0;
    }
}

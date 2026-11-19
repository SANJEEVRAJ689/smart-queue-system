package com.smartqueue.model;

import java.time.LocalDate;

/**
 * Entity representing the daily atomic token counter for a specific service.
 */
public class TokenSequence {
    private int serviceId;
    private LocalDate queueDate;
    private int lastNumber;

    public TokenSequence() {}

    public TokenSequence(int serviceId, LocalDate queueDate, int lastNumber) {
        this.serviceId = serviceId;
        this.queueDate = queueDate;
        this.lastNumber = lastNumber;
    }

    public int getServiceId() {
        return serviceId;
    }

    public void setServiceId(int serviceId) {
        this.serviceId = serviceId;
    }

    public LocalDate getQueueDate() {
        return queueDate;
    }

    public void setQueueDate(LocalDate queueDate) {
        this.queueDate = queueDate;
    }

    public int getLastNumber() {
        return lastNumber;
    }

    public void setLastNumber(int lastNumber) {
        this.lastNumber = lastNumber;
    }
}

package com.smartqueue.model;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Entity representing a customer's queue token.
 * Implements Comparable using the priority rank rule with starvation prevention.
 */
public class Token implements Comparable<Token> {
    private int id;
    private String tokenNo;            // e.g. "AD-001"
    private int serviceId;
    private String customerName;
    private String phone;
    private Priority priority;         // NORMAL, SENIOR, EMERGENCY
    private TokenStatus status;        // WAITING, CALLED, SERVING, COMPLETED, SKIPPED, CANCELLED
    private Integer counterId;
    private Integer customerId;
    private String trackingCode;
    private Integer durationSeconds;
    private int recallCount = 0;
    private LocalDateTime createdAt;
    private LocalDateTime calledAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private Instant createdAtInstant;
    private Instant calledAtInstant;
    private Instant startedAtInstant;
    private Instant completedAtInstant;
    private LocalDate queueDate;

    // Display / UI Helper properties
    private String serviceName;
    private String counterName;
    private int queuePosition;
    private int estimatedWaitMinutes;

    public Token() {
        this.status = TokenStatus.WAITING;
        this.priority = Priority.NORMAL;
        this.createdAtInstant = Instant.now();
        this.createdAt = LocalDateTime.ofInstant(this.createdAtInstant, ZoneId.systemDefault());
        this.queueDate = LocalDate.now();
    }

    public Token(int id, String tokenNo, int serviceId, String customerName, String phone,
                 Priority priority, TokenStatus status, LocalDate queueDate) {
        this.id = id;
        this.tokenNo = tokenNo;
        this.serviceId = serviceId;
        this.customerName = customerName;
        this.phone = phone;
        this.priority = priority != null ? priority : Priority.NORMAL;
        this.status = status != null ? status : TokenStatus.WAITING;
        this.createdAtInstant = Instant.now();
        this.createdAt = LocalDateTime.ofInstant(this.createdAtInstant, ZoneId.systemDefault());
        this.queueDate = queueDate != null ? queueDate : LocalDate.now();
    }

    /**
     * Calculates the effective priority rank taking starvation prevention into account:
     * EMERGENCY = 0
     * SENIOR = 1
     * NORMAL = 2
     * Starvation prevention: If NORMAL has waited >= 30 minutes, it is elevated to rank 1.
     */
    public int getEffectiveRank(int starvationThresholdMinutes) {
        if (priority == Priority.EMERGENCY) return 0;
        if (priority == Priority.SENIOR) return 1;

        if (createdAt != null) {
            long minutesWaited = Duration.between(createdAt, LocalDateTime.now()).toMinutes();
            if (minutesWaited >= starvationThresholdMinutes) {
                return 1; // Elevated to Senior rank
            }
        }
        return 2;
    }

    @Override
    public int compareTo(Token other) {
        if (other == null) return -1;

        int rankThis = this.getEffectiveRank(30);
        int rankOther = other.getEffectiveRank(30);

        if (rankThis != rankOther) {
            return Integer.compare(rankThis, rankOther);
        }

        // Within same rank: FIFO by creation time
        if (this.createdAt != null && other.createdAt != null) {
            int timeCmp = this.createdAt.compareTo(other.createdAt);
            if (timeCmp != 0) return timeCmp;
        }
        return Integer.compare(this.id, other.id);
    }

    // Getters and Setters

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTokenNo() {
        return tokenNo;
    }

    public void setTokenNo(String tokenNo) {
        this.tokenNo = tokenNo;
    }

    // Convenience aliases
    public String getTokenNumber() {
        return tokenNo;
    }

    public void setTokenNumber(String tokenNumber) {
        this.tokenNo = tokenNumber;
    }

    public int getServiceId() {
        return serviceId;
    }

    public void setServiceId(int serviceId) {
        this.serviceId = serviceId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getCustomerPhone() {
        return phone;
    }

    public void setCustomerPhone(String phone) {
        this.phone = phone;
    }

    public Priority getPriority() {
        return priority;
    }

    public void setPriority(Priority priority) {
        this.priority = priority;
    }

    public void setPriority(boolean isPriority) {
        this.priority = isPriority ? Priority.SENIOR : Priority.NORMAL;
    }

    public String getPriorityReason() {
        return priority != null ? priority.getDisplayName() : "Regular";
    }

    public void setPriorityReason(String reason) {
        if (reason != null && (reason.toLowerCase().contains("emergency") || reason.toLowerCase().contains("medical"))) {
            this.priority = Priority.EMERGENCY;
        } else if (reason != null && reason.toLowerCase().contains("senior")) {
            this.priority = Priority.SENIOR;
        }
    }

    public TokenStatus getStatus() {
        return status;
    }

    public void setStatus(TokenStatus status) {
        this.status = status;
    }

    public Integer getCounterId() {
        return counterId;
    }

    public void setCounterId(Integer counterId) {
        this.counterId = counterId;
    }

    public Integer getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Integer customerId) {
        this.customerId = customerId;
    }

    public String getTrackingCode() {
        return trackingCode;
    }

    public void setTrackingCode(String trackingCode) {
        this.trackingCode = trackingCode;
    }

    public void setDurationSeconds(Integer durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public int getRecallCount() {
        return recallCount;
    }

    public void setRecallCount(int recallCount) {
        this.recallCount = recallCount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
        if (createdAt != null) {
            this.createdAtInstant = createdAt.atZone(ZoneId.systemDefault()).toInstant();
        } else {
            this.createdAtInstant = null;
        }
    }

    public Instant getCreatedAtInstant() {
        return createdAtInstant;
    }

    public void setCreatedAtInstant(Instant createdAtInstant) {
        this.createdAtInstant = createdAtInstant;
        this.createdAt = createdAtInstant != null ? LocalDateTime.ofInstant(createdAtInstant, ZoneId.systemDefault()) : null;
    }

    public LocalDateTime getCalledAt() {
        return calledAt;
    }

    public void setCalledAt(LocalDateTime calledAt) {
        this.calledAt = calledAt;
        if (calledAt != null) {
            this.calledAtInstant = calledAt.atZone(ZoneId.systemDefault()).toInstant();
        } else {
            this.calledAtInstant = null;
        }
    }

    public Instant getCalledAtInstant() {
        return calledAtInstant;
    }

    public void setCalledAtInstant(Instant calledAtInstant) {
        this.calledAtInstant = calledAtInstant;
        this.calledAt = calledAtInstant != null ? LocalDateTime.ofInstant(calledAtInstant, ZoneId.systemDefault()) : null;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
        if (startedAt != null) {
            this.startedAtInstant = startedAt.atZone(ZoneId.systemDefault()).toInstant();
        } else {
            this.startedAtInstant = null;
        }
    }

    public Instant getStartedAtInstant() {
        return startedAtInstant;
    }

    public void setStartedAtInstant(Instant startedAtInstant) {
        this.startedAtInstant = startedAtInstant;
        this.startedAt = startedAtInstant != null ? LocalDateTime.ofInstant(startedAtInstant, ZoneId.systemDefault()) : null;
    }

    public LocalDateTime getServedAt() {
        return startedAt;
    }

    public void setServedAt(LocalDateTime servedAt) {
        setStartedAt(servedAt);
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
        if (completedAt != null) {
            this.completedAtInstant = completedAt.atZone(ZoneId.systemDefault()).toInstant();
        } else {
            this.completedAtInstant = null;
        }
    }

    public Instant getCompletedAtInstant() {
        return completedAtInstant;
    }

    public void setCompletedAtInstant(Instant completedAtInstant) {
        this.completedAtInstant = completedAtInstant;
        this.completedAt = completedAtInstant != null ? LocalDateTime.ofInstant(completedAtInstant, ZoneId.systemDefault()) : null;
    }

    /**
     * Calculates the session duration in seconds (completed_at minus started_at).
     * Returns null if session is not yet completed or started_at is missing.
     */
    public Long getDurationSeconds() {
        if (durationSeconds != null) {
            return durationSeconds.longValue();
        }
        if (startedAtInstant != null && completedAtInstant != null) {
            return Math.max(0L, Duration.between(startedAtInstant, completedAtInstant).getSeconds());
        }
        if (startedAt != null && completedAt != null) {
            return Math.max(0L, Duration.between(startedAt, completedAt).getSeconds());
        }
        return null;
    }

    /**
     * Returns formatted session duration string e.g. "5m 20s" or "--" if not completed.
     */
    public String getFormattedDuration() {
        Long sec = getDurationSeconds();
        if (sec == null) return "--";
        int totalSec = sec.intValue();
        int mins = totalSec / 60;
        int s = totalSec % 60;
        if (mins > 0) {
            return mins + "m " + s + "s";
        } else {
            return s + "s";
        }
    }

    public LocalDate getQueueDate() {
        return queueDate;
    }

    public void setQueueDate(LocalDate queueDate) {
        this.queueDate = queueDate;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getCounterName() {
        return counterName;
    }

    public void setCounterName(String counterName) {
        this.counterName = counterName;
    }

    public Integer getCounterNumber() {
        if (counterName != null && counterName.contains(" ")) {
            try {
                String[] parts = counterName.split(" ");
                for (String p : parts) {
                    if (p.matches("\\d+")) return Integer.parseInt(p);
                }
            } catch (Exception ignored) {}
        }
        return counterId;
    }

    public void setCounterNumber(Integer num) {
        if (num != null) this.counterName = "Counter " + num;
    }

    public int getQueuePosition() {
        return queuePosition;
    }

    public void setQueuePosition(int queuePosition) {
        this.queuePosition = queuePosition;
    }

    public int getEstimatedWaitMinutes() {
        return estimatedWaitMinutes;
    }

    public void setEstimatedWaitMinutes(int estimatedWaitMinutes) {
        this.estimatedWaitMinutes = estimatedWaitMinutes;
    }

    public boolean isPriority() {
        return priority != Priority.NORMAL;
    }

    @Override
    public String toString() {
        return tokenNo + " [" + status + "] - " + customerName + " (" + priority + ")";
    }
}

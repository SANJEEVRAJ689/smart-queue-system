package com.smartqueue.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public class CustomerDtos {

    public static class BookTokenRequest {
        @NotNull(message = "Service ID is required")
        private Integer serviceId;

        @NotBlank(message = "Customer name is required")
        private String customerName;

        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = "^[0-9+ -]{7,15}$", message = "Invalid phone number format")
        private String phone;

        private Boolean priority = false;
        private String priorityType; // "NORMAL", "SENIOR", "EMERGENCY"

        public BookTokenRequest() {}

        public Integer getServiceId() {
            return serviceId;
        }

        public void setServiceId(Integer serviceId) {
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

        public Boolean getPriority() {
            return priority;
        }

        @com.fasterxml.jackson.annotation.JsonSetter("priority")
        public void setPriority(Object val) {
            if (val instanceof Boolean b) {
                this.priority = b;
                if (b && this.priorityType == null) this.priorityType = "SENIOR";
            } else if (val instanceof String s) {
                this.priorityType = s.toUpperCase();
                this.priority = !"NORMAL".equalsIgnoreCase(s);
            }
        }

        public String getPriorityType() {
            return priorityType;
        }

        public void setPriorityType(String priorityType) {
            this.priorityType = priorityType;
        }
    }

    public static class BookTokenResponse {
        private String tokenNo;
        private String trackingCode;
        private String serviceName;
        private String customerName;
        private String phoneMasked;
        private Integer queuePosition;
        private Integer estimatedWaitMinutes;
        private String serverTimeUtc;

        public BookTokenResponse() {}

        public String getTokenNo() {
            return tokenNo;
        }

        public void setTokenNo(String tokenNo) {
            this.tokenNo = tokenNo;
        }

        public String getTrackingCode() {
            return trackingCode;
        }

        public void setTrackingCode(String trackingCode) {
            this.trackingCode = trackingCode;
        }

        public String getServiceName() {
            return serviceName;
        }

        public void setServiceName(String serviceName) {
            this.serviceName = serviceName;
        }

        public String getCustomerName() {
            return customerName;
        }

        public void setCustomerName(String customerName) {
            this.customerName = customerName;
        }

        public String getPhoneMasked() {
            return phoneMasked;
        }

        public void setPhoneMasked(String phoneMasked) {
            this.phoneMasked = phoneMasked;
        }

        public Integer getQueuePosition() {
            return queuePosition;
        }

        public void setQueuePosition(Integer queuePosition) {
            this.queuePosition = queuePosition;
        }

        public Integer getEstimatedWaitMinutes() {
            return estimatedWaitMinutes;
        }

        public void setEstimatedWaitMinutes(Integer estimatedWaitMinutes) {
            this.estimatedWaitMinutes = estimatedWaitMinutes;
        }

        public String getServerTimeUtc() {
            return serverTimeUtc;
        }

        public void setServerTimeUtc(String serverTimeUtc) {
            this.serverTimeUtc = serverTimeUtc;
        }
    }

    public static class TrackSessionRequest {
        @NotBlank(message = "Token number is required")
        private String tokenNo;

        @NotBlank(message = "Phone number is required")
        private String phone;

        public TrackSessionRequest() {}

        public TrackSessionRequest(String tokenNo, String phone) {
            this.tokenNo = tokenNo;
            this.phone = phone;
        }

        public String getTokenNo() {
            return tokenNo;
        }

        public void setTokenNo(String tokenNo) {
            this.tokenNo = tokenNo;
        }

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }
    }

    public static class TrackTokenResponse {
        private String tokenNo;
        private String serviceName;
        private String status;
        private String priority;
        private String counterName;
        private String customerName;
        private String phoneMasked;
        private Integer queuePosition;
        private Integer estimatedWaitMinutes;
        private String startedAtUtc;
        private String completedAtUtc;
        private String serverTimeUtc;
        private Integer durationSeconds;
        private String trackingCode;

        public TrackTokenResponse() {}

        public String getTokenNo() {
            return tokenNo;
        }

        public void setTokenNo(String tokenNo) {
            this.tokenNo = tokenNo;
        }

        public String getServiceName() {
            return serviceName;
        }

        public void setServiceName(String serviceName) {
            this.serviceName = serviceName;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getPriority() {
            return priority;
        }

        public void setPriority(String priority) {
            this.priority = priority;
        }

        public String getCounterName() {
            return counterName;
        }

        public void setCounterName(String counterName) {
            this.counterName = counterName;
        }

        public String getCustomerName() {
            return customerName;
        }

        public void setCustomerName(String customerName) {
            this.customerName = customerName;
        }

        public String getPhoneMasked() {
            return phoneMasked;
        }

        public void setPhoneMasked(String phoneMasked) {
            this.phoneMasked = phoneMasked;
        }

        public Integer getQueuePosition() {
            return queuePosition;
        }

        public void setQueuePosition(Integer queuePosition) {
            this.queuePosition = queuePosition;
        }

        public Integer getEstimatedWaitMinutes() {
            return estimatedWaitMinutes;
        }

        public void setEstimatedWaitMinutes(Integer estimatedWaitMinutes) {
            this.estimatedWaitMinutes = estimatedWaitMinutes;
        }

        public String getStartedAtUtc() {
            return startedAtUtc;
        }

        public void setStartedAtUtc(String startedAtUtc) {
            this.startedAtUtc = startedAtUtc;
        }

        public String getCompletedAtUtc() {
            return completedAtUtc;
        }

        public void setCompletedAtUtc(String completedAtUtc) {
            this.completedAtUtc = completedAtUtc;
        }

        public String getServerTimeUtc() {
            return serverTimeUtc;
        }

        public void setServerTimeUtc(String serverTimeUtc) {
            this.serverTimeUtc = serverTimeUtc;
        }

        public Integer getDurationSeconds() {
            return durationSeconds;
        }

        public void setDurationSeconds(Integer durationSeconds) {
            this.durationSeconds = durationSeconds;
        }

        public String getTrackingCode() {
            return trackingCode;
        }

        public void setTrackingCode(String trackingCode) {
            this.trackingCode = trackingCode;
        }
    }
}

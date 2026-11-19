package com.smartqueue.web.dto;

public class StaffDtos {

    public static class StaffTokenResponse {
        private Integer id;
        private String tokenNo;
        private Integer serviceId;
        private String serviceName;
        private String customerName;
        private String phone;
        private String status;
        private String priority;
        private Integer counterId;
        private String counterName;
        private String calledAtUtc;
        private String startedAtUtc;
        private String completedAtUtc;
        private String serverTimeUtc;
        private Integer durationSeconds;
        private Integer recallCount;

        public StaffTokenResponse() {}

        public Integer getId() {
            return id;
        }

        public void setId(Integer id) {
            this.id = id;
        }

        public String getTokenNo() {
            return tokenNo;
        }

        public void setTokenNo(String tokenNo) {
            this.tokenNo = tokenNo;
        }

        public Integer getServiceId() {
            return serviceId;
        }

        public void setServiceId(Integer serviceId) {
            this.serviceId = serviceId;
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

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
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

        public Integer getCounterId() {
            return counterId;
        }

        public void setCounterId(Integer counterId) {
            this.counterId = counterId;
        }

        public String getCounterName() {
            return counterName;
        }

        public void setCounterName(String counterName) {
            this.counterName = counterName;
        }

        public String getCalledAtUtc() {
            return calledAtUtc;
        }

        public void setCalledAtUtc(String calledAtUtc) {
            this.calledAtUtc = calledAtUtc;
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

        public Integer getRecallCount() {
            return recallCount;
        }

        public void setRecallCount(Integer recallCount) {
            this.recallCount = recallCount;
        }
    }

    public static class TransferRequest {
        private Integer targetServiceId;
        private Integer targetCounterId;
        private Integer sourceCounterId;

        public TransferRequest() {}

        public TransferRequest(Integer targetServiceId) {
            this.targetServiceId = targetServiceId;
        }

        public TransferRequest(Integer targetServiceId, Integer targetCounterId, Integer sourceCounterId) {
            this.targetServiceId = targetServiceId;
            this.targetCounterId = targetCounterId;
            this.sourceCounterId = sourceCounterId;
        }

        public Integer getTargetServiceId() {
            return targetServiceId;
        }

        public void setTargetServiceId(Integer targetServiceId) {
            this.targetServiceId = targetServiceId;
        }

        public Integer getTargetCounterId() {
            return targetCounterId;
        }

        public void setTargetCounterId(Integer targetCounterId) {
            this.targetCounterId = targetCounterId;
        }

        public Integer getSourceCounterId() {
            return sourceCounterId;
        }

        public void setSourceCounterId(Integer sourceCounterId) {
            this.sourceCounterId = sourceCounterId;
        }
    }

    public static class CounterStatusRequest {
        private String status; // "OPEN" or "CLOSED"

        public CounterStatusRequest() {}

        public CounterStatusRequest(String status) {
            this.status = status;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }
}

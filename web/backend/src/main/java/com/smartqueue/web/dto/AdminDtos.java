package com.smartqueue.web.dto;

import java.util.List;
import java.util.Map;

public class AdminDtos {

    public static class AnalyticsSummaryResponse {
        private long totalTokensToday;
        private long waitingTokens;
        private long servingTokens;
        private long completedTokens;
        private long skippedTokens;
        private long cancelledCount;
        private double avgWaitMinutes;
        private double avgServiceMinutes;
        private double noShowRate;
        private String serverTimeUtc;
        private List<Map<String, Object>> hourlyDistribution;

        public AnalyticsSummaryResponse() {}

        public long getTotalTokensToday() {
            return totalTokensToday;
        }

        public void setTotalTokensToday(long totalTokensToday) {
            this.totalTokensToday = totalTokensToday;
        }

        public long getWaitingTokens() {
            return waitingTokens;
        }

        public void setWaitingTokens(long waitingTokens) {
            this.waitingTokens = waitingTokens;
        }

        public long getServingTokens() {
            return servingTokens;
        }

        public void setServingTokens(long servingTokens) {
            this.servingTokens = servingTokens;
        }

        public long getCompletedTokens() {
            return completedTokens;
        }

        public void setCompletedTokens(long completedTokens) {
            this.completedTokens = completedTokens;
        }

        public long getSkippedTokens() {
            return skippedTokens;
        }

        public void setSkippedTokens(long skippedTokens) {
            this.skippedTokens = skippedTokens;
        }

        public long getCancelledCount() {
            return cancelledCount;
        }

        public void setCancelledCount(long cancelledCount) {
            this.cancelledCount = cancelledCount;
        }

        // Frontend AnalyticsSummary interface property compatibility aliases
        public long getWaitingCount() {
            return waitingTokens;
        }

        public long getServingCount() {
            return servingTokens;
        }

        public long getCompletedCount() {
            return completedTokens;
        }

        public long getSkippedCount() {
            return skippedTokens;
        }

        public double getAvgWaitMinutes() {
            return avgWaitMinutes;
        }

        public void setAvgWaitMinutes(double avgWaitMinutes) {
            this.avgWaitMinutes = avgWaitMinutes;
        }

        public double getAvgServiceMinutes() {
            return avgServiceMinutes;
        }

        public void setAvgServiceMinutes(double avgServiceMinutes) {
            this.avgServiceMinutes = avgServiceMinutes;
        }

        public double getNoShowRate() {
            return noShowRate;
        }

        public void setNoShowRate(double noShowRate) {
            this.noShowRate = noShowRate;
        }

        public String getServerTimeUtc() {
            return serverTimeUtc;
        }

        public void setServerTimeUtc(String serverTimeUtc) {
            this.serverTimeUtc = serverTimeUtc;
        }

        public List<Map<String, Object>> getHourlyDistribution() {
            return hourlyDistribution;
        }

        public void setHourlyDistribution(List<Map<String, Object>> hourlyDistribution) {
            this.hourlyDistribution = hourlyDistribution;
        }
    }

    public static class CreateStaffRequest {
        private String username;
        private String password;
        private String fullName;
        private String role; // "STAFF" or "ADMIN"

        public CreateStaffRequest() {}

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getFullName() {
            return fullName;
        }

        public void setFullName(String fullName) {
            this.fullName = fullName;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }
    }

    public static class HourlyStatResponse {
        private int hour;
        private String label;
        private int count;

        public HourlyStatResponse() {}

        public HourlyStatResponse(int hour, String label, int count) {
            this.hour = hour;
            this.label = label;
            this.count = count;
        }

        public int getHour() {
            return hour;
        }

        public void setHour(int hour) {
            this.hour = hour;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }
    }

    public static class ServiceStatResponse {
        private int serviceId;
        private String serviceName;
        private int totalTokens;
        private int completedTokens;
        private double avgWaitMinutes;

        public ServiceStatResponse() {}

        public ServiceStatResponse(int serviceId, String serviceName, int totalTokens, int completedTokens, double avgWaitMinutes) {
            this.serviceId = serviceId;
            this.serviceName = serviceName;
            this.totalTokens = totalTokens;
            this.completedTokens = completedTokens;
            this.avgWaitMinutes = avgWaitMinutes;
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

        public double getAvgWaitMinutes() {
            return avgWaitMinutes;
        }

        public void setAvgWaitMinutes(double avgWaitMinutes) {
            this.avgWaitMinutes = avgWaitMinutes;
        }
    }

    public static class CustomerResponse {
        private Integer id;
        private String name;
        private String phone;
        private String email;
        private java.time.Instant createdAt;
        private Integer totalTokensBooked;

        public CustomerResponse() {}

        public CustomerResponse(Integer id, String name, String phone, String email, java.time.Instant createdAt, Integer totalTokensBooked) {
            this.id = id;
            this.name = name;
            this.phone = phone;
            this.email = email;
            this.createdAt = createdAt;
            this.totalTokensBooked = totalTokensBooked;
        }

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

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public java.time.Instant getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(java.time.Instant createdAt) {
            this.createdAt = createdAt;
        }

        public Integer getTotalTokensBooked() {
            return totalTokensBooked;
        }

        public void setTotalTokensBooked(Integer totalTokensBooked) {
            this.totalTokensBooked = totalTokensBooked;
        }
    }
}

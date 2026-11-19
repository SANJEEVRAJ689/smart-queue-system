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
}

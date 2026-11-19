package com.smartqueue.web.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

public class AuthDtos {

    public static class LoginRequest {
        @NotBlank(message = "Username is required")
        private String username;

        @NotBlank(message = "Password is required")
        private String password;

        public LoginRequest() {}

        public LoginRequest(String username, String password) {
            this.username = username;
            this.password = password;
        }

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
    }

    public static class LoginResponse {
        private String token;
        private String role;
        private String username;
        private String fullName;
        private String serverTimeUtc;

        public LoginResponse() {}

        public LoginResponse(String token, String role, String username, String fullName) {
            this.token = token;
            this.role = role;
            this.username = username;
            this.fullName = fullName;
            this.serverTimeUtc = Instant.now().toString();
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getFullName() {
            return fullName;
        }

        public void setFullName(String fullName) {
            this.fullName = fullName;
        }

        public String getServerTimeUtc() {
            return serverTimeUtc;
        }

        public void setServerTimeUtc(String serverTimeUtc) {
            this.serverTimeUtc = serverTimeUtc;
        }
    }
}

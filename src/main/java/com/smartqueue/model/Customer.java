package com.smartqueue.model;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Model representing a Customer profile.
 * Customers have a unique phone number and can have multiple queue visits over time.
 */
public class Customer {
    private int id;
    private String fullName;
    private String phone;
    private String email;
    private Instant createdAt;
    private Instant updatedAt;

    public Customer() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Customer(int id, String fullName, String phone, String email) {
        this.id = id;
        this.fullName = fullName;
        this.phone = phone;
        this.email = email;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    /**
     * Helper to return masked phone number (e.g. +91 ******3201) to protect customer privacy.
     */
    public String getMaskedPhone() {
        if (phone == null || phone.length() < 4) return "****";
        int len = phone.length();
        String last4 = phone.substring(len - 4);
        return "*".repeat(len - 4) + last4;
    }

    @Override
    public String toString() {
        return fullName + " (" + getMaskedPhone() + ")";
    }
}

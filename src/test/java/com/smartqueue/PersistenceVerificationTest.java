package com.smartqueue;

import com.smartqueue.dao.TokenDao;
import com.smartqueue.dao.impl.TokenDaoImpl;
import com.smartqueue.model.Token;
import com.smartqueue.model.TokenStatus;
import com.smartqueue.service.TokenService;
import com.smartqueue.util.DatabaseConnection;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class PersistenceVerificationTest {

    @Test
    public void testTokenPersistenceAcrossRestarts() {
        assertFalse(DatabaseConnection.isOfflineFallback(), "Must connect to real MySQL, not offline fallback");

        TokenService tokenService = new TokenService();
        String customerName = "Persistence Customer";
        String phone = "9887766554";

        Optional<Token> bookedOpt = tokenService.bookToken(1, customerName, phone, false, null);
        assertTrue(bookedOpt.isPresent(), "Booked token must be present");
        Token booked = bookedOpt.get();
        int bookedId = booked.getId();
        assertTrue(bookedId > 0, "Token ID must be greater than 0");
        assertNotNull(booked.getTrackingCode(), "Tracking code must be present");

        // Close connection pool to simulate application shutdown
        DatabaseConnection.shutdown();

        // Reconnect fresh connection from MySQL
        TokenDao tokenDao = new TokenDaoImpl();
        Optional<Token> retrieved = tokenDao.findById(bookedId);
        assertTrue(retrieved.isPresent(), "Token must persist in MySQL database after pool reset");
        assertEquals(customerName, retrieved.get().getCustomerName());
        assertEquals(phone, retrieved.get().getPhone());
        assertEquals(TokenStatus.WAITING, retrieved.get().getStatus());
        assertEquals(booked.getTrackingCode(), retrieved.get().getTrackingCode());
    }
}

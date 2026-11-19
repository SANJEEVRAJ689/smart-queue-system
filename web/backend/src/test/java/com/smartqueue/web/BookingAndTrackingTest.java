package com.smartqueue.web;

import com.smartqueue.web.dto.CustomerDtos.BookTokenRequest;
import com.smartqueue.web.dto.CustomerDtos.BookTokenResponse;
import com.smartqueue.web.dto.CustomerDtos.TrackTokenResponse;
import com.smartqueue.web.service.CustomerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class BookingAndTrackingTest {

    @Autowired
    private CustomerService customerService;

    @Test
    @DisplayName("Guest booking generates tracking code and masks phone number")
    void testBookingAndTrackingFlow() {
        BookTokenRequest req = new BookTokenRequest();
        req.setServiceId(1);
        req.setCustomerName("Integration Tester");
        req.setPhone("9876543210");
        req.setPriority(false);

        BookTokenResponse booked = customerService.bookToken(req, "127.0.0.1");
        assertNotNull(booked);
        assertNotNull(booked.getTokenNo());
        assertNotNull(booked.getTrackingCode());
        assertEquals("Integration Tester", booked.getCustomerName());
        assertTrue(booked.getPhoneMasked().contains("*"), "Phone number must be masked");

        // Track by tracking code
        TrackTokenResponse tracked = customerService.trackByTrackingCode(booked.getTrackingCode());
        assertNotNull(tracked);
        assertEquals(booked.getTokenNo(), tracked.getTokenNo());
        assertEquals("WAITING", tracked.getStatus());
        assertEquals("Integration", tracked.getCustomerName(), "First name only should be returned");
        assertNotNull(tracked.getServerTimeUtc());

        // Cancel token
        boolean cancelled = customerService.cancelToken(booked.getTrackingCode());
        assertTrue(cancelled);

        TrackTokenResponse afterCancel = customerService.trackByTrackingCode(booked.getTrackingCode());
        assertEquals("CANCELLED", afterCancel.getStatus());
    }
}

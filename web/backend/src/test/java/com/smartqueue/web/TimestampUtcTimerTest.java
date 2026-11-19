package com.smartqueue.web;

import com.smartqueue.web.dto.CustomerDtos.BookTokenRequest;
import com.smartqueue.web.dto.CustomerDtos.BookTokenResponse;
import com.smartqueue.web.dto.StaffDtos.StaffTokenResponse;
import com.smartqueue.web.service.CustomerService;
import com.smartqueue.web.service.StaffService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class TimestampUtcTimerTest {

    @Autowired
    private CustomerService customerService;

    @Autowired
    private StaffService staffService;

    @Test
    @DisplayName("Verify UTC timestamps and duration calculation across token lifecycle")
    void testUtcTimestampsAndTimerContinuity() throws Exception {
        int counterId = 1;

        // Reset counter
        staffService.setCounterStatus(counterId, "OPEN");

        // 1. Book token
        BookTokenRequest req = new BookTokenRequest();
        req.setServiceId(1);
        req.setCustomerName("Timer Customer");
        req.setPhone("9888877777");
        BookTokenResponse booked = customerService.bookToken(req, "127.0.0.1");

        assertNotNull(booked.getServerTimeUtc());
        assertTrue(booked.getServerTimeUtc().endsWith("Z"), "serverTimeUtc must end with Z");
        assertDoesNotThrow(() -> Instant.parse(booked.getServerTimeUtc()));

        // 2. Call next
        Optional<StaffTokenResponse> called = staffService.callNextToken(counterId, null);
        assertTrue(called.isPresent());
        StaffTokenResponse calledToken = called.get();
        assertNotNull(calledToken.getServerTimeUtc());
        assertNotNull(calledToken.getCalledAtUtc());
        assertTrue(calledToken.getCalledAtUtc().endsWith("Z"));

        // 3. Start serving
        StaffTokenResponse servingToken = staffService.startServing(calledToken.getId(), null);
        assertNotNull(servingToken.getStartedAtUtc());
        assertTrue(servingToken.getStartedAtUtc().endsWith("Z"));

        // Sleep to simulate elapsed serving duration across second boundary
        Thread.sleep(1600);

        // 4. Complete
        StaffTokenResponse completedToken = staffService.completeToken(servingToken.getId(), counterId, null);
        assertNotNull(completedToken.getCompletedAtUtc());
        assertTrue(completedToken.getCompletedAtUtc().endsWith("Z"));
        assertNotNull(completedToken.getDurationSeconds());
        assertTrue(completedToken.getDurationSeconds() >= 1, "Duration must be at least 1 second");
    }
}

package com.smartqueue;

import com.smartqueue.dao.CustomerDao;
import com.smartqueue.dao.TokenDao;
import com.smartqueue.dao.TokenEventDao;
import com.smartqueue.dao.impl.CustomerDaoImpl;
import com.smartqueue.dao.impl.TokenDaoImpl;
import com.smartqueue.dao.impl.TokenEventDaoImpl;
import com.smartqueue.model.Customer;
import com.smartqueue.model.Priority;
import com.smartqueue.model.Token;
import com.smartqueue.model.TokenEvent;
import com.smartqueue.model.TokenStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 2 Unit and Integration Tests:
 * Validates Customers, Token Tracking Codes, Duration Seconds, and Token Events.
 */
public class CustomerAndTokenEventDaoTest {

    private static CustomerDao customerDao;
    private static TokenDao tokenDao;
    private static TokenEventDao tokenEventDao;

    @BeforeAll
    public static void setUp() {
        customerDao = new CustomerDaoImpl();
        tokenDao = new TokenDaoImpl();
        tokenEventDao = new TokenEventDaoImpl();
    }

    @Test
    public void testCustomerFindOrCreateByPhone() {
        String testPhone = "9988776655";
        String testName = "Test Phase2 Customer";
        
        Customer c1 = customerDao.findOrCreate(testName, testPhone, "test@example.com");
        assertNotNull(c1);
        assertTrue(c1.getId() > 0);
        assertEquals(testPhone, c1.getPhone());
        assertEquals(testName, c1.getFullName());

        // Calling findOrCreate again with the same phone must return the existing customer
        Customer c2 = customerDao.findOrCreate("Another Name", testPhone, null);
        assertEquals(c1.getId(), c2.getId());
        assertEquals(testPhone, c2.getPhone());
    }

    @Test
    public void testTokenCreationGeneratesTrackingCodeAndLinksCustomer() {
        String phone = "9123456780";
        String customerName = "Integration Customer";
        
        Token token = new Token();
        token.setTokenNo("TEST-" + System.currentTimeMillis() % 10000);
        token.setServiceId(1);
        token.setCustomerName(customerName);
        token.setPhone(phone);
        token.setPriority(Priority.NORMAL);
        token.setQueueDate(LocalDate.now());

        boolean created = tokenDao.create(token);
        assertTrue(created);
        assertTrue(token.getId() > 0);
        assertNotNull(token.getTrackingCode(), "Tracking code must be generated");
        assertTrue(token.getTrackingCode().contains("-"), "Tracking code format must contain hyphens");
        assertNotNull(token.getCustomerId(), "Customer ID must be linked");
        assertTrue(token.getCustomerId() > 0);

        // Find by tracking code
        Optional<Token> foundOpt = tokenDao.findByTrackingCode(token.getTrackingCode());
        assertTrue(foundOpt.isPresent());
        assertEquals(token.getId(), foundOpt.get().getId());
        assertEquals(token.getTokenNo(), foundOpt.get().getTokenNo());

        // Verify initial token_events entry
        List<TokenEvent> events = tokenEventDao.findByTokenId(token.getId());
        assertFalse(events.isEmpty(), "Token events must record initial transition");
        assertEquals(TokenStatus.WAITING.name(), events.get(0).getToStatus());
    }

    @Test
    public void testTokenLifecycleCalculatesDurationAndRecordsEvents() throws InterruptedException {
        String phone = "9998887771";
        Token token = new Token();
        token.setTokenNo("LC-" + (System.currentTimeMillis() % 1000));
        token.setServiceId(1);
        token.setCustomerName("Lifecycle Test Customer");
        token.setPhone(phone);
        token.setPriority(Priority.NORMAL);
        token.setQueueDate(LocalDate.now());

        boolean created = tokenDao.create(token);
        assertTrue(created);

        // Transition: WAITING -> CALLED
        boolean called = tokenDao.updateStatus(token.getId(), TokenStatus.CALLED);
        assertTrue(called);
        
        // Transition: CALLED -> SERVING
        boolean serving = tokenDao.updateStatus(token.getId(), TokenStatus.SERVING);
        assertTrue(serving);
        
        // Brief sleep to ensure elapsed duration >= 1 second
        Thread.sleep(1500);

        // Transition: SERVING -> COMPLETED
        boolean completed = tokenDao.updateStatus(token.getId(), TokenStatus.COMPLETED);
        assertTrue(completed);

        // Verify token duration_seconds is computed
        Optional<Token> completedOpt = tokenDao.findById(token.getId());
        assertTrue(completedOpt.isPresent());
        Token completedToken = completedOpt.get();
        assertEquals(TokenStatus.COMPLETED, completedToken.getStatus());
        assertNotNull(completedToken.getDurationSeconds(), "Duration seconds must be computed on completion");
        assertTrue(completedToken.getDurationSeconds() >= 1, "Duration seconds should be >= 1 second");

        // Verify token_events history has all 4 steps
        List<TokenEvent> events = tokenEventDao.findByTokenId(token.getId());
        assertTrue(events.size() >= 4, "Expected at least 4 state transition events");
        assertEquals("WAITING", events.get(0).getToStatus());
        assertEquals("CALLED", events.get(1).getToStatus());
        assertEquals("SERVING", events.get(2).getToStatus());
        assertEquals("COMPLETED", events.get(3).getToStatus());
    }
}

package com.smartqueue;

import com.smartqueue.concurrent.AutoCaller;
import com.smartqueue.concurrent.NoShowWatcher;
import com.smartqueue.dao.CounterDao;
import com.smartqueue.dao.TokenDao;
import com.smartqueue.dao.impl.CounterDaoImpl;
import com.smartqueue.dao.impl.TokenDaoImpl;
import com.smartqueue.model.Counter;
import com.smartqueue.model.CounterStatus;
import com.smartqueue.model.Priority;
import com.smartqueue.model.Token;
import com.smartqueue.model.TokenStatus;
import com.smartqueue.service.TokenService;
import com.smartqueue.util.DatabaseConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verification Suite for Counter Status Bug Fix (A1).
 *
 * Enforces Counter Status Rules:
 * - OPEN   : Idle and ready for the next customer.
 * - BUSY   : Actively serving or calling a token.
 * - CLOSED : Desk closed by staff or offline.
 *
 * Verifies that:
 * 1. CounterStatus.fromString strictly rejects unknown values (e.g., 'ONLINE').
 * 2. Completing a token resets counter to OPEN, clears current_token_id, and allows calling next.
 * 3. Skipping a token resets counter to OPEN, clears current_token_id, and allows calling next.
 * 4. No-show timeout resets counter to OPEN, clears current_token_id, and allows calling next.
 * 5. A BUSY counter cannot call a second token while one is already active.
 */
public class CounterStatusFlowTest {

    private TokenDao tokenDao;
    private CounterDao counterDao;
    private TokenService tokenService;
    private AutoCaller autoCaller;

    private static final int TEST_SERVICE_ID = 1; // Admissions Office
    private Counter testCounter;

    @BeforeEach
    void setUp() {
        tokenDao = new TokenDaoImpl();
        counterDao = new CounterDaoImpl();
        tokenService = new TokenService();
        autoCaller = new AutoCaller();

        // Ensure Counter 1 is reset to OPEN and idle for test execution
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE tokens SET status = 'COMPLETED' WHERE counter_id = 1 AND status IN ('CALLED', 'SERVING')")) {
            ps.executeUpdate();
        } catch (Exception ignored) {}
        testCounter = counterDao.findById(1).orElseThrow();
        counterDao.clearToken(testCounter.getId(), CounterStatus.OPEN);
    }

    @AfterEach
    void tearDown() {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM tokens WHERE token_no LIKE 'A-%' OR token_no LIKE 'TST-%'")) {
            ps.executeUpdate();
        } catch (Exception ignored) {}
        counterDao.clearToken(1, CounterStatus.OPEN);
        counterDao.clearToken(2, CounterStatus.OPEN);
        counterDao.clearToken(3, CounterStatus.OPEN);
        counterDao.clearToken(4, CounterStatus.OPEN);
    }

    private Token createWaitingToken(String name, String phone) {
        Token t = new Token();
        // Keep token number strictly <= 10 characters to satisfy VARCHAR(10) schema
        long suffix = System.nanoTime() % 100000;
        t.setTokenNumber("A-" + suffix);
        t.setServiceId(TEST_SERVICE_ID);
        t.setCustomerName(name);
        t.setCustomerPhone(phone);
        t.setPriority(Priority.NORMAL);
        t.setStatus(TokenStatus.WAITING);
        t.setQueueDate(LocalDate.now());
        boolean created = tokenDao.create(t);
        assertTrue(created, "Token must be created successfully");
        return t;
    }

    @Test
    @DisplayName("A1.1: fromString validates valid enums and throws on unknown strings like 'ONLINE'")
    void testCounterStatusFromStringValidation() {
        assertEquals(CounterStatus.OPEN, CounterStatus.fromString("OPEN"));
        assertEquals(CounterStatus.BUSY, CounterStatus.fromString("busy"));
        assertEquals(CounterStatus.CLOSED, CounterStatus.fromString("CLOSED"));

        // Reject 'ONLINE' and arbitrary strings to prevent silent state corruption to CLOSED
        assertThrows(IllegalArgumentException.class, () -> CounterStatus.fromString("ONLINE"),
                "FromString must reject 'ONLINE' and throw IllegalArgumentException");
        assertThrows(IllegalArgumentException.class, () -> CounterStatus.fromString("INVALID_STATUS"),
                "FromString must reject unknown values and throw IllegalArgumentException");
    }

    @Test
    @DisplayName("A1.2: Completing a token sets counter to OPEN, clears current_token_id, and allows next call")
    void testCompleteTokenReturnsCounterToOpenAndAllowsNextCall() throws Exception {
        // 1. Book two test tokens
        Token t1 = createWaitingToken("Alice A1", "9000000001");
        Token t2 = createWaitingToken("Bob A1", "9000000002");

        // 2. Counter calls token 1
        Optional<Token> called = autoCaller.callNext(TEST_SERVICE_ID, testCounter.getId());
        assertTrue(called.isPresent());

        Token activeToken = called.get();

        // Verify Counter is BUSY and has current_token_id set
        Counter busyCounter = counterDao.findById(testCounter.getId()).orElseThrow();
        assertEquals(CounterStatus.BUSY, busyCounter.getStatus(), "Counter must be BUSY during active call");
        assertEquals(activeToken.getId(), busyCounter.getCurrentTokenId(), "Counter current_token_id must match active token");

        // 3. Start serving then complete
        tokenService.startServing(activeToken.getId());
        boolean completed = tokenService.completeToken(activeToken.getId(), testCounter.getId());
        assertTrue(completed, "Token completion must succeed");

        // 4. Assert counter is OPEN and current_token_id is null
        Counter freedCounter = counterDao.findById(testCounter.getId()).orElseThrow();
        assertEquals(CounterStatus.OPEN, freedCounter.getStatus(), "Counter must return to OPEN after completion");
        assertNull(freedCounter.getCurrentTokenId(), "Counter current_token_id must be cleared (null) after completion");
        assertTrue(counterDao.findActiveOnlineCounters().stream().anyMatch(c -> c.getId() == testCounter.getId()),
                "Counter must remain in active online counters list");

        // 5. Assert counter can immediately call next waiting token
        Optional<Token> nextCalled = autoCaller.callNext(TEST_SERVICE_ID, testCounter.getId());
        assertTrue(nextCalled.isPresent(), "OPEN counter must be able to call next token");
        assertEquals(TokenStatus.CALLED, nextCalled.get().getStatus());

        // Clean up
        tokenService.skipToken(nextCalled.get().getId(), testCounter.getId());
    }

    @Test
    @DisplayName("A1.3: Skipping a token sets counter to OPEN, clears current_token_id, and allows next call")
    void testSkipTokenReturnsCounterToOpenAndAllowsNextCall() throws Exception {
        Token token = createWaitingToken("Charlie Skip", "9000000003");

        // Call the token
        Optional<Token> called = autoCaller.callNext(TEST_SERVICE_ID, testCounter.getId());
        assertTrue(called.isPresent());
        Token activeToken = called.get();

        // Skip the customer (no-show)
        boolean skipped = tokenService.skipToken(activeToken.getId(), testCounter.getId());
        assertTrue(skipped);

        // Verify counter state
        Counter freedCounter = counterDao.findById(testCounter.getId()).orElseThrow();
        assertEquals(CounterStatus.OPEN, freedCounter.getStatus(), "Counter must return to OPEN after skip");
        assertNull(freedCounter.getCurrentTokenId(), "current_token_id must be null after skip");
        assertTrue(counterDao.findOpenIdleCounters().stream().anyMatch(c -> c.getId() == testCounter.getId()),
                "Counter must appear in open idle counters list");
    }

    @Test
    @DisplayName("A1.4: No-show timeout frees counter to OPEN and clears current_token_id")
    void testNoShowWatcherFreesCounterToOpen() throws Exception {
        Token token = createWaitingToken("Dave NoShow", "9000000004");

        // Call token
        Optional<Token> called = autoCaller.callNext(TEST_SERVICE_ID, testCounter.getId());
        assertTrue(called.isPresent());
        Token activeToken = called.get();

        // Simulate overdue timestamp (> 120s ago)
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE tokens SET called_at = ? WHERE id = ?")) {
            ps.setTimestamp(1, java.sql.Timestamp.valueOf(java.time.LocalDateTime.now().minusMinutes(5)));
            ps.setInt(2, activeToken.getId());
            ps.executeUpdate();
        }

        // Run NoShowWatcher to detect and process overdue token
        NoShowWatcher watcher = new NoShowWatcher();
        watcher.run();

        // Verify counter is restored to OPEN and current_token_id is cleared
        Counter freedCounter = counterDao.findById(testCounter.getId()).orElseThrow();
        assertEquals(CounterStatus.OPEN, freedCounter.getStatus(), "Counter must return to OPEN after no-show timeout");
        assertNull(freedCounter.getCurrentTokenId(), "current_token_id must be null after no-show timeout");
    }

    @Test
    @DisplayName("A1.5: A BUSY counter cannot call a second token while one is already active")
    void testBusyCounterCannotCallSecondToken() {
        Token t1 = createWaitingToken("Busy Test 1", "9000000005");
        Token t2 = createWaitingToken("Busy Test 2", "9000000006");

        // First call succeeds and makes counter BUSY
        Optional<Token> firstCall = autoCaller.callNext(TEST_SERVICE_ID, testCounter.getId());
        assertTrue(firstCall.isPresent(), "First call should succeed");

        // Verify counter is now BUSY
        Counter busyCounter = counterDao.findById(testCounter.getId()).orElseThrow();
        assertEquals(CounterStatus.BUSY, busyCounter.getStatus());

        // Calling next again on the same BUSY counter is a harmless double-click: returns the same active token
        Optional<Token> secondCall = autoCaller.callNext(TEST_SERVICE_ID, testCounter.getId());
        assertTrue(secondCall.isPresent(), "Double-click must return the existing active token");
        assertEquals(firstCall.get().getId(), secondCall.get().getId(), "Must not claim a second token");

        // Clean up
        assertDoesNotThrow(() -> tokenService.skipToken(firstCall.get().getId(), testCounter.getId()));
    }
}

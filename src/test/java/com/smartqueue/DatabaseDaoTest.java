package com.smartqueue;

import com.smartqueue.dao.*;
import com.smartqueue.dao.impl.*;
import com.smartqueue.model.*;
import com.smartqueue.util.DatabaseConnection;
import com.smartqueue.util.PasswordUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit & Integration tests for Phase 1: Database, Schema, and DAOs.
 */
public class DatabaseDaoTest {

    private static UserDao userDao;
    private static ServiceDao serviceDao;
    private static CounterDao counterDao;
    private static TokenDao tokenDao;
    private static TokenSequenceDao tokenSequenceDao;
    private static AuditLogDao auditLogDao;

    @BeforeAll
    public static void setup() {
        userDao = new UserDaoImpl();
        serviceDao = new ServiceDaoImpl();
        counterDao = new CounterDaoImpl();
        tokenDao = new TokenDaoImpl();
        tokenSequenceDao = new TokenSequenceDaoImpl();
        auditLogDao = new AuditLogDaoImpl();
    }

    @Test
    public void testUserDaoAndAuth() {
        Optional<User> adminOpt = userDao.findByUsername("admin");
        assertTrue(adminOpt.isPresent(), "Admin user should exist in seeded database");
        User admin = adminOpt.get();
        assertEquals(Role.ADMIN, admin.getRole());
        assertTrue(admin.isActive());
        assertTrue(PasswordUtil.checkPassword("Admin@123", admin.getPasswordHash()));
    }

    @Test
    public void testServiceDao() {
        List<ServiceEntity> services = serviceDao.findAll();
        assertEquals(3, services.size(), "Should have exactly 3 seeded services (AD, CL, LB)");
        assertTrue(services.stream().anyMatch(s -> s.getPrefix().equals("AD")));
        assertTrue(services.stream().anyMatch(s -> s.getPrefix().equals("CL")));
        assertTrue(services.stream().anyMatch(s -> s.getPrefix().equals("LB")));
    }

    @Test
    public void testCounterDao() {
        List<Counter> counters = counterDao.findAll();
        assertEquals(4, counters.size(), "Should have exactly 4 seeded counters");

        List<Counter> openCounters = counterDao.findOpenIdleCounters();
        assertFalse(openCounters.isEmpty(), "Should have open idle counters in seed");
    }

    @Test
    public void testTokenSequenceDaoAtomicGeneration() throws Exception {
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            LocalDate today = LocalDate.now();
            int firstSeq = tokenSequenceDao.getNextSequenceNumber(conn, 1, today);
            assertTrue(firstSeq >= 16, "Sequence for Admissions should be at least 16 (accounting for seed)");

            // Calling it again inside the transaction increments to firstSeq + 1
            int nextSeq = tokenSequenceDao.getNextSequenceNumber(conn, 1, today);
            assertEquals(firstSeq + 1, nextSeq, "Second increment must be exactly firstSeq + 1");
            conn.rollback(); // Rollback so tests remain idempotent on persistent databases
        }
    }

    @Test
    public void testAuditLogDao() {
        boolean logged = auditLogDao.log(1, "TEST_SYSTEM_INITIALIZATION", null);
        assertTrue(logged, "Audit log record should insert successfully");

        List<AuditLog> recent = auditLogDao.findRecentLogs(5);
        assertFalse(recent.isEmpty(), "Audit log should contain entries");
        assertTrue(recent.stream().anyMatch(a -> a.getAction().equals("TEST_SYSTEM_INITIALIZATION")));
    }

    @Test
    public void testWaitEstimationHistorySeeded() {
        // Admissions should have 15 completed tokens in seed
        List<Double> durations = tokenDao.getRecentServiceDurationsMinutes(1, 20);
        assertTrue(durations.size() >= 10, "Admissions service should have at least 10 seeded duration samples");
        for (Double d : durations) {
            assertTrue(d > 0.0 && d < 60.0, "Service duration should be reasonable");
        }
    }
}

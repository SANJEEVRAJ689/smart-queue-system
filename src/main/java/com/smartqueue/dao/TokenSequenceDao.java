package com.smartqueue.dao;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;

/**
 * Data Access Object for managing atomic, daily sequential numbering per service.
 */
public interface TokenSequenceDao {

    /**
     * Atomically retrieves and increments the sequence counter for the specified service and date
     * using pessimistic row-level locking (SELECT ... FOR UPDATE).
     *
     * @param conn the active transaction connection
     * @param serviceId ID of the service department
     * @param queueDate the date of queue operation
     * @return next sequential number (1, 2, 3...)
     */
    int getNextSequenceNumber(Connection conn, int serviceId, LocalDate queueDate) throws SQLException;
}

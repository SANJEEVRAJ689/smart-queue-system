package com.smartqueue.dao.impl;

import com.smartqueue.dao.TokenSequenceDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

/**
 * JDBC Implementation of TokenSequenceDao using SELECT ... FOR UPDATE.
 * Prevents race conditions during simultaneous customer token bookings.
 */
public class TokenSequenceDaoImpl implements TokenSequenceDao {
    private static final Logger logger = LoggerFactory.getLogger(TokenSequenceDaoImpl.class);

    @Override
    public int getNextSequenceNumber(Connection conn, int serviceId, LocalDate queueDate) throws SQLException {
        String selectSql = "SELECT last_number FROM token_sequence WHERE service_id = ? AND queue_date = ? FOR UPDATE";
        String insertSql = "INSERT INTO token_sequence (service_id, queue_date, last_number) VALUES (?, ?, ?)";
        String updateSql = "UPDATE token_sequence SET last_number = ? WHERE service_id = ? AND queue_date = ?";

        int nextSeq = 1;
        boolean exists = false;

        try (PreparedStatement psSelect = conn.prepareStatement(selectSql)) {
            psSelect.setInt(1, serviceId);
            psSelect.setDate(2, Date.valueOf(queueDate));
            try (ResultSet rs = psSelect.executeQuery()) {
                if (rs.next()) {
                    exists = true;
                    nextSeq = rs.getInt("last_number") + 1;
                }
            }
        }

        if (exists) {
            try (PreparedStatement psUpdate = conn.prepareStatement(updateSql)) {
                psUpdate.setInt(1, nextSeq);
                psUpdate.setInt(2, serviceId);
                psUpdate.setDate(3, Date.valueOf(queueDate));
                psUpdate.executeUpdate();
            }
        } else {
            try (PreparedStatement psInsert = conn.prepareStatement(insertSql)) {
                psInsert.setInt(1, serviceId);
                psInsert.setDate(2, Date.valueOf(queueDate));
                psInsert.setInt(3, nextSeq);
                psInsert.executeUpdate();
            }
        }

        logger.debug("Generated sequence {} for service {} on {}", nextSeq, serviceId, queueDate);
        return nextSeq;
    }
}

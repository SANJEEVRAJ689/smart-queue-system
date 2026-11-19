package com.smartqueue.web.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDate;

@Service
public class TokenNumberGeneratorService {

    private static final Logger logger = LoggerFactory.getLogger(TokenNumberGeneratorService.class);
    private final DataSource dataSource;

    public TokenNumberGeneratorService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Transactional
    public String generateNextTokenNumber(String prefix) {
        String cleanPrefix = (prefix != null && !prefix.isBlank()) ? prefix.trim().toUpperCase() : "AD";
        LocalDate today = LocalDate.now();

        try (Connection conn = dataSource.getConnection()) {
            boolean prevAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                int serviceId = 1;
                String serviceSql = "SELECT id FROM services WHERE prefix = ?";
                try (PreparedStatement ps = conn.prepareStatement(serviceSql)) {
                    ps.setString(1, cleanPrefix);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) serviceId = rs.getInt("id");
                    }
                }

                String selectSql = "SELECT last_number FROM token_sequence WHERE service_id = ? AND queue_date = ? FOR UPDATE";
                String insertSql = "INSERT INTO token_sequence (service_id, queue_date, last_number) VALUES (?, ?, ?)";
                String updateSql = "UPDATE token_sequence SET last_number = ? WHERE service_id = ? AND queue_date = ?";

                int nextSeq = 1;
                boolean exists = false;
                try (PreparedStatement psSelect = conn.prepareStatement(selectSql)) {
                    psSelect.setInt(1, serviceId);
                    psSelect.setDate(2, Date.valueOf(today));
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
                        psUpdate.setDate(3, Date.valueOf(today));
                        psUpdate.executeUpdate();
                    }
                } else {
                    try (PreparedStatement psInsert = conn.prepareStatement(insertSql)) {
                        psInsert.setInt(1, serviceId);
                        psInsert.setDate(2, Date.valueOf(today));
                        psInsert.setInt(3, nextSeq);
                        psInsert.executeUpdate();
                    }
                }

                conn.commit();
                return String.format("%s-%03d", cleanPrefix, nextSeq);
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(prevAutoCommit);
            }
        } catch (SQLException e) {
            logger.error("Error generating token sequence for prefix {}", cleanPrefix, e);
            throw new RuntimeException("Error generating token number", e);
        }
    }
}

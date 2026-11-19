package com.smartqueue.dao.impl;

import com.smartqueue.dao.ServiceStatsDao;
import com.smartqueue.model.ServiceStats;
import com.smartqueue.util.DatabaseConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC Implementation of ServiceStatsDao.
 */
public class ServiceStatsDaoImpl implements ServiceStatsDao {
    private static final Logger logger = LoggerFactory.getLogger(ServiceStatsDaoImpl.class);

    private static final String BASE_SELECT =
            "SELECT st.id, st.service_id, st.stat_date, st.total_tokens, st.completed_tokens, " +
            "       st.skipped_tokens, st.avg_wait_seconds, st.avg_service_seconds, " +
            "       s.name AS service_name " +
            "FROM service_stats st " +
            "LEFT JOIN services s ON st.service_id = s.id ";

    @Override
    public Optional<ServiceStats> findByServiceAndDate(int serviceId, LocalDate date) {
        String sql = BASE_SELECT + "WHERE st.service_id = ? AND st.stat_date = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, serviceId);
            ps.setDate(2, Date.valueOf(date));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToStats(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding stats for service: {} on date: {}", serviceId, date, e);
        }
        return Optional.empty();
    }

    @Override
    public List<ServiceStats> findStatsByDateRange(LocalDate startDate, LocalDate endDate) {
        List<ServiceStats> list = new ArrayList<>();
        String sql = BASE_SELECT +
                     "WHERE st.stat_date BETWEEN ? AND ? " +
                     "ORDER BY st.stat_date DESC, s.name ASC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(startDate));
            ps.setDate(2, Date.valueOf(endDate));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToStats(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding stats for range: {} to {}", startDate, endDate, e);
        }
        return list;
    }

    @Override
    public List<ServiceStats> findDailyStatsForService(int serviceId, int pastDays) {
        List<ServiceStats> list = new ArrayList<>();
        String sql = BASE_SELECT +
                     "WHERE st.service_id = ? AND st.stat_date >= ? " +
                     "ORDER BY st.stat_date ASC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, serviceId);
            ps.setDate(2, Date.valueOf(LocalDate.now().minusDays(pastDays)));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToStats(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding daily stats for service: {}", serviceId, e);
        }
        return list;
    }

    /**
     * Calculates the daily metrics for the given service and upserts into service_stats.
     */
    @Override
    public boolean upsertDailyStats(int serviceId, LocalDate date) {
        String computeSql =
                "SELECT COUNT(*) AS total_count, " +
                "       SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) AS completed_count, " +
                "       SUM(CASE WHEN status = 'SKIPPED' THEN 1 ELSE 0 END) AS skipped_count, " +
                "       AVG(CASE WHEN status = 'COMPLETED' AND called_at IS NOT NULL AND created_at IS NOT NULL " +
                "                THEN TIMESTAMPDIFF(SECOND, created_at, called_at) ELSE NULL END) AS avg_wait, " +
                "       AVG(CASE WHEN status = 'COMPLETED' AND completed_at IS NOT NULL AND started_at IS NOT NULL " +
                "                THEN TIMESTAMPDIFF(SECOND, started_at, completed_at) ELSE NULL END) AS avg_service " +
                "FROM tokens " +
                "WHERE service_id = ? AND CAST(created_at AS DATE) = ?";

        try (Connection conn = DatabaseConnection.getConnection()) {
            int total = 0, completed = 0, skipped = 0;
            int avgWait = 0, avgService = 0;

            try (PreparedStatement psComp = conn.prepareStatement(computeSql)) {
                psComp.setInt(1, serviceId);
                psComp.setDate(2, Date.valueOf(date));
                try (ResultSet rs = psComp.executeQuery()) {
                    if (rs.next()) {
                        total = rs.getInt("total_count");
                        completed = rs.getInt("completed_count");
                        skipped = rs.getInt("skipped_count");
                        double w = rs.getDouble("avg_wait");
                        if (!rs.wasNull() && w > 0) avgWait = (int) Math.abs(w);
                        double s = rs.getDouble("avg_service");
                        if (!rs.wasNull() && s > 0) avgService = (int) Math.abs(s);
                    }
                }
            } catch (SQLException ex) {
                logger.warn("Could not compute daily stats for service {}", serviceId, ex);
            }

            // Upsert into service_stats
            String checkExist = "SELECT id FROM service_stats WHERE service_id = ? AND stat_date = ?";
            Integer existingId = null;
            try (PreparedStatement psExist = conn.prepareStatement(checkExist)) {
                psExist.setInt(1, serviceId);
                psExist.setDate(2, Date.valueOf(date));
                try (ResultSet rs = psExist.executeQuery()) {
                    if (rs.next()) {
                        existingId = rs.getInt("id");
                    }
                }
            }

            if (existingId == null) {
                String insertSql = "INSERT INTO service_stats (service_id, stat_date, total_tokens, " +
                                   "completed_tokens, skipped_tokens, avg_wait_seconds, avg_service_seconds) " +
                                   "VALUES (?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement psIns = conn.prepareStatement(insertSql)) {
                    psIns.setInt(1, serviceId);
                    psIns.setDate(2, Date.valueOf(date));
                    psIns.setInt(3, total);
                    psIns.setInt(4, completed);
                    psIns.setInt(5, skipped);
                    psIns.setInt(6, avgWait);
                    psIns.setInt(7, avgService);
                    return psIns.executeUpdate() > 0;
                }
            } else {
                String updateSql = "UPDATE service_stats SET total_tokens = ?, completed_tokens = ?, " +
                                   "skipped_tokens = ?, avg_wait_seconds = ?, avg_service_seconds = ? " +
                                   "WHERE id = ?";
                try (PreparedStatement psUpd = conn.prepareStatement(updateSql)) {
                    psUpd.setInt(1, total);
                    psUpd.setInt(2, completed);
                    psUpd.setInt(3, skipped);
                    psUpd.setInt(4, avgWait);
                    psUpd.setInt(5, avgService);
                    psUpd.setInt(6, existingId);
                    return psUpd.executeUpdate() > 0;
                }
            }
        } catch (SQLException e) {
            logger.error("Error upserting daily stats for service: {} on date: {}", serviceId, date, e);
        }
        return false;
    }

    private ServiceStats mapResultSetToStats(ResultSet rs) throws SQLException {
        ServiceStats st = new ServiceStats();
        st.setId(rs.getInt("id"));
        st.setServiceId(rs.getInt("service_id"));
        Date d = rs.getDate("stat_date");
        if (d != null) st.setStatDate(d.toLocalDate());
        st.setTotalTokens(rs.getInt("total_tokens"));
        st.setCompletedTokens(rs.getInt("completed_tokens"));
        st.setSkippedTokens(rs.getInt("skipped_tokens"));
        st.setAvgWaitSeconds(rs.getInt("avg_wait_seconds"));
        st.setAvgServiceSeconds(rs.getInt("avg_service_seconds"));
        st.setServiceName(rs.getString("service_name"));
        return st;
    }
}

package com.smartqueue.dao.impl;

import com.smartqueue.dao.CounterDao;
import com.smartqueue.model.Counter;
import com.smartqueue.model.CounterStatus;
import com.smartqueue.util.DatabaseConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC Implementation of CounterDao.
 */
public class CounterDaoImpl implements CounterDao {
    private static final Logger logger = LoggerFactory.getLogger(CounterDaoImpl.class);

    private static final String BASE_SELECT =
            "SELECT c.id, c.name, c.service_id, c.staff_id, c.status, c.current_token_id, " +
            "       s.name AS service_name, u.full_name AS staff_name, t.token_no AS current_token_no " +
            "FROM counters c " +
            "LEFT JOIN services s ON c.service_id = s.id " +
            "LEFT JOIN users u ON c.staff_id = u.id " +
            "LEFT JOIN tokens t ON c.current_token_id = t.id ";

    @Override
    public Optional<Counter> findById(int id) {
        String sql = BASE_SELECT + "WHERE c.id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToCounter(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding counter by id: {}", id, e);
        }
        return Optional.empty();
    }

    @Override
    public Optional<Counter> findByName(String name) {
        String sql = BASE_SELECT + "WHERE c.name = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToCounter(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding counter by name: {}", name, e);
        }
        return Optional.empty();
    }

    @Override
    public Optional<Counter> findByStaffId(int staffId) {
        String sql = BASE_SELECT + "WHERE c.staff_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, staffId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToCounter(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding counter by staff id: {}", staffId, e);
        }
        return Optional.empty();
    }

    @Override
    public List<Counter> findAll() {
        List<Counter> list = new ArrayList<>();
        String sql = BASE_SELECT + "ORDER BY c.id ASC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapResultSetToCounter(rs));
            }
        } catch (SQLException e) {
            logger.error("Error fetching all counters", e);
        }
        return list;
    }

    @Override
    public List<Counter> findActiveOnlineCounters() {
        List<Counter> list = new ArrayList<>();
        String sql = BASE_SELECT + "WHERE c.status IN ('OPEN', 'BUSY') ORDER BY c.id ASC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapResultSetToCounter(rs));
            }
        } catch (SQLException e) {
            logger.error("Error fetching active online counters", e);
        }
        return list;
    }

    @Override
    public List<Counter> findOpenOrBusyCountersByService(int serviceId) {
        List<Counter> list = new ArrayList<>();
        String sql = BASE_SELECT + "WHERE c.service_id = ? AND c.status IN ('OPEN', 'BUSY')";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, serviceId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToCounter(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding open/busy counters for service: {}", serviceId, e);
        }
        return list;
    }

    @Override
    public List<Counter> findOpenIdleCounters() {
        List<Counter> list = new ArrayList<>();
        String sql = BASE_SELECT + "WHERE c.status = 'OPEN' AND c.current_token_id IS NULL";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapResultSetToCounter(rs));
            }
        } catch (SQLException e) {
            logger.error("Error finding open idle counters", e);
        }
        return list;
    }

    @Override
    public boolean create(Counter counter) {
        String sql = "INSERT INTO counters (name, service_id, staff_id, status, current_token_id) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, counter.getName());
            ps.setInt(2, counter.getServiceId());
            if (counter.getStaffId() != null) {
                ps.setInt(3, counter.getStaffId());
            } else {
                ps.setNull(3, Types.INTEGER);
            }
            ps.setString(4, counter.getStatus().name());
            if (counter.getCurrentTokenId() != null) {
                ps.setInt(5, counter.getCurrentTokenId());
            } else {
                ps.setNull(5, Types.INTEGER);
            }

            int affected = ps.executeUpdate();
            if (affected > 0) {
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        counter.setId(keys.getInt(1));
                    }
                }
                return true;
            }
        } catch (SQLException e) {
            logger.error("Error creating counter: {}", counter.getName(), e);
        }
        return false;
    }

    @Override
    public boolean update(Counter counter) {
        String sql = "UPDATE counters SET name = ?, service_id = ?, staff_id = ?, status = ?, current_token_id = ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, counter.getName());
            ps.setInt(2, counter.getServiceId());
            if (counter.getStaffId() != null) {
                ps.setInt(3, counter.getStaffId());
            } else {
                ps.setNull(3, Types.INTEGER);
            }
            ps.setString(4, counter.getStatus().name());
            if (counter.getCurrentTokenId() != null) {
                ps.setInt(5, counter.getCurrentTokenId());
            } else {
                ps.setNull(5, Types.INTEGER);
            }
            ps.setInt(6, counter.getId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("Error updating counter: {}", counter.getId(), e);
        }
        return false;
    }

    @Override
    public boolean updateStatus(int counterId, CounterStatus status) {
        String sql = "UPDATE counters SET status = ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setInt(2, counterId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("Error updating counter status: {}", counterId, e);
        }
        return false;
    }

    @Override
    public boolean updateStatus(int counterId, String status) {
        return updateStatus(counterId, CounterStatus.fromString(status));
    }

    @Override
    public boolean assignToken(int counterId, Integer tokenId, CounterStatus status) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return assignToken(conn, counterId, tokenId, status);
        } catch (SQLException e) {
            logger.error("Error acquiring connection to assign token {} to counter {}", tokenId, counterId, e);
            return false;
        }
    }

    @Override
    public boolean assignToken(Connection conn, int counterId, Integer tokenId, CounterStatus status) {
        String sql = "UPDATE counters SET current_token_id = ?, status = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (tokenId != null) {
                ps.setInt(1, tokenId);
            } else {
                ps.setNull(1, Types.INTEGER);
            }
            ps.setString(2, status.name());
            ps.setInt(3, counterId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("Error assigning token {} to counter {}", tokenId, counterId, e);
        }
        return false;
    }

    @Override
    public boolean clearToken(int counterId, CounterStatus newStatus) {
        return assignToken(counterId, null, newStatus);
    }

    @Override
    public boolean clearToken(Connection conn, int counterId, CounterStatus newStatus) {
        return assignToken(conn, counterId, null, newStatus);
    }

    @Override
    public boolean delete(int id) {
        String sql = "DELETE FROM counters WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("Error deleting counter: {}", id, e);
        }
        return false;
    }

    private Counter mapResultSetToCounter(ResultSet rs) throws SQLException {
        Counter c = new Counter();
        c.setId(rs.getInt("id"));
        c.setName(rs.getString("name"));
        c.setServiceId(rs.getInt("service_id"));
        int staffId = rs.getInt("staff_id");
        if (!rs.wasNull()) {
            c.setStaffId(staffId);
        }
        c.setStatus(CounterStatus.fromString(rs.getString("status")));
        int tokenId = rs.getInt("current_token_id");
        if (!rs.wasNull()) {
            c.setCurrentTokenId(tokenId);
        }
        c.setServiceName(rs.getString("service_name"));
        c.setStaffName(rs.getString("staff_name"));
        c.setCurrentTokenNo(rs.getString("current_token_no"));
        return c;
    }
}

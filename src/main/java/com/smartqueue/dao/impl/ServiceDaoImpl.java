package com.smartqueue.dao.impl;

import com.smartqueue.dao.ServiceDao;
import com.smartqueue.model.ServiceEntity;
import com.smartqueue.util.DatabaseConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC Implementation of ServiceDao.
 */
public class ServiceDaoImpl implements ServiceDao {
    private static final Logger logger = LoggerFactory.getLogger(ServiceDaoImpl.class);

    @Override
    public Optional<ServiceEntity> findById(int id) {
        String sql = "SELECT id, name, prefix, default_avg_minutes, active FROM services WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToService(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding service by id: {}", id, e);
        }
        return Optional.empty();
    }

    @Override
    public Optional<ServiceEntity> findByPrefix(String prefix) {
        String sql = "SELECT id, name, prefix, default_avg_minutes, active FROM services WHERE prefix = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, prefix.toUpperCase().trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToService(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding service by prefix: {}", prefix, e);
        }
        return Optional.empty();
    }

    @Override
    public List<ServiceEntity> findAll() {
        List<ServiceEntity> list = new ArrayList<>();
        String sql = "SELECT id, name, prefix, default_avg_minutes, active FROM services ORDER BY id ASC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapResultSetToService(rs));
            }
        } catch (SQLException e) {
            logger.error("Error fetching all services", e);
        }
        return list;
    }

    @Override
    public List<ServiceEntity> findActiveServices() {
        List<ServiceEntity> list = new ArrayList<>();
        String sql = "SELECT id, name, prefix, default_avg_minutes, active FROM services WHERE active = true ORDER BY name ASC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapResultSetToService(rs));
            }
        } catch (SQLException e) {
            logger.error("Error fetching active services", e);
        }
        return list;
    }

    @Override
    public boolean create(ServiceEntity service) {
        String sql = "INSERT INTO services (name, prefix, default_avg_minutes, active) VALUES (?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, service.getName());
            ps.setString(2, service.getPrefix().toUpperCase().trim());
            ps.setInt(3, service.getDefaultAvgMinutes());
            ps.setBoolean(4, service.isActive());
            int affected = ps.executeUpdate();
            if (affected > 0) {
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        service.setId(keys.getInt(1));
                    }
                }
                return true;
            }
        } catch (SQLException e) {
            logger.error("Error creating service: {}", service.getName(), e);
        }
        return false;
    }

    @Override
    public boolean update(ServiceEntity service) {
        String sql = "UPDATE services SET name = ?, prefix = ?, default_avg_minutes = ?, active = ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, service.getName());
            ps.setString(2, service.getPrefix().toUpperCase().trim());
            ps.setInt(3, service.getDefaultAvgMinutes());
            ps.setBoolean(4, service.isActive());
            ps.setInt(5, service.getId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("Error updating service: {}", service.getId(), e);
        }
        return false;
    }

    @Override
    public boolean delete(int id) {
        String sql = "DELETE FROM services WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("Error deleting service: {}", id, e);
        }
        return false;
    }

    private ServiceEntity mapResultSetToService(ResultSet rs) throws SQLException {
        ServiceEntity s = new ServiceEntity();
        s.setId(rs.getInt("id"));
        s.setName(rs.getString("name"));
        s.setPrefix(rs.getString("prefix"));
        s.setDefaultAvgMinutes(rs.getInt("default_avg_minutes"));
        s.setActive(rs.getBoolean("active"));
        return s;
    }
}

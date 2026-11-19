package com.smartqueue.dao.impl;

import com.smartqueue.dao.CustomerDao;
import com.smartqueue.model.Customer;
import com.smartqueue.util.DatabaseConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC Implementation of CustomerDao.
 */
public class CustomerDaoImpl implements CustomerDao {
    private static final Logger logger = LoggerFactory.getLogger(CustomerDaoImpl.class);

    private static final String BASE_SELECT =
            "SELECT id, full_name, phone, email, created_at, updated_at FROM customers ";

    @Override
    public Optional<Customer> findById(int id) {
        String sql = BASE_SELECT + "WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSet(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding customer by id: {}", id, e);
        }
        return Optional.empty();
    }

    @Override
    public Optional<Customer> findByPhone(String phone) {
        if (phone == null || phone.trim().isEmpty()) return Optional.empty();
        String sql = BASE_SELECT + "WHERE phone = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, phone.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSet(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding customer by phone: {}", phone, e);
        }
        return Optional.empty();
    }

    @Override
    public Customer findOrCreate(String fullName, String phone, String email) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return findOrCreate(conn, fullName, phone, email);
        } catch (SQLException e) {
            logger.error("Error in findOrCreate customer: {} ({})", fullName, phone, e);
            throw new RuntimeException("Database error finding/creating customer", e);
        }
    }

    @Override
    public Customer findOrCreate(Connection conn, String fullName, String phone, String email) throws SQLException {
        String cleanPhone = (phone != null && !phone.trim().isEmpty()) ? phone.trim() : "GUEST-" + System.currentTimeMillis();
        String cleanName = (fullName != null && !fullName.trim().isEmpty()) ? fullName.trim() : "Guest";

        String findSql = BASE_SELECT + "WHERE phone = ?";
        try (PreparedStatement ps = conn.prepareStatement(findSql)) {
            ps.setString(1, cleanPhone);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapResultSet(rs);
                }
            }
        }

        // Insert new customer
        String insertSql = "INSERT INTO customers (full_name, phone, email, created_at, updated_at) " +
                           "VALUES (?, ?, ?, ?, ?)";
        Instant now = Instant.now();
        try (PreparedStatement ps = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, cleanName);
            ps.setString(2, cleanPhone);
            ps.setString(3, email != null ? email.trim() : null);
            ps.setTimestamp(4, Timestamp.from(now));
            ps.setTimestamp(5, Timestamp.from(now));
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    Customer c = new Customer(keys.getInt(1), cleanName, cleanPhone, email);
                    c.setCreatedAt(now);
                    c.setUpdatedAt(now);
                    return c;
                }
            }
        }
        throw new SQLException("Failed to retrieve generated customer ID");
    }

    @Override
    public boolean create(Customer customer) {
        String sql = "INSERT INTO customers (full_name, phone, email, created_at, updated_at) " +
                     "VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            Instant now = Instant.now();
            ps.setString(1, customer.getFullName());
            ps.setString(2, customer.getPhone());
            ps.setString(3, customer.getEmail());
            ps.setTimestamp(4, Timestamp.from(now));
            ps.setTimestamp(5, Timestamp.from(now));
            int affected = ps.executeUpdate();
            if (affected > 0) {
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        customer.setId(keys.getInt(1));
                    }
                }
                return true;
            }
        } catch (SQLException e) {
            logger.error("Error creating customer: {}", customer.getFullName(), e);
        }
        return false;
    }

    @Override
    public boolean update(Customer customer) {
        String sql = "UPDATE customers SET full_name = ?, email = ?, updated_at = ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, customer.getFullName());
            ps.setString(2, customer.getEmail());
            ps.setTimestamp(3, Timestamp.from(Instant.now()));
            ps.setInt(4, customer.getId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("Error updating customer: {}", customer.getId(), e);
        }
        return false;
    }

    @Override
    public List<Customer> findAll() {
        List<Customer> list = new ArrayList<>();
        String sql = BASE_SELECT + "ORDER BY id DESC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapResultSet(rs));
            }
        } catch (SQLException e) {
            logger.error("Error fetching all customers", e);
        }
        return list;
    }

    private Customer mapResultSet(ResultSet rs) throws SQLException {
        Customer c = new Customer();
        c.setId(rs.getInt("id"));
        c.setFullName(rs.getString("full_name"));
        c.setPhone(rs.getString("phone"));
        c.setEmail(rs.getString("email"));
        Timestamp created = rs.getTimestamp("created_at");
        if (created != null) c.setCreatedAt(created.toInstant());
        Timestamp updated = rs.getTimestamp("updated_at");
        if (updated != null) c.setUpdatedAt(updated.toInstant());
        return c;
    }
}

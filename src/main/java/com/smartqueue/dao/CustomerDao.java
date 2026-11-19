package com.smartqueue.dao;

import com.smartqueue.model.Customer;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object interface for Customer operations.
 */
public interface CustomerDao {
    Optional<Customer> findById(int id);
    Optional<Customer> findByPhone(String phone);
    Customer findOrCreate(String fullName, String phone, String email);
    Customer findOrCreate(Connection conn, String fullName, String phone, String email) throws SQLException;
    boolean create(Customer customer);
    boolean update(Customer customer);
    List<Customer> findAll();
}

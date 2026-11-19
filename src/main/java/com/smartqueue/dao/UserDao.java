package com.smartqueue.dao;

import com.smartqueue.model.User;

import java.util.List;
import java.util.Optional;

/**
 * Data Access Object interface for User operations.
 */
public interface UserDao {
    Optional<User> findById(int id);
    Optional<User> findByUsername(String username);
    List<User> findAll();
    List<User> findStaffUsers();
    boolean create(User user);
    boolean update(User user);
    boolean delete(int id);
}

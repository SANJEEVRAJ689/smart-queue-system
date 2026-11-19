package com.smartqueue.service;

import com.smartqueue.dao.UserDao;
import com.smartqueue.dao.impl.UserDaoImpl;
import com.smartqueue.model.Role;
import com.smartqueue.model.User;
import com.smartqueue.util.PasswordUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Authentication and Session Management Service.
 */
public class AuthService {
    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);
    private static AuthService instance;

    private final UserDao userDao;
    private User currentUser;

    public AuthService() {
        this.userDao = new UserDaoImpl();
    }

    public AuthService(UserDao userDao) {
        this.userDao = userDao;
    }

    public static synchronized AuthService getInstance() {
        if (instance == null) {
            instance = new AuthService();
        }
        return instance;
    }

    /**
     * Authenticates a user against the database using BCrypt password checking.
     *
     * @param username username entered
     * @param plainPassword plain-text password entered
     * @return Optional containing the authenticated User, or empty if invalid
     */
    public Optional<User> login(String username, String plainPassword) {
        if (username == null || plainPassword == null || username.trim().isEmpty()) {
            return Optional.empty();
        }

        Optional<User> userOpt = userDao.findByUsername(username.trim());
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (PasswordUtil.checkPassword(plainPassword, user.getPasswordHash())) {
                this.currentUser = user;
                logger.info("User logged in successfully: {} [{}]", user.getUsername(), user.getRole());
                return Optional.of(user);
            } else {
                logger.warn("Password mismatch for username: {}", username);
            }
        } else {
            logger.warn("User not found: {}", username);
        }
        return Optional.empty();
    }

    public void logout() {
        if (currentUser != null) {
            logger.info("User logged out: {}", currentUser.getUsername());
        }
        this.currentUser = null;
    }

    public User getCurrentUser() {
        return currentUser;
    }

    public boolean isLoggedIn() {
        return currentUser != null;
    }

    public boolean isAdmin() {
        return currentUser != null && currentUser.getRole() == Role.ADMIN;
    }
}

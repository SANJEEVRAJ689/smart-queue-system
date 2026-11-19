package com.smartqueue.controller;

import com.smartqueue.model.Role;
import com.smartqueue.model.User;
import com.smartqueue.service.AuthService;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.util.Optional;

/**
 * Controller for the Login Modal Dialog.
 * Uses BCrypt authentication via AuthService.
 */
public class LoginController {

    @FXML private Label lblRoleHeader;
    @FXML private TextField txtUsername;
    @FXML private PasswordField txtPassword;
    @FXML private Label lblError;

    private final AuthService authService = AuthService.getInstance();
    private Role requiredRole = Role.STAFF;
    private boolean loginSuccessful = false;

    public void setRequiredRole(Role role) {
        this.requiredRole = role;
        if (lblRoleHeader != null) {
            lblRoleHeader.setText("Sign in to " + (role == Role.ADMIN ? "Admin Dashboard" : "Staff Portal"));
        }
    }

    @FXML
    public void initialize() {
        if (lblError != null) lblError.setText("");
    }

    @FXML
    public void handleLogin() {
        String username = txtUsername.getText();
        String password = txtPassword.getText();

        if (username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            showError("Please enter both username and password.");
            return;
        }

        Optional<User> userOpt = authService.login(username, password);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (requiredRole == Role.ADMIN && user.getRole() != Role.ADMIN) {
                authService.logout();
                showError("Access Denied: Admin credentials required.");
                return;
            }
            loginSuccessful = true;
            closeStage();
        } else {
            showError("Invalid username or password.");
        }
    }

    @FXML
    public void handleFillAdminCredentials() {
        // Fill quick-demo credentials for the viva presentation.
        // Password is "Admin@123" (BCrypt-hashed and stored in seed.sql).
        txtUsername.setText("admin");
        txtPassword.setText("Admin@123");
        lblError.setText("");
    }

    @FXML
    public void handleFillStaffCredentials() {
        // Fill quick-demo credentials for counter staff portal.
        // Password is "Staff@123" (BCrypt-hashed and stored in seed.sql).
        txtUsername.setText("staff1");
        txtPassword.setText("Staff@123");
        lblError.setText("");
    }

    @FXML
    public void handleCancel() {
        loginSuccessful = false;
        closeStage();
    }

    public boolean isLoginSuccessful() {
        return loginSuccessful;
    }

    private void showError(String msg) {
        lblError.setText(msg);
        lblError.setVisible(true);
    }

    private void closeStage() {
        Stage stage = (Stage) txtUsername.getScene().getWindow();
        if (stage != null) {
            stage.close();
        }
    }
}

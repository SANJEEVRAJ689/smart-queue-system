package com.smartqueue.controller;

import com.smartqueue.dao.CounterDao;
import com.smartqueue.dao.TokenDao;
import com.smartqueue.dao.impl.CounterDaoImpl;
import com.smartqueue.dao.impl.TokenDaoImpl;
import com.smartqueue.model.Role;
import com.smartqueue.model.User;
import com.smartqueue.service.AuthService;
import com.smartqueue.service.QueueManager;
import com.smartqueue.util.AlertUtil;
import com.smartqueue.util.DatabaseConnection;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDate;

/**
 * Controller for the Main System Launcher.
 * Allows evaluators and users to launch the Customer Kiosk, Staff Portal,
 * Live Display Board, or Admin Dashboard in separate windows.
 */
public class MainLauncherController implements QueueManager.QueueUpdateListener {
    private static final Logger logger = LoggerFactory.getLogger(MainLauncherController.class);

    @FXML private Label lblDatabaseMode;
    @FXML private Label lblTodayTokens;
    @FXML private Label lblActiveCounters;
    @FXML private Label lblCurrentUser;

    private final TokenDao tokenDao = new TokenDaoImpl();
    private final CounterDao counterDao = new CounterDaoImpl();
    private final AuthService authService = AuthService.getInstance();

    @FXML
    public void initialize() {
        refreshDashboardMetrics();
        QueueManager.getInstance().addListener(this);
    }

    private void refreshDashboardMetrics() {
        if (DatabaseConnection.isOfflineFallback()) {
            lblDatabaseMode.setText("In-Memory Demo Mode (H2/MySQL)");
            lblDatabaseMode.setStyle("-fx-text-fill: #d97706; -fx-font-weight: bold;");
        } else {
            lblDatabaseMode.setText("MySQL Connected (Production)");
            lblDatabaseMode.setStyle("-fx-text-fill: #16a34a; -fx-font-weight: bold;");
        }

        int todayCount = tokenDao.findTokensByDate(LocalDate.now()).size();
        lblTodayTokens.setText(String.valueOf(todayCount));

        int onlineCounters = counterDao.findActiveOnlineCounters().size();
        lblActiveCounters.setText(String.valueOf(onlineCounters));

        if (authService.isLoggedIn()) {
            lblCurrentUser.setText("Logged in as: " + authService.getCurrentUser().getFullName());
        } else {
            lblCurrentUser.setText("Not logged in");
        }
    }

    @Override
    public void onQueueUpdated() {
        refreshDashboardMetrics();
    }

    @FXML
    public void handleOpenKiosk() {
        openWindow("/fxml/kiosk.fxml", "Smart Queue - Customer Booking Kiosk", 850, 680, false);
    }

    @FXML
    public void handleOpenDisplayBoard() {
        openWindow("/fxml/display_board.fxml", "Smart Queue - Live Display Board", 1024, 700, false);
    }

    @FXML
    public void handleOpenStaffPortal() {
        if (!authService.isLoggedIn() || authService.getCurrentUser().getRole() != Role.STAFF) {
            boolean loggedIn = promptLogin(Role.STAFF);
            if (!loggedIn) return;
        }
        openWindow("/fxml/staff_dashboard.fxml", "Smart Queue - Counter Staff Dashboard", 950, 700, false);
    }

    @FXML
    public void handleOpenAdminDashboard() {
        if (!authService.isLoggedIn() || !authService.isAdmin()) {
            boolean loggedIn = promptLogin(Role.ADMIN);
            if (!loggedIn) return;
        }
        openWindow("/fxml/admin_dashboard.fxml", "Smart Queue - Admin Control & Analytics", 1050, 720, false);
    }

    @FXML
    public void handleLogout() {
        authService.logout();
        refreshDashboardMetrics();
        AlertUtil.showInfo("Logged Out", "You have been logged out successfully.");
    }

    private boolean promptLogin(Role requiredRole) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
            Parent root = loader.load();
            LoginController controller = loader.getController();
            controller.setRequiredRole(requiredRole);

            Stage stage = new Stage();
            stage.setTitle("Login Required - " + requiredRole);
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.showAndWait();

            refreshDashboardMetrics();
            return controller.isLoginSuccessful();
        } catch (IOException e) {
            logger.error("Failed to open login dialog", e);
            AlertUtil.showError("Error", "Could not load login screen: " + e.getMessage());
            return false;
        }
    }

    private void openWindow(String fxmlPath, String title, double width, double height, boolean modal) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent root = loader.load();
            Stage stage = new Stage();
            stage.setTitle(title);
            stage.setScene(new Scene(root, width, height));
            if (modal) {
                stage.initModality(Modality.APPLICATION_MODAL);
                stage.showAndWait();
            } else {
                stage.show();
            }
        } catch (IOException e) {
            logger.error("Error opening window: {}", fxmlPath, e);
            AlertUtil.showError("Window Error", "Could not load screen: " + e.getMessage());
        }
    }
}

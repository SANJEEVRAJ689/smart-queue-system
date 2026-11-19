package com.smartqueue.controller;

import com.smartqueue.dao.AuditLogDao;
import com.smartqueue.dao.CounterDao;
import com.smartqueue.dao.ServiceDao;
import com.smartqueue.dao.TokenDao;
import com.smartqueue.dao.UserDao;
import com.smartqueue.dao.impl.AuditLogDaoImpl;
import com.smartqueue.dao.impl.CounterDaoImpl;
import com.smartqueue.dao.impl.ServiceDaoImpl;
import com.smartqueue.dao.impl.TokenDaoImpl;
import com.smartqueue.dao.impl.UserDaoImpl;
import com.smartqueue.model.AuditLog;
import com.smartqueue.model.Counter;
import com.smartqueue.model.Role;
import com.smartqueue.model.ServiceEntity;
import com.smartqueue.model.Token;
import com.smartqueue.model.User;
import com.smartqueue.service.QueueManager;
import com.smartqueue.service.ReportService;
import com.smartqueue.util.AlertUtil;
import com.smartqueue.util.DateTimeUtil;
import com.smartqueue.util.PasswordUtil;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for the Administrator Management & Reporting Panel.
 */
public class AdminDashboardController implements QueueManager.QueueUpdateListener {
    private static final Logger logger = LoggerFactory.getLogger(AdminDashboardController.class);

    // KPI Summary Labels
    @FXML private Label lblKpiTotal;
    @FXML private Label lblKpiCompleted;
    @FXML private Label lblKpiSkipped;
    @FXML private Label lblKpiNoShowRate;
    @FXML private Label lblKpiAvgWait;
    @FXML private Label lblKpiAvgService;

    // Charts
    @FXML private BarChart<String, Number> chartTokensByService;
    @FXML private BarChart<String, Number> chartPeakHours;

    // Services Management
    @FXML private TableView<ServiceEntity> tblServices;
    @FXML private TableColumn<ServiceEntity, String> colServicePrefix;
    @FXML private TableColumn<ServiceEntity, String> colServiceName;
    @FXML private TableColumn<ServiceEntity, String> colServiceDesc;
    @FXML private TableColumn<ServiceEntity, Number> colServiceAvgTime;
    @FXML private TableColumn<ServiceEntity, Boolean> colServiceActive;

    @FXML private TextField txtServiceName;
    @FXML private TextField txtServicePrefix;
    @FXML private TextField txtServiceDesc;
    @FXML private TextField txtServiceAvgTime;
    @FXML private CheckBox chkServiceActive;

    // Counters Management
    @FXML private TableView<Counter> tblCounters;
    @FXML private TableColumn<Counter, Number> colCounterNumber;
    @FXML private TableColumn<Counter, String> colCounterService;
    @FXML private TableColumn<Counter, String> colCounterStaff;
    @FXML private TableColumn<Counter, String> colCounterStatus;

    @FXML private TextField txtCounterNumber;
    @FXML private ComboBox<ServiceEntity> cmbCounterService;
    @FXML private ComboBox<User> cmbCounterStaff;

    // Staff Management
    @FXML private TableView<User> tblStaff;
    @FXML private TableColumn<User, String> colStaffUsername;
    @FXML private TableColumn<User, String> colStaffFullName;
    @FXML private TableColumn<User, String> colStaffRole;

    @FXML private TextField txtStaffUsername;
    @FXML private PasswordField txtStaffPassword;
    @FXML private TextField txtStaffFullName;
    @FXML private ComboBox<Role> cmbStaffRole;

    // Audit Log Viewer
    @FXML private TableView<AuditLog> tblAuditLog;
    @FXML private TableColumn<AuditLog, Number> colAuditId;
    @FXML private TableColumn<AuditLog, String> colAuditTime;
    @FXML private TableColumn<AuditLog, String> colAuditUser;
    @FXML private TableColumn<AuditLog, String> colAuditAction;
    @FXML private TableColumn<AuditLog, String> colAuditToken;

    private final ServiceDao serviceDao = new ServiceDaoImpl();
    private final CounterDao counterDao = new CounterDaoImpl();
    private final UserDao userDao = new UserDaoImpl();
    private final TokenDao tokenDao = new TokenDaoImpl();
    private final AuditLogDao auditLogDao = new AuditLogDaoImpl();
    private final ReportService reportService = new ReportService();

    @FXML
    public void initialize() {
        setupServicesTable();
        setupCountersTable();
        setupStaffTable();
        setupAuditTable();

        refreshAnalytics();
        refreshServices();
        refreshCounters();
        refreshStaff();
        refreshAuditLogs();

        QueueManager.getInstance().addListener(this);
    }

    private void setupServicesTable() {
        colServicePrefix.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCodePrefix()));
        colServiceName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getName()));
        colServiceDesc.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getDescription()));
        colServiceAvgTime.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getDefaultAvgTimeMins()));
        colServiceActive.setCellValueFactory(c -> new SimpleBooleanProperty(c.getValue().isActive()));
    }

    private void setupCountersTable() {
        colCounterNumber.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getCounterNumber()));
        colCounterService.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getServiceName()));
        colCounterStaff.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getStaffName() != null ? c.getValue().getStaffName() : "Unassigned"
        ));
        colCounterStatus.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getStatus().name()));
    }

    private void setupStaffTable() {
        colStaffUsername.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getUsername()));
        colStaffFullName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getFullName()));
        colStaffRole.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getRole().name()));

        cmbStaffRole.setItems(FXCollections.observableArrayList(Role.STAFF, Role.ADMIN));
        cmbStaffRole.getSelectionModel().select(Role.STAFF);
    }

    private void setupAuditTable() {
        if (tblAuditLog == null) return;
        colAuditId.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getId()));
        colAuditTime.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getTimestamp() != null ? DateTimeUtil.formatDateTime(c.getValue().getTimestamp()) : "--"
        ));
        colAuditUser.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getUsername() != null ? c.getValue().getUsername() : "System"
        ));
        colAuditAction.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getAction()));
        colAuditToken.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getTokenNo() != null ? c.getValue().getTokenNo() : (c.getValue().getTokenId() != null ? "#" + c.getValue().getTokenId() : "--")
        ));
    }

    private void refreshAnalytics() {
        QueueManager.getInstance().submitWorkerTask(() -> {
            Map<String, Object> summary = reportService.getTodaySummary();
            List<ServiceEntity> services = serviceDao.findAll();
            List<Token> todayTokens = tokenDao.findTokensByDate(LocalDate.now());

            Platform.runLater(() -> {
                lblKpiTotal.setText(String.valueOf(summary.get("totalTokens")));
                lblKpiCompleted.setText(String.valueOf(summary.get("completedTokens")));
                lblKpiSkipped.setText(String.valueOf(summary.get("skippedTokens")));
                lblKpiNoShowRate.setText(summary.get("noShowRate") + "%");
                lblKpiAvgWait.setText(summary.get("avgWaitMinutes") + "m");
                lblKpiAvgService.setText(summary.get("avgServiceMinutes") + "m");

                // Chart 1: Tokens by Service
                if (chartTokensByService != null) {
                    chartTokensByService.getData().clear();
                    XYChart.Series<String, Number> series = new XYChart.Series<>();
                    series.setName("Tokens Handled");
                    for (ServiceEntity s : services) {
                        int count = (int) todayTokens.stream()
                                .filter(t -> t.getServiceId() == s.getId())
                                .count();
                        series.getData().add(new XYChart.Data<>(s.getName(), count));
                    }
                    chartTokensByService.getData().add(series);
                }

                // Chart 2: Peak Hours Distribution
                if (chartPeakHours != null) {
                    chartPeakHours.getData().clear();
                    XYChart.Series<String, Number> hourSeries = new XYChart.Series<>();
                    hourSeries.setName("Tokens per Hour");

                    Map<Integer, Integer> hourCounts = new HashMap<>();
                    for (Token t : todayTokens) {
                        if (t.getCreatedAt() != null) {
                            int hr = t.getCreatedAt().getHour();
                            hourCounts.put(hr, hourCounts.getOrDefault(hr, 0) + 1);
                        }
                    }

                    // Populate standard business hours (8 AM to 5 PM)
                    for (int h = 8; h <= 17; h++) {
                        String label = String.format("%02d:00", h);
                        int count = hourCounts.getOrDefault(h, 0);
                        hourSeries.getData().add(new XYChart.Data<>(label, count));
                    }
                    chartPeakHours.getData().add(hourSeries);
                }
            });
        });
    }

    private void refreshServices() {
        QueueManager.getInstance().submitWorkerTask(() -> {
            List<ServiceEntity> list = serviceDao.findAll();
            Platform.runLater(() -> {
                tblServices.setItems(FXCollections.observableArrayList(list));
                cmbCounterService.setItems(FXCollections.observableArrayList(list));
            });
        });
    }

    private void refreshCounters() {
        QueueManager.getInstance().submitWorkerTask(() -> {
            List<Counter> list = counterDao.findAll();
            Platform.runLater(() -> tblCounters.setItems(FXCollections.observableArrayList(list)));
        });
    }

    private void refreshStaff() {
        QueueManager.getInstance().submitWorkerTask(() -> {
            List<User> list = userDao.findAll();
            Platform.runLater(() -> {
                tblStaff.setItems(FXCollections.observableArrayList(list));
                cmbCounterStaff.setItems(FXCollections.observableArrayList(list));
            });
        });
    }

    @FXML
    public void refreshAuditLogs() {
        if (tblAuditLog == null) return;
        QueueManager.getInstance().submitWorkerTask(() -> {
            List<AuditLog> logs = auditLogDao.findRecentLogs(300);
            Platform.runLater(() -> tblAuditLog.setItems(FXCollections.observableArrayList(logs)));
        });
    }

    @FXML
    public void handleAddService() {
        String name = txtServiceName.getText();
        String prefix = txtServicePrefix.getText();
        String desc = txtServiceDesc.getText();
        String avgTimeStr = txtServiceAvgTime.getText();

        if (name == null || name.trim().isEmpty() || prefix == null || prefix.trim().isEmpty()) {
            AlertUtil.showWarning("Missing Fields", "Please enter Service Name and Code Prefix.");
            return;
        }

        int avg = 5;
        try {
            if (avgTimeStr != null && !avgTimeStr.trim().isEmpty()) {
                avg = Integer.parseInt(avgTimeStr.trim());
            }
        } catch (NumberFormatException ignored) {}

        ServiceEntity s = new ServiceEntity(0, name.trim(), prefix.trim().toUpperCase(), desc, avg, chkServiceActive.isSelected());
        QueueManager.getInstance().submitWorkerTask(() -> {
            boolean created = serviceDao.create(s);
            Platform.runLater(() -> {
                if (created) {
                    AlertUtil.showInfo("Created", "Service " + s.getName() + " added successfully.");
                    txtServiceName.clear();
                    txtServicePrefix.clear();
                    txtServiceDesc.clear();
                    txtServiceAvgTime.clear();
                    refreshServices();
                    refreshAnalytics();
                } else {
                    AlertUtil.showError("Error", "Could not create service (prefix may already exist).");
                }
            });
        });
    }

    @FXML
    public void handleDeleteService() {
        ServiceEntity selected = tblServices.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertUtil.showWarning("No Selection", "Please select a service from the table to delete.");
            return;
        }
        if (!AlertUtil.showConfirmation("Confirm Delete", "Delete service: " + selected.getName() + "?")) return;

        QueueManager.getInstance().submitWorkerTask(() -> {
            boolean ok = serviceDao.delete(selected.getId());
            Platform.runLater(() -> {
                if (ok) {
                    refreshServices();
                    refreshAnalytics();
                } else {
                    AlertUtil.showError("Delete Failed", "Cannot delete service with active tokens or counters.");
                }
            });
        });
    }

    @FXML
    public void handleAddCounter() {
        String numStr = txtCounterNumber.getText();
        ServiceEntity service = cmbCounterService.getSelectionModel().getSelectedItem();
        User staff = cmbCounterStaff.getSelectionModel().getSelectedItem();

        if (numStr == null || numStr.trim().isEmpty() || service == null) {
            AlertUtil.showWarning("Missing Fields", "Please enter Counter Number and select an assigned Service.");
            return;
        }

        try {
            int counterNum = Integer.parseInt(numStr.trim());
            Counter c = new Counter(0, counterNum, service.getId(), staff != null ? staff.getId() : null, "OFFLINE");
            QueueManager.getInstance().submitWorkerTask(() -> {
                boolean ok = counterDao.create(c);
                Platform.runLater(() -> {
                    if (ok) {
                        AlertUtil.showInfo("Created", "Counter " + counterNum + " created successfully.");
                        txtCounterNumber.clear();
                        refreshCounters();
                    } else {
                        AlertUtil.showError("Error", "Could not create counter (number may already exist).");
                    }
                });
            });
        } catch (NumberFormatException e) {
            AlertUtil.showWarning("Invalid Input", "Counter number must be a valid integer.");
        }
    }

    @FXML
    public void handleDeleteCounter() {
        Counter selected = tblCounters.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertUtil.showWarning("No Selection", "Please select a counter from the table to delete.");
            return;
        }
        if (!AlertUtil.showConfirmation("Confirm Delete", "Delete Counter " + selected.getCounterNumber() + "?")) return;

        QueueManager.getInstance().submitWorkerTask(() -> {
            boolean ok = counterDao.delete(selected.getId());
            Platform.runLater(this::refreshCounters);
        });
    }

    @FXML
    public void handleAddStaff() {
        String username = txtStaffUsername.getText();
        String password = txtStaffPassword.getText();
        String fullName = txtStaffFullName.getText();
        Role role = cmbStaffRole.getSelectionModel().getSelectedItem();

        if (username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty() || fullName == null || fullName.trim().isEmpty()) {
            AlertUtil.showWarning("Missing Fields", "All fields are required to register new staff.");
            return;
        }

        // Hash password securely with BCrypt
        String hash = PasswordUtil.hashPassword(password.trim());
        User user = new User(0, username.trim(), hash, fullName.trim(), role != null ? role : Role.STAFF);

        QueueManager.getInstance().submitWorkerTask(() -> {
            boolean ok = userDao.create(user);
            Platform.runLater(() -> {
                if (ok) {
                    AlertUtil.showInfo("Success", "User " + user.getUsername() + " created successfully.");
                    txtStaffUsername.clear();
                    txtStaffPassword.clear();
                    txtStaffFullName.clear();
                    refreshStaff();
                } else {
                    AlertUtil.showError("Error", "Could not create user (username may already exist).");
                }
            });
        });
    }

    @FXML
    public void handleDeleteStaff() {
        User selected = tblStaff.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertUtil.showWarning("No Selection", "Please select a user to delete.");
            return;
        }
        if (selected.getId() == 1) {
            AlertUtil.showWarning("Protected", "Cannot delete default administrator account.");
            return;
        }
        if (!AlertUtil.showConfirmation("Confirm Delete", "Delete user account: " + selected.getUsername() + "?")) return;

        QueueManager.getInstance().submitWorkerTask(() -> {
            boolean ok = userDao.delete(selected.getId());
            Platform.runLater(this::refreshStaff);
        });
    }

    @FXML
    public void handleExportCsv() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Token Activity Report (CSV)");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Spreadsheet (*.csv)", "*.csv"));
        chooser.setInitialFileName("queue_report_" + LocalDate.now() + ".csv");

        File file = chooser.showSaveDialog(lblKpiTotal.getScene().getWindow());
        if (file != null) {
            QueueManager.getInstance().submitWorkerTask(() -> {
                boolean success = reportService.exportTokensToCsv(file, LocalDate.now());
                Platform.runLater(() -> {
                    if (success) {
                        AlertUtil.showInfo("Report Exported", "Successfully exported report to:\n" + file.getAbsolutePath());
                    } else {
                        AlertUtil.showError("Export Error", "Failed to write CSV report.");
                    }
                });
            });
        }
    }

    @Override
    public void onQueueUpdated() {
        refreshAnalytics();
        refreshAuditLogs();
    }
}

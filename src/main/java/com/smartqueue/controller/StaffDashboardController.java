package com.smartqueue.controller;

import com.smartqueue.dao.CounterDao;
import com.smartqueue.dao.ServiceDao;
import com.smartqueue.dao.TokenDao;
import com.smartqueue.dao.impl.CounterDaoImpl;
import com.smartqueue.dao.impl.ServiceDaoImpl;
import com.smartqueue.dao.impl.TokenDaoImpl;
import com.smartqueue.model.Counter;
import com.smartqueue.model.CounterStatus;
import com.smartqueue.model.ServiceEntity;
import com.smartqueue.model.Token;
import com.smartqueue.model.TokenStatus;
import com.smartqueue.service.AuthService;
import com.smartqueue.service.QueueManager;
import com.smartqueue.service.TokenService;
import com.smartqueue.util.AlertUtil;
import com.smartqueue.util.AppException;
import com.smartqueue.util.DateTimeUtil;
import com.smartqueue.util.TimeUtil;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * Controller for the Counter Staff Dashboard.
 *
 * Provides operations: Call Next, Serve, Complete, Skip/No-show, Recall,
 * plus optional Automatic Calling when counter becomes free.
 * All DB operations execute on background worker threads.
 */
public class StaffDashboardController implements QueueManager.QueueUpdateListener {
    private static final Logger logger = LoggerFactory.getLogger(StaffDashboardController.class);

    @FXML private ComboBox<Counter> cmbCounters;
    @FXML private Label lblCounterStatus;
    @FXML private Label lblStaffName;

    // Current Token Panel
    @FXML private Label lblCurrentToken;
    @FXML private Label lblCurrentCustomer;
    @FXML private Label lblCurrentPhone;
    @FXML private Label lblCurrentPriority;
    @FXML private Label lblCurrentStatus;
    @FXML private Label lblServingTimer;

    // Buttons
    @FXML private Button btnCallNext;
    @FXML private Button btnStartServing;
    @FXML private Button btnComplete;
    @FXML private Button btnSkip;
    @FXML private Button btnRecall;
    @FXML private Button btnTransfer;
    @FXML private CheckBox chkAutoCall;

    // Waiting list table
    @FXML private TableView<Token> tblWaiting;
    @FXML private TableColumn<Token, String> colTokenNumber;
    @FXML private TableColumn<Token, String> colCustomer;
    @FXML private TableColumn<Token, String> colPhone;
    @FXML private TableColumn<Token, String> colPriority;
    @FXML private TableColumn<Token, String> colPosition;
    @FXML private TableColumn<Token, String> colEstWait;

    // Statistics labels
    @FXML private Label lblServedCount;
    @FXML private Label lblWaitingCount;

    private final CounterDao counterDao = new CounterDaoImpl();
    private final TokenDao tokenDao = new TokenDaoImpl();
    private final ServiceDao serviceDao = new ServiceDaoImpl();
    private final TokenService tokenService = new TokenService();
    private final AuthService authService = AuthService.getInstance();

    private Token currentToken;
    private Timeline timerTimeline;

    @FXML
    public void initialize() {
        setupTableColumns();
        loadCounters();
        setupServingTimer();

        if (authService.isLoggedIn()) {
            lblStaffName.setText(authService.getCurrentUser().getFullName());
        } else {
            lblStaffName.setText("Staff Operator");
        }

        cmbCounters.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                refreshCounterData();
            }
        });

        chkAutoCall.selectedProperty().addListener((obs, oldVal, newVal) -> {
            Counter c = cmbCounters.getSelectionModel().getSelectedItem();
            if (c != null) {
                QueueManager.getInstance().setAutoCall(c.getId(), newVal);
            }
        });

        QueueManager.getInstance().addListener(this);
    }

    private void setupTableColumns() {
        colTokenNumber.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getTokenNumber()));
        colCustomer.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getCustomerName()));
        colPhone.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getCustomerPhone()));
        colPriority.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().isPriority() ? "PRIORITY: " + cell.getValue().getPriorityReason() : "Regular"
        ));
        colPosition.setCellValueFactory(cell -> new SimpleStringProperty(String.valueOf(cell.getValue().getQueuePosition())));
        colEstWait.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getEstimatedWaitMinutes() + "m"));
    }

    private void loadCounters() {
        List<Counter> counters = counterDao.findAll();
        cmbCounters.setItems(FXCollections.observableArrayList(counters));
        if (!counters.isEmpty()) {
            cmbCounters.getSelectionModel().selectFirst();
            refreshCounterData();
        }
    }

    private void setupServingTimer() {
        timerTimeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            if (currentToken != null && currentToken.getStatus() == TokenStatus.SERVING && currentToken.getStartedAtInstant() != null) {
                lblServingTimer.setText(TimeUtil.formatElapsed(currentToken.getStartedAtInstant()));
            } else if (currentToken != null && currentToken.getStatus() == TokenStatus.CALLED && currentToken.getCalledAtInstant() != null) {
                lblServingTimer.setText("Called " + TimeUtil.formatElapsed(currentToken.getCalledAtInstant()) + " ago");
            } else {
                lblServingTimer.setText("00:00");
            }
        }));
        timerTimeline.setCycleCount(Timeline.INDEFINITE);
        timerTimeline.play();
    }

    private void refreshCounterData() {
        Counter selected = cmbCounters.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        QueueManager.getInstance().submitWorkerTask(() -> {
            // Find current active token assigned to this counter
            List<Token> active = tokenDao.findCurrentlyServingTokens();
            Token myActive = active.stream()
                    .filter(t -> t.getCounterId() != null && t.getCounterId() == selected.getId())
                    .findFirst()
                    .orElse(null);

            // Find waiting list for this counter's service
            List<Token> waiting = tokenDao.findWaitingTokens(selected.getServiceId());
            for (Token t : waiting) {
                int est = tokenService.getWaitTimeEstimator().estimateForToken(t.getId(), t.getServiceId());
                t.setEstimatedWaitMinutes(est);
            }

            // Find completed tokens count for today
            List<Token> completed = tokenDao.findCompletedTokensForCounterToday(selected.getId());

            Platform.runLater(() -> {
                this.currentToken = myActive;
                updateCurrentTokenUI(myActive);
                tblWaiting.setItems(FXCollections.observableArrayList(waiting));
                lblWaitingCount.setText(String.valueOf(waiting.size()));
                lblServedCount.setText(String.valueOf(completed.size()));

                // Auto-call check: if idle and auto-call enabled and someone waiting
                if (myActive == null && chkAutoCall.isSelected() && !waiting.isEmpty()) {
                    handleCallNext();
                }
            });
        });
    }

    private void updateCurrentTokenUI(Token token) {
        Counter selected = cmbCounters.getSelectionModel().getSelectedItem();
        CounterStatus currentStatus = (selected != null) ? selected.getStatus() : CounterStatus.OPEN;

        if (token != null) {
            lblCurrentToken.setText(token.getTokenNumber());
            lblCurrentCustomer.setText(token.getCustomerName());
            lblCurrentPhone.setText(token.getCustomerPhone());
            lblCurrentStatus.setText(token.getStatus().name());

            if (token.isPriority()) {
                lblCurrentPriority.setText("PRIORITY: " + token.getPriorityReason());
                lblCurrentPriority.setStyle("-fx-text-fill: #dc2626; -fx-font-weight: bold;");
            } else {
                lblCurrentPriority.setText("Regular");
                lblCurrentPriority.setStyle("-fx-text-fill: #0284c7;");
            }

            lblCounterStatus.setText(CounterStatus.BUSY.name());
            lblCounterStatus.setStyle("-fx-text-fill: #dc2626; -fx-font-weight: bold;");

            btnCallNext.setDisable(true);
            btnStartServing.setDisable(token.getStatus() == TokenStatus.SERVING);
            btnComplete.setDisable(false);
            btnSkip.setDisable(false);
            btnRecall.setDisable(false);
            if (btnTransfer != null) btnTransfer.setDisable(false);
        } else {
            lblCurrentToken.setText("None");
            lblCurrentCustomer.setText("--");
            lblCurrentPhone.setText("--");
            lblCurrentStatus.setText("IDLE");
            lblCurrentPriority.setText("--");
            lblServingTimer.setText("00:00");

            // Display real counter status (OPEN, CLOSED) instead of hard-coded ONLINE
            CounterStatus idleStatus = (currentStatus == CounterStatus.CLOSED) ? CounterStatus.CLOSED : CounterStatus.OPEN;
            lblCounterStatus.setText(idleStatus.name());
            if (idleStatus == CounterStatus.OPEN) {
                lblCounterStatus.setStyle("-fx-text-fill: #16a34a; -fx-font-weight: bold;");
                btnCallNext.setDisable(false);
            } else {
                lblCounterStatus.setStyle("-fx-text-fill: #6b7280; -fx-font-weight: bold;");
                btnCallNext.setDisable(true);
            }

            btnStartServing.setDisable(true);
            btnComplete.setDisable(true);
            btnSkip.setDisable(true);
            btnRecall.setDisable(true);
            if (btnTransfer != null) btnTransfer.setDisable(true);
        }
    }

    @FXML
    public void handleCallNext() {
        Counter c = cmbCounters.getSelectionModel().getSelectedItem();
        if (c == null) return;

        btnCallNext.setDisable(true);
        QueueManager.getInstance().submitWorkerTask(() -> {
            Optional<Token> nextToken = tokenService.callNextToken(c.getId());
            Platform.runLater(() -> {
                btnCallNext.setDisable(false);
                if (nextToken.isPresent()) {
                    refreshCounterData();
                } else {
                    AlertUtil.showInfo("Queue Empty", "No waiting tokens in the queue for this service.");
                }
            });
        });
    }

    @FXML
    public void handleStartServing() {
        if (currentToken == null) return;
        QueueManager.getInstance().submitWorkerTask(() -> {
            try {
                tokenService.startServing(currentToken.getId());
                Platform.runLater(this::refreshCounterData);
            } catch (AppException e) {
                Platform.runLater(() -> AlertUtil.showError("Action Denied", e.getMessage()));
            }
        });
    }

    @FXML
    public void handleComplete() {
        if (currentToken == null) return;
        Counter c = cmbCounters.getSelectionModel().getSelectedItem();
        if (c == null) return;

        Token completingToken = currentToken;
        QueueManager.getInstance().submitWorkerTask(() -> {
            try {
                tokenService.completeToken(completingToken.getId(), c.getId());
                long durationSec = (completingToken.getStartedAtInstant() != null)
                        ? java.time.Duration.between(completingToken.getStartedAtInstant(), java.time.Instant.now()).getSeconds()
                        : 0;
                logger.info("Session completed for token {} at Counter {}. Total duration: {}",
                        completingToken.getTokenNumber(), c.getId(), DateTimeUtil.formatDuration((int) durationSec));
                Platform.runLater(this::refreshCounterData);
            } catch (AppException e) {
                Platform.runLater(() -> AlertUtil.showError("Action Denied", e.getMessage()));
            }
        });
    }

    @FXML
    public void handleSkip() {
        if (currentToken == null) return;
        boolean confirm = AlertUtil.showConfirmation(
                "Confirm Skip",
                "Mark customer " + currentToken.getCustomerName() + " (" + currentToken.getTokenNumber() + ") as No-Show?"
        );
        if (!confirm) return;

        Counter c = cmbCounters.getSelectionModel().getSelectedItem();
        if (c == null) return;

        QueueManager.getInstance().submitWorkerTask(() -> {
            try {
                tokenService.skipToken(currentToken.getId(), c.getId());
                Platform.runLater(this::refreshCounterData);
            } catch (AppException e) {
                Platform.runLater(() -> AlertUtil.showError("Action Denied", e.getMessage()));
            }
        });
    }

    @FXML
    public void handleRecall() {
        if (currentToken == null) return;
        Counter c = cmbCounters.getSelectionModel().getSelectedItem();
        if (c == null) return;

        QueueManager.getInstance().submitWorkerTask(() -> {
            try {
                tokenService.recallToken(currentToken.getId(), c.getCounterNumber());
                Platform.runLater(() -> {
                    refreshCounterData();
                    AlertUtil.showInfo("Recalled", "Token " + currentToken.getTokenNumber() + " returned to queue.");
                });
            } catch (AppException e) {
                Platform.runLater(() -> AlertUtil.showError("Action Denied", e.getMessage()));
            }
        });
    }

    @FXML
    public void handleTransfer() {
        if (currentToken == null) return;

        List<ServiceEntity> services = serviceDao.findActiveServices();
        if (services.isEmpty()) {
            AlertUtil.showWarning("Transfer", "No other active services available.");
            return;
        }

        ChoiceDialog<ServiceEntity> dialog = new ChoiceDialog<>(services.get(0), services);
        dialog.setTitle("Transfer Customer");
        dialog.setHeaderText("Transfer Token " + currentToken.getTokenNumber());
        dialog.setContentText("Select destination department:");

        Optional<ServiceEntity> target = dialog.showAndWait();
        if (target.isPresent()) {
            ServiceEntity dest = target.get();
            QueueManager.getInstance().submitWorkerTask(() -> {
                try {
                    tokenService.transferService(currentToken.getId(), dest.getId());
                    Platform.runLater(() -> {
                        refreshCounterData();
                        AlertUtil.showInfo("Transferred", "Token " + currentToken.getTokenNumber() +
                                " transferred to " + dest.getName() + " queue.");
                    });
                } catch (AppException e) {
                    Platform.runLater(() -> AlertUtil.showError("Transfer Failed", e.getMessage()));
                }
            });
        }
    }

    @Override
    public void onQueueUpdated() {
        refreshCounterData();
    }
}

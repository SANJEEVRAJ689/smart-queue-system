package com.smartqueue.controller;

import com.smartqueue.dao.CounterDao;
import com.smartqueue.dao.TokenDao;
import com.smartqueue.dao.impl.CounterDaoImpl;
import com.smartqueue.dao.impl.TokenDaoImpl;
import com.smartqueue.model.Counter;
import com.smartqueue.model.Token;
import com.smartqueue.service.QueueManager;
import com.smartqueue.util.DateTimeUtil;
import com.smartqueue.util.SoundUtil;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Controller for the Public Live Display Board.
 * Features:
 * 1. Large "Now Serving" counter cards with high-contrast typography.
 * 2. Next 5 waiting tokens queue list.
 * 3. 2-Second background polling thread using ScheduledExecutorService.
 * 4. Real-time digital clock and date header.
 */
public class DisplayBoardController implements QueueManager.QueueUpdateListener {
    private static final Logger logger = LoggerFactory.getLogger(DisplayBoardController.class);

    @FXML private Label lblClock;
    @FXML private Label lblDate;
    @FXML private FlowPane paneNowServing;

    // Upcoming queue table
    @FXML private TableView<Token> tblUpcoming;
    @FXML private TableColumn<Token, String> colUpcomingToken;
    @FXML private TableColumn<Token, String> colUpcomingService;
    @FXML private TableColumn<Token, String> colUpcomingPriority;

    private final TokenDao tokenDao = new TokenDaoImpl();
    private final CounterDao counterDao = new CounterDaoImpl();
    private ScheduledExecutorService displayScheduler;

    @FXML
    public void initialize() {
        setupClock();
        setupUpcomingTable();
        refreshDisplayData();

        // 2-Second background refresh scheduler (strictly on background worker thread)
        displayScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "DisplayBoard-Poller");
            t.setDaemon(true);
            return t;
        });
        displayScheduler.scheduleAtFixedRate(this::refreshDisplayData, 2, 2, TimeUnit.SECONDS);

        // Also register with QueueManager for instantaneous event updates
        QueueManager.getInstance().addListener(this);
    }

    private void setupClock() {
        Timeline clock = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            LocalDateTime now = LocalDateTime.now();
            lblClock.setText(DateTimeUtil.formatTime(now));
            lblDate.setText(DateTimeUtil.formatDate(now));
        }));
        clock.setCycleCount(Animation.INDEFINITE);
        clock.play();
    }

    private void setupUpcomingTable() {
        colUpcomingToken.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getTokenNumber()));
        colUpcomingService.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getServiceName()));
        colUpcomingPriority.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().isPriority() ? "PRIORITY" : "Regular"
        ));
    }

    /**
     * Executes queries on background thread and updates JavaFX display board.
     */
    public void refreshDisplayData() {
        try {
            List<Token> activeTokens = tokenDao.findCurrentlyServingTokens();
            List<Counter> allCounters = counterDao.findAll();
            List<Token> nextWaiting = tokenDao.findNextWaitingTokensForDisplay(5);

            Platform.runLater(() -> {
                updateServingCards(allCounters, activeTokens);
                tblUpcoming.setItems(FXCollections.observableArrayList(nextWaiting));
            });
        } catch (Exception e) {
            logger.debug("Error updating display board: {}", e.getMessage());
        }
    }

    private void updateServingCards(List<Counter> counters, List<Token> activeTokens) {
        paneNowServing.getChildren().clear();

        if (counters.isEmpty()) {
            Label noCounters = new Label("No active service counters.");
            noCounters.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 18px;");
            paneNowServing.getChildren().add(noCounters);
            return;
        }

        for (Counter c : counters) {
            Token serving = activeTokens.stream()
                    .filter(t -> t.getCounterId() != null && t.getCounterId() == c.getId())
                    .findFirst()
                    .orElse(null);

            VBox card = new VBox(8);
            card.getStyleClass().add("serving-card");
            card.setPrefWidth(280);
            card.setPrefHeight(210);
            card.setAlignment(Pos.CENTER);

            Label lblCounter = new Label("COUNTER " + c.getCounterNumber());
            lblCounter.getStyleClass().add("serving-counter-label");

            Label lblToken = new Label(serving != null ? serving.getTokenNumber() : "--");
            lblToken.getStyleClass().add("serving-token-number");

            Label lblService = new Label(c.getServiceName() != null ? c.getServiceName() : "General Desk");
            lblService.getStyleClass().add("serving-service-label");

            Label lblStatus = new Label();
            if (serving != null) {
                lblStatus.setText(serving.getStatus().name());
                lblStatus.getStyleClass().add("serving-status-badge");
                if (serving.isPriority()) {
                    lblStatus.setStyle("-fx-background-color: #ef4444; -fx-text-fill: white;");
                }
            } else {
                lblStatus.setText(c.getStatus().name());
                lblStatus.setStyle("-fx-background-color: #475569; -fx-text-fill: #cbd5e1; -fx-padding: 3 10; -fx-background-radius: 10;");
            }

            card.getChildren().addAll(lblCounter, lblToken, lblService, lblStatus);
            paneNowServing.getChildren().add(card);
        }
    }

    @FXML
    public void handleTestChime() {
        SoundUtil.playChimeAsync();
    }

    @Override
    public void onQueueUpdated() {
        refreshDisplayData();
    }
}

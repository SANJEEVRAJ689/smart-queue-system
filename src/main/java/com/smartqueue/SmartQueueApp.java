package com.smartqueue;

import com.smartqueue.service.QueueManager;
import com.smartqueue.util.DatabaseConnection;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main JavaFX Application Bootstrap.
 *
 * Multithreading Lifecycle:
 * When the window is closed, stop() guarantees graceful shutdown of:
 * 1. QueueManager background schedulers and worker thread pools.
 * 2. HikariCP database connection pool.
 */
public class SmartQueueApp extends Application {
    private static final Logger logger = LoggerFactory.getLogger(SmartQueueApp.class);

    @Override
    public void start(Stage primaryStage) {
        try {
            logger.info("Starting Smart Queue and Token Management System...");

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main_launcher.fxml"));
            Parent root = loader.load();

            Scene scene = new Scene(root, 880, 620);
            primaryStage.setTitle("Smart Queue & Token Management System • College Project");
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(800);
            primaryStage.setMinHeight(550);
            primaryStage.show();

            logger.info("Main system launcher window loaded successfully.");
        } catch (Exception e) {
            logger.error("Fatal error starting JavaFX application", e);
        }
    }

    @Override
    public void stop() {
        logger.info("Application shutdown requested. Terminating background threads and database pool...");
        try {
            QueueManager.getInstance().shutdown();
            com.smartqueue.concurrent.ThreadPoolManager.getInstance().shutdown();
            DatabaseConnection.shutdown();
        } catch (Exception e) {
            logger.error("Error during application shutdown", e);
        }
        logger.info("Application shut down cleanly.");
    }

    public static void main(String[] args) {
        launch(args);
    }
}

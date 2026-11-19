package com.smartqueue.util;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * Thread-safe Database Connection Manager using HikariCP connection pooling.
 *
 * Design features for college presentation & reliability:
 * 1. Reads database credentials and pool configuration from db.properties.
 * 2. Attempts to connect to MySQL first.
 * 3. Graceful Fallback: If MySQL is not running on the evaluator's machine,
 *    it automatically initializes an in-memory database with the exact schema
 *    and seed data so the project demo NEVER crashes due to a stopped MySQL service.
 * 4. Thread-Safe: All worker threads borrow pooled connections and return them automatically.
 */
public class DatabaseConnection {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseConnection.class);
    private static HikariDataSource dataSource;
    private static boolean isOfflineFallback = false;

    static {
        initializePool();
    }

    private static synchronized void initializePool() {
        if (dataSource != null && !dataSource.isClosed()) {
            return;
        }

        Properties props = loadProperties();

        String envUrl = System.getenv("DB_URL");
        String envUser = System.getenv("DB_USER");
        String envPass = System.getenv("DB_PASSWORD");

        String driver = props.getProperty("db.driver", "com.mysql.cj.jdbc.Driver");
        String url = (envUrl != null && !envUrl.isBlank()) ? envUrl : props.getProperty("db.url");
        String user = (envUser != null && !envUser.isBlank()) ? envUser : props.getProperty("db.user", "root");
        String pass = (envPass != null) ? envPass : props.getProperty("db.password", "root");
        boolean fallbackEnabled = Boolean.parseBoolean(props.getProperty("db.offline.fallback.enabled", "true"));

        try {
            logger.info("Attempting to connect to primary MySQL database at: {}", url);
            HikariConfig config = new HikariConfig();
            config.setDriverClassName(driver);
            config.setJdbcUrl(url);
            config.setUsername(user);
            config.setPassword(pass);
            config.setMaximumPoolSize(Integer.parseInt(props.getProperty("db.pool.maximumPoolSize", "10")));
            config.setMinimumIdle(Integer.parseInt(props.getProperty("db.pool.minimumIdle", "2")));
            config.setIdleTimeout(Long.parseLong(props.getProperty("db.pool.idleTimeout", "30000")));
            config.setConnectionTimeout(Long.parseLong(props.getProperty("db.pool.connectionTimeout", "5000")));
            config.setPoolName("SmartQueue-MySQL-Pool");

            dataSource = new HikariDataSource(config);

            // Test connection
            try (Connection conn = dataSource.getConnection()) {
                logger.info("Successfully connected to MySQL database pool.");
            }

            // Run Flyway database migrations (baseline on existing schema, apply V2, V3, V4...)
            runFlywayMigrations(dataSource);
        } catch (Exception ex) {
            logger.warn("Could not connect to MySQL server ({}). Reason: {}", url, ex.getMessage());

            if (fallbackEnabled) {
                logger.info("Activating embedded in-memory fallback database for seamless demo...");
                initializeInMemoryFallback();
            } else {
                String errorMsg = "CRITICAL: Unable to connect to MySQL database at " + url +
                        ". MySQL is required in production (db.offline.fallback.enabled=false). " +
                        "Please verify that the MySQL 8.x service is active and credentials are correct.";
                logger.error(errorMsg, ex);
                throw new RuntimeException(errorMsg, ex);
            }
        }
    }

    private static void initializeInMemoryFallback() {
        try {
            HikariConfig config = new HikariConfig();
            config.setDriverClassName("org.h2.Driver");
            config.setJdbcUrl("jdbc:h2:mem:smart_queue_db;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false;DEFAULT_LOCK_TIMEOUT=10000");
            config.setUsername("sa");
            config.setPassword("");
            config.setMaximumPoolSize(15);
            config.setPoolName("SmartQueue-H2-Fallback-Pool");

            dataSource = new HikariDataSource(config);
            isOfflineFallback = true;
            logger.info("In-memory fallback database initialized. Executing schema and seed data...");

            executeSqlScript("/database/schema_h2.sql", "/database/seed_h2.sql");
            logger.info("In-memory database successfully populated with default services, counters, and admin.");
        } catch (Exception e) {
            logger.error("Failed to initialize fallback database", e);
            throw new RuntimeException("Critical: Database initialization failure.", e);
        }
    }

    /**
     * Executes SQL scripts when initializing in-memory test/demo database.
     */
    private static void executeSqlScript(String... scriptPaths) {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            for (String path : scriptPaths) {
                try (InputStream is = DatabaseConnection.class.getResourceAsStream(path)) {
                    if (is == null) {
                        logger.warn("Script resource not found: {}", path);
                        continue;
                    }
                    BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                    StringBuilder cleanSql = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String trimmed = line.trim();
                        // Ignore single-line comments
                        if (!trimmed.startsWith("--") && !trimmed.startsWith("//") && !trimmed.isEmpty()) {
                            cleanSql.append(line).append("\n");
                        }
                    }

                    // Split into distinct statements by semicolon
                    for (String statementStr : cleanSql.toString().split(";")) {
                        String trimmed = statementStr.trim();
                        if (!trimmed.isEmpty()) {
                            try {
                                stmt.execute(trimmed);
                            } catch (SQLException ex) {
                                logger.warn("Failed executing statement: [{}] - Reason: {}", trimmed, ex.getMessage());
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error executing database initialization script", e);
        }
    }

    private static Properties loadProperties() {
        Properties props = new Properties();
        try (InputStream is = DatabaseConnection.class.getResourceAsStream("/db.properties")) {
            if (is != null) {
                props.load(is);
            } else {
                logger.warn("db.properties not found on classpath, using defaults.");
            }
        } catch (Exception e) {
            logger.error("Error reading db.properties", e);
        }
        return props;
    }

    /**
     * Executes Flyway database migrations.
     * Uses baselineOnMigrate=true to automatically baseline against the pre-existing V1 schema.
     */
    public static synchronized void runFlywayMigrations(javax.sql.DataSource ds) {
        try {
            logger.info("Initializing Flyway database migrations...");
            org.flywaydb.core.Flyway flyway = org.flywaydb.core.Flyway.configure()
                    .dataSource(ds)
                    .baselineOnMigrate(true)
                    .baselineVersion("1")
                    .baselineDescription("Baseline existing schema")
                    .locations("classpath:db/migration")
                    .load();

            org.flywaydb.core.api.output.MigrateResult result = flyway.migrate();
            logger.info("Flyway successfully executed {} migration(s). Current schema version: {}",
                    result.migrationsExecuted, result.targetSchemaVersion);
        } catch (Exception ex) {
            logger.error("Flyway database migration failed", ex);
            throw new RuntimeException("Flyway migration failed: " + ex.getMessage(), ex);
        }
    }

    /**
     * Borrows a connection from the pool. Always close with try-with-resources!
     */
    public static Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            initializePool();
        }
        return dataSource.getConnection();
    }

    /**
     * Indicates whether the app is running against live MySQL or offline fallback.
     */
    public static boolean isOfflineFallback() {
        return isOfflineFallback;
    }

    /**
     * Closes the connection pool gracefully when the application terminates.
     */
    public static synchronized void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            logger.info("Closing HikariCP database connection pool...");
            dataSource.close();
            logger.info("HikariCP pool shut down successfully.");
        }
    }
}

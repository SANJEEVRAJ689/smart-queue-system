package com.smartqueue.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Properties;

/**
 * Singleton reader for application configurations defined in app.properties.
 */
public class AppConfig {
    private static final Logger logger = LoggerFactory.getLogger(AppConfig.class);
    private static final Properties props = new Properties();

    static {
        try (InputStream is = AppConfig.class.getResourceAsStream("/app.properties")) {
            if (is != null) {
                props.load(is);
                logger.info("app.properties loaded successfully.");
            } else {
                logger.warn("app.properties not found on classpath, using defaults.");
            }
        } catch (Exception e) {
            logger.error("Failed to load app.properties", e);
        }
    }

    public static int getInt(String key, int defaultValue) {
        String val = props.getProperty(key);
        if (val == null) return defaultValue;
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static int getDefaultAvgMinutes() {
        return getInt("app.default_avg_minutes", 5);
    }

    public static int getDisplayRefreshSeconds() {
        return getInt("app.display_refresh_seconds", 2);
    }

    public static int getQueueSchedulerIntervalSeconds() {
        return getInt("app.queue_scheduler_interval_seconds", 3);
    }

    public static int getNoShowTimeoutSeconds() {
        return getInt("app.no_show_timeout_seconds", 120);
    }

    public static int getStarvationThresholdMinutes() {
        return getInt("app.starvation_threshold_minutes", 30);
    }

    public static int getWaitEstimationHistoryLimit() {
        return getInt("app.wait_estimation_history_limit", 20);
    }
}

package com.smartqueue.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Health and connectivity status API.
 */
@RestController
@RequestMapping("/api")
@Tag(name = "System Health", description = "Endpoints for database connectivity and system status")
public class HealthController {

    private final DataSource dataSource;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/health")
    @Operation(summary = "Check database and application status", description = "Returns MySQL connection status, database product version, and schema name")
    public ResponseEntity<Map<String, Object>> getHealth() {
        Map<String, Object> response = new LinkedHashMap<>();
        try (Connection conn = dataSource.getConnection()) {
            response.put("status", "UP");
            response.put("service", "Smart Queue Web Backend");
            response.put("database", conn.getMetaData().getDatabaseProductName());
            response.put("databaseVersion", conn.getMetaData().getDatabaseProductVersion());
            response.put("databaseName", conn.getCatalog());
            response.put("connectionStatus", "Connected to MySQL");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("status", "DOWN");
            response.put("error", e.getMessage());
            response.put("connectionStatus", "Disconnected");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
        }
    }
}

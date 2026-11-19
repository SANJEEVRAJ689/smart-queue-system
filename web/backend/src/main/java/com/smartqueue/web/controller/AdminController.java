package com.smartqueue.web.controller;

import com.smartqueue.web.dto.AdminDtos.AnalyticsSummaryResponse;
import com.smartqueue.web.dto.AdminDtos.CreateStaffRequest;
import com.smartqueue.web.entity.*;
import com.smartqueue.web.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin Operations", description = "Administration, service/counter setup, analytics, and CSV exports")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/services")
    @Operation(summary = "List All Services", description = "Returns all services including inactive ones.")
    public ResponseEntity<List<ServiceEntity>> getServices() {
        return ResponseEntity.ok(adminService.getAllServices());
    }

    @PostMapping("/services")
    @Operation(summary = "Create Service", description = "Adds a new department service.")
    public ResponseEntity<ServiceEntity> createService(@Valid @RequestBody ServiceEntity service) {
        return ResponseEntity.ok(adminService.createService(service));
    }

    @PutMapping("/services/{id}")
    @Operation(summary = "Update Service", description = "Updates service details.")
    public ResponseEntity<ServiceEntity> updateService(@PathVariable int id, @Valid @RequestBody ServiceEntity service) {
        return ResponseEntity.ok(adminService.updateService(id, service));
    }

    @GetMapping("/counters")
    @Operation(summary = "List All Counters", description = "Returns all desks.")
    public ResponseEntity<List<CounterEntity>> getCounters() {
        return ResponseEntity.ok(adminService.getAllCounters());
    }

    @PostMapping("/counters")
    @Operation(summary = "Create Counter", description = "Adds a new service desk.")
    public ResponseEntity<CounterEntity> createCounter(@Valid @RequestBody CounterEntity counter) {
        return ResponseEntity.ok(adminService.createCounter(counter));
    }

    @PutMapping("/counters/{id}")
    @Operation(summary = "Update Counter", description = "Updates desk configuration.")
    public ResponseEntity<CounterEntity> updateCounter(@PathVariable int id, @Valid @RequestBody CounterEntity counter) {
        return ResponseEntity.ok(adminService.updateCounter(id, counter));
    }

    @GetMapping("/staff")
    @Operation(summary = "List Staff Accounts", description = "Returns user accounts.")
    public ResponseEntity<List<UserEntity>> getStaff() {
        return ResponseEntity.ok(adminService.getAllStaff());
    }

    @PostMapping("/staff")
    @Operation(summary = "Create Staff Account", description = "Creates a new staff or admin user.")
    public ResponseEntity<UserEntity> createStaff(@Valid @RequestBody CreateStaffRequest request) {
        return ResponseEntity.ok(adminService.createStaff(request));
    }

    @GetMapping({"/analytics", "/analytics/summary"})
    @Operation(summary = "Get Real-time Analytics", description = "Returns today's KPIs, hourly distribution, average wait, and service durations.")
    public ResponseEntity<AnalyticsSummaryResponse> getAnalytics() {
        return ResponseEntity.ok(adminService.getAnalyticsSummary());
    }

    @GetMapping("/analytics/hourly")
    @Operation(summary = "Hourly Token Stats", description = "Returns today's hourly token traffic distribution.")
    public ResponseEntity<List<com.smartqueue.web.dto.AdminDtos.HourlyStatResponse>> getHourlyStats() {
        return ResponseEntity.ok(adminService.getHourlyStats());
    }

    @GetMapping("/analytics/services")
    @Operation(summary = "Service Breakdown Stats", description = "Returns today's volume and wait metrics per service.")
    public ResponseEntity<List<com.smartqueue.web.dto.AdminDtos.ServiceStatResponse>> getServiceStats() {
        return ResponseEntity.ok(adminService.getServiceStats());
    }

    @GetMapping("/customers")
    @Operation(summary = "List Customers", description = "Returns all registered customers.")
    public ResponseEntity<List<com.smartqueue.web.dto.AdminDtos.CustomerResponse>> getCustomers() {
        return ResponseEntity.ok(adminService.getAllCustomers());
    }

    @GetMapping(value = "/export/csv", produces = "text/csv")
    @Operation(summary = "Export Tokens CSV", description = "Generates and downloads a complete CSV export of all tokens.")
    public ResponseEntity<String> exportCsv() {
        String csv = adminService.exportTokensToCsv();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"tokens_report.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }

    @GetMapping({"/audit-logs", "/audit-log"})
    @Operation(summary = "View Audit Log", description = "Returns the latest 100 system audit records.")
    public ResponseEntity<List<AuditLogEntity>> getAuditLogs() {
        return ResponseEntity.ok(adminService.getRecentAuditLogs());
    }
}

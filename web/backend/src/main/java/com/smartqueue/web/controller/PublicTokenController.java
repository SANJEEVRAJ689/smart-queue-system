package com.smartqueue.web.controller;

import com.smartqueue.web.entity.CounterEntity;
import com.smartqueue.web.entity.ServiceEntity;
import com.smartqueue.web.entity.TokenEntity;
import com.smartqueue.web.repository.CounterRepository;
import com.smartqueue.web.repository.ServiceRepository;
import com.smartqueue.web.repository.TokenRepository;
import com.smartqueue.web.service.TokenNumberGeneratorService;
import com.smartqueue.web.service.WebSocketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/public")
@Tag(name = "Public Tokens", description = "Public digital token issuance and live tracking for home page dispenser")
public class PublicTokenController {

    private final TokenRepository tokenRepository;
    private final ServiceRepository serviceRepository;
    private final CounterRepository counterRepository;
    private final TokenNumberGeneratorService tokenNumberGeneratorService;
    private final WebSocketService webSocketService;

    public PublicTokenController(
            TokenRepository tokenRepository,
            ServiceRepository serviceRepository,
            CounterRepository counterRepository,
            TokenNumberGeneratorService tokenNumberGeneratorService,
            WebSocketService webSocketService) {
        this.tokenRepository = tokenRepository;
        this.serviceRepository = serviceRepository;
        this.counterRepository = counterRepository;
        this.tokenNumberGeneratorService = tokenNumberGeneratorService;
        this.webSocketService = webSocketService;
    }

    public static class PublicTokenRequest {
        private Integer departmentId;
        private String customerName;

        public Integer getDepartmentId() {
            return departmentId;
        }

        public void setDepartmentId(Integer departmentId) {
            this.departmentId = departmentId;
        }

        public String getCustomerName() {
            return customerName;
        }

        public void setCustomerName(String customerName) {
            this.customerName = customerName;
        }
    }

    @GetMapping("/departments")
    @Operation(summary = "List Departments", description = "Returns active departments for home page token dispenser.")
    public ResponseEntity<List<ServiceEntity>> getDepartments() {
        return ResponseEntity.ok(serviceRepository.findByActiveTrue());
    }

    @PostMapping("/tokens")
    @Transactional
    @Operation(summary = "Issue Digital Token", description = "Generates next sequential token for department using row-locked sequence counter.")
    public ResponseEntity<Map<String, Object>> issueToken(@RequestBody PublicTokenRequest req) {
        if (req.getDepartmentId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "departmentId is required");
        }

        ServiceEntity service = serviceRepository.findById(req.getDepartmentId())
                .filter(s -> Boolean.TRUE.equals(s.getActive()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Department not found or inactive"));

        LocalDate today = LocalDate.now();
        String tokenNo = tokenNumberGeneratorService.generateNextTokenNumber(service.getPrefix());
        String trackingCode = UUID.randomUUID().toString().replace("-", "");

        String name = (req.getCustomerName() != null && !req.getCustomerName().isBlank())
                ? req.getCustomerName().trim()
                : "Guest Customer";

        TokenEntity token = new TokenEntity();
        token.setTokenNo(tokenNo);
        token.setServiceId(service.getId());
        token.setCustomerName(name);
        token.setPhone("9999999999");
        token.setPriority("NORMAL");
        token.setStatus("WAITING");
        token.setQueueDate(today);
        token.setCreatedAt(Instant.now());
        token.setTrackingCode(trackingCode);

        tokenRepository.save(token);

        // Calculate queue position (number of WAITING tokens in this department with id <= this token)
        long position = tokenRepository.findByQueueDateOrderByCreatedAtDesc(today).stream()
                .filter(t -> t.getServiceId().equals(service.getId()) && "WAITING".equals(t.getStatus()))
                .filter(t -> t.getId() <= token.getId())
                .count();
        if (position < 1) position = 1;

        int duration = service.getDefaultAvgMinutes() != null ? service.getDefaultAvgMinutes() : 5;
        int estimatedWait = (int) (position * duration);

        webSocketService.notifyQueueUpdated(service.getId());

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("tokenNo", tokenNo);
        resp.put("position", position);
        resp.put("estimatedWait", estimatedWait);
        resp.put("status", "WAITING");
        resp.put("trackingCode", trackingCode);
        resp.put("serviceName", service.getName());
        resp.put("counterName", null);

        return ResponseEntity.ok(resp);
    }

    @GetMapping("/tokens/{trackingCode}")
    @Operation(summary = "Check Token Status", description = "Retrieves current status, position, and desk assignment for a token.")
    public ResponseEntity<Map<String, Object>> getTokenStatus(@PathVariable String trackingCode) {
        TokenEntity token = tokenRepository.findByTrackingCode(trackingCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Token not found"));

        ServiceEntity service = serviceRepository.findById(token.getServiceId()).orElse(null);
        String serviceName = service != null ? service.getName() : "General";

        String counterName = null;
        if (token.getCounterId() != null) {
            CounterEntity counter = counterRepository.findById(token.getCounterId()).orElse(null);
            if (counter != null) {
                counterName = counter.getName();
            }
        }

        LocalDate today = token.getQueueDate() != null ? token.getQueueDate() : LocalDate.now();
        long position = 0;
        int estimatedWait = 0;

        if ("WAITING".equals(token.getStatus())) {
            position = tokenRepository.findByQueueDateOrderByCreatedAtDesc(today).stream()
                    .filter(t -> t.getServiceId().equals(token.getServiceId()) && "WAITING".equals(t.getStatus()))
                    .filter(t -> t.getId() <= token.getId())
                    .count();
            if (position < 1) position = 1;
            int duration = (service != null && service.getDefaultAvgMinutes() != null)
                    ? service.getDefaultAvgMinutes() : 5;
            estimatedWait = (int) (position * duration);
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("tokenNo", token.getTokenNo());
        resp.put("position", position);
        resp.put("estimatedWait", estimatedWait);
        resp.put("status", token.getStatus());
        resp.put("trackingCode", token.getTrackingCode());
        resp.put("serviceName", serviceName);
        resp.put("counterId", token.getCounterId());
        resp.put("counterName", counterName);

        return ResponseEntity.ok(resp);
    }
}

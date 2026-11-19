package com.smartqueue.web.controller;

import com.smartqueue.web.dto.StaffDtos.CounterStatusRequest;
import com.smartqueue.web.dto.StaffDtos.StaffTokenResponse;
import com.smartqueue.web.dto.StaffDtos.TransferRequest;
import com.smartqueue.web.entity.CounterEntity;
import com.smartqueue.web.repository.CounterRepository;
import com.smartqueue.web.service.StaffService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/staff")
@Tag(name = "Staff Operations", description = "Desk operations, queue handling, calling logic, and timers")
public class StaffController {

    private final StaffService staffService;
    private final CounterRepository counterRepository;

    public StaffController(StaffService staffService, CounterRepository counterRepository) {
        this.staffService = staffService;
        this.counterRepository = counterRepository;
    }

    @GetMapping("/counters")
    @Operation(summary = "List Desks", description = "Returns all service counters.")
    public ResponseEntity<List<CounterEntity>> getAllCounters() {
        return ResponseEntity.ok(counterRepository.findAll());
    }

    @PostMapping("/counters/{counterId}/call-next")
    @Operation(summary = "Call Next Customer", description = "Claims next waiting token using atomic row locking; returns existing token on double-click.")
    public ResponseEntity<Optional<StaffTokenResponse>> callNext(@PathVariable int counterId) {
        return ResponseEntity.ok(staffService.callNextToken(counterId, null));
    }

    @PostMapping("/tokens/{tokenId}/start")
    @Operation(summary = "Start Serving", description = "Transitions token to SERVING and records started_at.")
    public ResponseEntity<StaffTokenResponse> startServing(@PathVariable int tokenId) {
        return ResponseEntity.ok(staffService.startServing(tokenId, null));
    }

    @PostMapping("/tokens/{tokenId}/complete")
    @Operation(summary = "Complete Token", description = "Transitions to COMPLETED, records duration, frees counter, triggers auto-call if enabled.")
    public ResponseEntity<StaffTokenResponse> completeToken(
            @PathVariable int tokenId,
            @RequestParam int counterId) {
        return ResponseEntity.ok(staffService.completeToken(tokenId, counterId, null));
    }

    @PostMapping("/tokens/{tokenId}/skip")
    @Operation(summary = "Skip / No-Show", description = "Marks customer as SKIPPED, frees counter, triggers auto-call.")
    public ResponseEntity<StaffTokenResponse> skipToken(
            @PathVariable int tokenId,
            @RequestParam int counterId) {
        return ResponseEntity.ok(staffService.skipToken(tokenId, counterId, null));
    }

    @PostMapping("/tokens/{tokenId}/recall")
    @Operation(summary = "Recall Customer", description = "Recalls a skipped customer once. Re-enters at back of queue.")
    public ResponseEntity<StaffTokenResponse> recallToken(
            @PathVariable int tokenId,
            @RequestParam int counterId) {
        return ResponseEntity.ok(staffService.recallToken(tokenId, counterId, null));
    }

    @PostMapping("/tokens/{tokenId}/transfer")
    @Operation(summary = "Transfer Token", description = "Moves ticket to another department queue.")
    public ResponseEntity<StaffTokenResponse> transferToken(
            @PathVariable int tokenId,
            @RequestBody TransferRequest request) {
        return ResponseEntity.ok(staffService.transferToken(tokenId, request.getTargetServiceId(), null));
    }

    @PostMapping("/counters/{counterId}/status")
    @Operation(summary = "Update Desk Status", description = "Sets counter status to OPEN or CLOSED.")
    public ResponseEntity<Map<String, Object>> setCounterStatus(
            @PathVariable int counterId,
            @RequestBody CounterStatusRequest request) {
        boolean ok = staffService.setCounterStatus(counterId, request.getStatus());
        return ResponseEntity.ok(Map.of("success", ok, "status", request.getStatus()));
    }

    @PostMapping("/counters/{counterId}/auto-call")
    @Operation(summary = "Toggle Auto-Call", description = "Enables or disables automatic calling when a token is completed.")
    public ResponseEntity<Map<String, Object>> toggleAutoCall(
            @PathVariable int counterId,
            @RequestParam boolean enabled) {
        staffService.setAutoCallEnabled(counterId, enabled);
        return ResponseEntity.ok(Map.of("counterId", counterId, "autoCall", enabled));
    }

    @GetMapping("/counters/{counterId}/auto-call")
    @Operation(summary = "Get Auto-Call Status", description = "Returns whether auto-calling is enabled for the desk.")
    public ResponseEntity<Map<String, Object>> getAutoCall(@PathVariable int counterId) {
        boolean enabled = staffService.isAutoCallEnabled(counterId);
        return ResponseEntity.ok(Map.of("counterId", counterId, "autoCall", enabled));
    }

    @GetMapping("/queue")
    @Operation(summary = "View Live Queue", description = "Returns waiting queue optionally filtered by service.")
    public ResponseEntity<List<StaffTokenResponse>> getQueue(@RequestParam(required = false) Integer serviceId) {
        return ResponseEntity.ok(staffService.getWaitingQueue(serviceId));
    }

    @GetMapping("/history")
    @Operation(summary = "View Recent History", description = "Returns recently completed or skipped tokens.")
    public ResponseEntity<List<StaffTokenResponse>> getHistory() {
        return ResponseEntity.ok(staffService.getRecentHistory());
    }
}

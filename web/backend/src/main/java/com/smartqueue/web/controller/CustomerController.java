package com.smartqueue.web.controller;

import com.smartqueue.web.dto.CustomerDtos.BookTokenRequest;
import com.smartqueue.web.dto.CustomerDtos.BookTokenResponse;
import com.smartqueue.web.dto.CustomerDtos.TrackSessionRequest;
import com.smartqueue.web.dto.CustomerDtos.TrackTokenResponse;
import com.smartqueue.web.entity.ServiceEntity;
import com.smartqueue.web.repository.ServiceRepository;
import com.smartqueue.web.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/customer")
@Tag(name = "Customer", description = "Public guest token booking and tracking APIs")
public class CustomerController {

    private final CustomerService customerService;
    private final ServiceRepository serviceRepository;

    public CustomerController(CustomerService customerService, ServiceRepository serviceRepository) {
        this.customerService = customerService;
        this.serviceRepository = serviceRepository;
    }

    @GetMapping("/services")
    @Operation(summary = "List Active Services", description = "Returns active department services for customer booking.")
    public ResponseEntity<List<ServiceEntity>> getActiveServices() {
        return ResponseEntity.ok(serviceRepository.findByActiveTrue());
    }

    @PostMapping("/book")
    @Operation(summary = "Book Digital Token", description = "Generates a token number, queue position, estimated wait, and tracking code.")
    public ResponseEntity<BookTokenResponse> bookToken(
            @Valid @RequestBody BookTokenRequest request,
            HttpServletRequest servletRequest) {
        String clientIp = servletRequest.getRemoteAddr();
        return ResponseEntity.ok(customerService.bookToken(request, clientIp));
    }

    @PostMapping("/track-session")
    @Operation(summary = "Find Tracking Code", description = "Returns the secure tracking code for a valid token number + phone combination.")
    public ResponseEntity<Map<String, String>> trackSession(@Valid @RequestBody TrackSessionRequest request) {
        String trackingCode = customerService.trackSession(request);
        return ResponseEntity.ok(Map.of("trackingCode", trackingCode));
    }

    @GetMapping("/track/{trackingCode}")
    @Operation(summary = "Track Queue Status", description = "Public tracking endpoint using only trackingCode (masked phone and first name).")
    public ResponseEntity<TrackTokenResponse> trackByCode(@PathVariable String trackingCode) {
        return ResponseEntity.ok(customerService.trackByTrackingCode(trackingCode));
    }

    @PostMapping("/cancel/{trackingCode}")
    @Operation(summary = "Cancel Token", description = "Allows a waiting customer to cancel their ticket.")
    public ResponseEntity<Map<String, Object>> cancelToken(@PathVariable String trackingCode) {
        boolean ok = customerService.cancelToken(trackingCode);
        return ResponseEntity.ok(Map.of("success", ok, "message", "Token has been cancelled."));
    }
}

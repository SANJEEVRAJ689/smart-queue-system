package com.smartqueue.web.service;

import com.smartqueue.web.dto.CustomerDtos.BookTokenRequest;
import com.smartqueue.web.dto.CustomerDtos.BookTokenResponse;
import com.smartqueue.web.dto.CustomerDtos.TrackSessionRequest;
import com.smartqueue.web.dto.CustomerDtos.TrackTokenResponse;
import com.smartqueue.web.entity.CustomerEntity;
import com.smartqueue.web.entity.ServiceEntity;
import com.smartqueue.web.entity.TokenEntity;
import com.smartqueue.web.entity.TokenEventEntity;
import com.smartqueue.web.repository.*;
import com.smartqueue.web.security.RateLimitingService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Service
public class CustomerService {

    private final TokenRepository tokenRepository;
    private final ServiceRepository serviceRepository;
    private final CounterRepository counterRepository;
    private final CustomerRepository customerRepository;
    private final TokenEventRepository tokenEventRepository;
    private final TokenNumberGeneratorService tokenNumberGeneratorService;
    private final WaitTimeCalculationService waitTimeCalculationService;
    private final RateLimitingService rateLimitingService;
    private final WebSocketService webSocketService;

    public CustomerService(
            TokenRepository tokenRepository,
            ServiceRepository serviceRepository,
            CounterRepository counterRepository,
            CustomerRepository customerRepository,
            TokenEventRepository tokenEventRepository,
            TokenNumberGeneratorService tokenNumberGeneratorService,
            WaitTimeCalculationService waitTimeCalculationService,
            RateLimitingService rateLimitingService,
            WebSocketService webSocketService) {
        this.tokenRepository = tokenRepository;
        this.serviceRepository = serviceRepository;
        this.counterRepository = counterRepository;
        this.customerRepository = customerRepository;
        this.tokenEventRepository = tokenEventRepository;
        this.tokenNumberGeneratorService = tokenNumberGeneratorService;
        this.waitTimeCalculationService = waitTimeCalculationService;
        this.rateLimitingService = rateLimitingService;
        this.webSocketService = webSocketService;
    }

    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 5) return "***";
        int len = phone.length();
        return phone.substring(0, 3) + "*".repeat(Math.max(1, len - 5)) + phone.substring(len - 2);
    }

    public static String getFirstNameOnly(String fullName) {
        if (fullName == null || fullName.isBlank()) return "Guest";
        return fullName.trim().split("\\s+")[0];
    }

    @Transactional
    public BookTokenResponse bookToken(BookTokenRequest request, String clientIp) {
        if (!rateLimitingService.allowRequest(clientIp != null ? clientIp : "127.0.0.1")) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Booking rate limit exceeded. Please wait a minute.");
        }

        ServiceEntity service = serviceRepository.findById(request.getServiceId())
                .filter(s -> Boolean.TRUE.equals(s.getActive()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Service not found or inactive."));

        String cleanPhone = request.getPhone().trim().replaceAll("[^0-9+]", "");
        String cleanName = request.getCustomerName().trim();

        // Upsert customer
        CustomerEntity customer = customerRepository.findByPhone(cleanPhone)
                .orElseGet(() -> {
                    CustomerEntity c = new CustomerEntity();
                    c.setPhone(cleanPhone);
                    c.setFullName(cleanName);
                    return customerRepository.save(c);
                });

        String priority = "NORMAL";
        if (request.getPriorityType() != null && !request.getPriorityType().isBlank()) {
            priority = request.getPriorityType().toUpperCase();
        } else if (Boolean.TRUE.equals(request.getPriority())) {
            priority = "SENIOR";
        }

        String tokenNo = tokenNumberGeneratorService.generateNextTokenNumber(service.getPrefix());
        String trackingCode = UUID.randomUUID().toString().replace("-", "");

        TokenEntity token = new TokenEntity();
        token.setTokenNo(tokenNo);
        token.setServiceId(service.getId());
        token.setCustomerName(cleanName);
        token.setPhone(cleanPhone);
        token.setCustomerId(customer.getId());
        token.setPriority(priority);
        token.setStatus("WAITING");
        token.setQueueDate(LocalDate.now());
        token.setCreatedAt(Instant.now());
        token.setTrackingCode(trackingCode);

        TokenEntity saved = tokenRepository.save(token);

        tokenEventRepository.save(new TokenEventEntity(saved.getId(), null, "WAITING", null, null));

        int ahead = tokenRepository.countWaitingAhead(saved.getId(), service.getId(), saved.getQueueDate());
        int estWait = waitTimeCalculationService.estimateWaitMinutes(service.getId(), ahead);

        webSocketService.notifyQueueUpdated(service.getId());

        BookTokenResponse response = new BookTokenResponse();
        response.setTokenNo(saved.getTokenNo());
        response.setTrackingCode(saved.getTrackingCode());
        response.setServiceName(service.getName());
        response.setCustomerName(cleanName);
        response.setPhoneMasked(maskPhone(cleanPhone));
        response.setQueuePosition(ahead + 1);
        response.setEstimatedWaitMinutes(estWait);
        response.setServerTimeUtc(Instant.now().toString());

        return response;
    }

    @Transactional(readOnly = true)
    public String trackSession(TrackSessionRequest request) {
        String cleanPhone = request.getPhone().trim().replaceAll("[^0-9+]", "");
        String cleanToken = request.getTokenNo().trim().toUpperCase();

        TokenEntity token = tokenRepository.findByTokenNoAndPhoneAndQueueDate(cleanToken, cleanPhone, LocalDate.now())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Token not found for provided details today."));

        return token.getTrackingCode();
    }

    @Transactional(readOnly = true)
    public TrackTokenResponse trackByTrackingCode(String trackingCode) {
        TokenEntity token = tokenRepository.findByTrackingCode(trackingCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invalid tracking code."));

        ServiceEntity service = serviceRepository.findById(token.getServiceId()).orElse(null);
        String serviceName = service != null ? service.getName() : "Unknown Service";

        String counterName = null;
        if (token.getCounterId() != null) {
            counterName = counterRepository.findById(token.getCounterId())
                    .map(c -> c.getName())
                    .orElse("Counter " + token.getCounterId());
        }

        int ahead = 0;
        int estWait = 0;
        if ("WAITING".equals(token.getStatus())) {
            ahead = tokenRepository.countWaitingAhead(token.getId(), token.getServiceId(), token.getQueueDate());
            estWait = waitTimeCalculationService.estimateWaitMinutes(token.getServiceId(), ahead);
        }

        TrackTokenResponse resp = new TrackTokenResponse();
        resp.setTokenNo(token.getTokenNo());
        resp.setServiceName(serviceName);
        resp.setStatus(token.getStatus());
        resp.setPriority(token.getPriority());
        resp.setCounterName(counterName);
        resp.setCustomerName(getFirstNameOnly(token.getCustomerName()));
        resp.setPhoneMasked(maskPhone(token.getPhone()));
        resp.setQueuePosition("WAITING".equals(token.getStatus()) ? ahead + 1 : 0);
        resp.setEstimatedWaitMinutes(estWait);
        resp.setStartedAtUtc(token.getStartedAt() != null ? token.getStartedAt().toString() : null);
        resp.setCompletedAtUtc(token.getCompletedAt() != null ? token.getCompletedAt().toString() : null);
        resp.setDurationSeconds(token.getDurationSeconds());
        resp.setServerTimeUtc(Instant.now().toString());
        resp.setTrackingCode(token.getTrackingCode());

        return resp;
    }

    @Transactional
    public boolean cancelToken(String trackingCode) {
        TokenEntity token = tokenRepository.findByTrackingCode(trackingCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invalid tracking code."));

        if (!"WAITING".equals(token.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only WAITING tokens can be cancelled.");
        }

        token.setStatus("CANCELLED");
        token.setCompletedAt(Instant.now());
        tokenRepository.save(token);

        tokenEventRepository.save(new TokenEventEntity(token.getId(), "WAITING", "CANCELLED", null, null));
        webSocketService.notifyQueueUpdated(token.getServiceId());
        webSocketService.notifyTokenUpdated(token.getTrackingCode(), trackByTrackingCode(trackingCode));

        return true;
    }
}

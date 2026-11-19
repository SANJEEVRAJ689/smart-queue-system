package com.smartqueue.web.service;

import com.smartqueue.web.dto.AdminDtos.AnalyticsSummaryResponse;
import com.smartqueue.web.dto.AdminDtos.CreateStaffRequest;
import com.smartqueue.web.entity.*;
import com.smartqueue.web.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Service
public class AdminService {

    private final ServiceRepository serviceRepository;
    private final CounterRepository counterRepository;
    private final UserRepository userRepository;
    private final TokenRepository tokenRepository;
    private final AuditLogRepository auditLogRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminService(
            ServiceRepository serviceRepository,
            CounterRepository counterRepository,
            UserRepository userRepository,
            TokenRepository tokenRepository,
            AuditLogRepository auditLogRepository,
            PasswordEncoder passwordEncoder) {
        this.serviceRepository = serviceRepository;
        this.counterRepository = counterRepository;
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.auditLogRepository = auditLogRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<ServiceEntity> getAllServices() {
        return serviceRepository.findAll();
    }

    @Transactional
    public ServiceEntity createService(ServiceEntity service) {
        service.setId(null);
        return serviceRepository.save(service);
    }

    @Transactional
    public ServiceEntity updateService(int id, ServiceEntity updated) {
        ServiceEntity existing = serviceRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Service not found."));
        existing.setName(updated.getName());
        existing.setPrefix(updated.getPrefix());
        existing.setDefaultAvgMinutes(updated.getDefaultAvgMinutes());
        existing.setActive(updated.getActive());
        return serviceRepository.save(existing);
    }

    public List<CounterEntity> getAllCounters() {
        return counterRepository.findAll();
    }

    @Transactional
    public CounterEntity createCounter(CounterEntity counter) {
        counter.setId(null);
        return counterRepository.save(counter);
    }

    @Transactional
    public CounterEntity updateCounter(int id, CounterEntity updated) {
        CounterEntity existing = counterRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Counter not found."));
        existing.setName(updated.getName());
        existing.setServiceId(updated.getServiceId());
        existing.setStaffId(updated.getStaffId());
        existing.setStatus(updated.getStatus());
        return counterRepository.save(existing);
    }

    public List<UserEntity> getAllStaff() {
        return userRepository.findAll();
    }

    @Transactional
    public UserEntity createStaff(CreateStaffRequest request) {
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists.");
        }
        UserEntity user = new UserEntity();
        user.setUsername(request.getUsername().trim());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName().trim());
        user.setRole(request.getRole() != null ? request.getRole().toUpperCase() : "STAFF");
        user.setActive(true);
        return userRepository.save(user);
    }

    public AnalyticsSummaryResponse getAnalyticsSummary() {
        LocalDate today = LocalDate.now();
        List<TokenEntity> todayTokens = tokenRepository.findByQueueDateOrderByCreatedAtDesc(today);

        long total = todayTokens.size();
        long waiting = todayTokens.stream().filter(t -> "WAITING".equals(t.getStatus())).count();
        long serving = todayTokens.stream().filter(t -> "SERVING".equals(t.getStatus())).count();
        long completed = todayTokens.stream().filter(t -> "COMPLETED".equals(t.getStatus())).count();
        long skipped = todayTokens.stream().filter(t -> "SKIPPED".equals(t.getStatus())).count();

        double totalWaitSec = 0;
        int waitCount = 0;
        double totalServiceSec = 0;
        int serviceCount = 0;

        // Hourly breakdown (0..23)
        Map<Integer, Integer> hourlyMap = new TreeMap<>();
        for (int h = 0; h < 24; h++) hourlyMap.put(h, 0);

        for (TokenEntity t : todayTokens) {
            if (t.getCreatedAt() != null) {
                int hour = t.getCreatedAt().atZone(ZoneId.systemDefault()).getHour();
                hourlyMap.put(hour, hourlyMap.getOrDefault(hour, 0) + 1);
            }

            if (t.getCreatedAt() != null && t.getCalledAt() != null) {
                long wait = Duration.between(t.getCreatedAt(), t.getCalledAt()).getSeconds();
                if (wait >= 0) {
                    totalWaitSec += wait;
                    waitCount++;
                }
            }

            if (t.getDurationSeconds() != null && t.getDurationSeconds() > 0) {
                totalServiceSec += t.getDurationSeconds();
                serviceCount++;
            }
        }

        double avgWaitMins = waitCount > 0 ? (totalWaitSec / waitCount) / 60.0 : 0.0;
        double avgServiceMins = serviceCount > 0 ? (totalServiceSec / serviceCount) / 60.0 : 0.0;
        double noShowRate = (completed + skipped) > 0 ? ((double) skipped / (completed + skipped)) * 100.0 : 0.0;

        List<Map<String, Object>> hourlyList = new ArrayList<>();
        hourlyMap.forEach((h, count) -> {
            hourlyList.add(Map.of("hour", String.format("%02d:00", h), "tokens", count));
        });

        AnalyticsSummaryResponse resp = new AnalyticsSummaryResponse();
        resp.setTotalTokensToday(total);
        resp.setWaitingTokens(waiting);
        resp.setServingTokens(serving);
        resp.setCompletedTokens(completed);
        resp.setSkippedTokens(skipped);
        resp.setAvgWaitMinutes(Math.round(avgWaitMins * 10.0) / 10.0);
        resp.setAvgServiceMinutes(Math.round(avgServiceMins * 10.0) / 10.0);
        resp.setNoShowRate(Math.round(noShowRate * 10.0) / 10.0);
        resp.setServerTimeUtc(Instant.now().toString());
        resp.setHourlyDistribution(hourlyList);

        return resp;
    }

    public String exportTokensToCsv() {
        List<TokenEntity> tokens = tokenRepository.findAll();
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        // Header
        pw.println("ID,Token Number,Service ID,Customer Name,Phone,Priority,Status,Counter ID,Created At,Called At,Started At,Completed At,Duration Seconds");

        for (TokenEntity t : tokens) {
            pw.printf("%d,\"%s\",%d,\"%s\",\"%s\",\"%s\",\"%s\",%s,\"%s\",\"%s\",\"%s\",\"%s\",%s%n",
                    t.getId(),
                    t.getTokenNo(),
                    t.getServiceId(),
                    t.getCustomerName() != null ? t.getCustomerName().replace("\"", "\"\"") : "",
                    t.getPhone() != null ? t.getPhone() : "",
                    t.getPriority(),
                    t.getStatus(),
                    t.getCounterId() != null ? t.getCounterId().toString() : "",
                    t.getCreatedAt() != null ? t.getCreatedAt().toString() : "",
                    t.getCalledAt() != null ? t.getCalledAt().toString() : "",
                    t.getStartedAt() != null ? t.getStartedAt().toString() : "",
                    t.getCompletedAt() != null ? t.getCompletedAt().toString() : "",
                    t.getDurationSeconds() != null ? t.getDurationSeconds().toString() : "");
        }
        pw.flush();
        return sw.toString();
    }

    public List<AuditLogEntity> getRecentAuditLogs() {
        return auditLogRepository.findTop100ByOrderByTimestampDesc();
    }
}

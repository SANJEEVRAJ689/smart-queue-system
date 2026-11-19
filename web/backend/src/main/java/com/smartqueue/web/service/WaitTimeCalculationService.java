package com.smartqueue.web.service;

import com.smartqueue.web.entity.ServiceEntity;
import com.smartqueue.web.entity.TokenEntity;
import com.smartqueue.web.repository.CounterRepository;
import com.smartqueue.web.repository.ServiceRepository;
import com.smartqueue.web.repository.TokenRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class WaitTimeCalculationService {

    private final TokenRepository tokenRepository;
    private final CounterRepository counterRepository;
    private final ServiceRepository serviceRepository;

    public WaitTimeCalculationService(
            TokenRepository tokenRepository,
            CounterRepository counterRepository,
            ServiceRepository serviceRepository) {
        this.tokenRepository = tokenRepository;
        this.counterRepository = counterRepository;
        this.serviceRepository = serviceRepository;
    }

    public double getAverageServiceDurationMinutes(int serviceId) {
        List<TokenEntity> recent = tokenRepository.findRecentCompletedTokens(serviceId);
        if (recent.size() < 3) {
            return serviceRepository.findById(serviceId)
                    .map(ServiceEntity::getDefaultAvgMinutes)
                    .map(Integer::doubleValue)
                    .orElse(5.0);
        }

        double totalDurationSec = 0;
        int count = 0;
        for (TokenEntity t : recent) {
            if (t.getStartedAt() != null && t.getCompletedAt() != null) {
                long sec = Duration.between(t.getStartedAt(), t.getCompletedAt()).getSeconds();
                if (sec >= 3) {
                    totalDurationSec += sec;
                    count++;
                }
            }
        }
        return count >= 3 ? (totalDurationSec / count) / 60.0 : 5.0;
    }

    public int estimateWaitMinutes(int serviceId, int peopleAhead) {
        double avgMins = getAverageServiceDurationMinutes(serviceId);
        int activeCounters = Math.max(1, counterRepository.findOpenOrBusyCountersByService(serviceId).size());

        // Sum remaining active service time for currently serving tokens in this service
        double remainingActiveMins = 0.0;
        Instant now = Instant.now();
        List<TokenEntity> servingTokens = tokenRepository.findCurrentlyServingTokens();
        for (TokenEntity t : servingTokens) {
            if (t.getServiceId() == serviceId && "SERVING".equals(t.getStatus()) && t.getStartedAt() != null) {
                double elapsed = Duration.between(t.getStartedAt(), now).getSeconds() / 60.0;
                double remaining = Math.max(0.0, avgMins - elapsed);
                remainingActiveMins += remaining;
            }
        }

        double totalWorkMins = remainingActiveMins + (peopleAhead * avgMins);
        int estimated = (int) Math.round(totalWorkMins / activeCounters);
        return Math.max(0, estimated);
    }
}

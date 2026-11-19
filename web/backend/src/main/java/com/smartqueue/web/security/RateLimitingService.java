package com.smartqueue.web.security;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimitingService {

    private static final int MAX_REQUESTS_PER_MINUTE = 20;
    private static final long ONE_MINUTE_MS = 60 * 1000;

    private static class RequestWindow {
        int count;
        long windowStart;

        RequestWindow(int count, long windowStart) {
            this.count = count;
            this.windowStart = windowStart;
        }
    }

    private final Map<String, RequestWindow> ipRequests = new ConcurrentHashMap<>();

    public boolean allowRequest(String ip) {
        long now = System.currentTimeMillis();
        RequestWindow window = ipRequests.compute(ip, (key, w) -> {
            if (w == null || (now - w.windowStart > ONE_MINUTE_MS)) {
                return new RequestWindow(1, now);
            }
            w.count++;
            return w;
        });

        return window.count <= MAX_REQUESTS_PER_MINUTE;
    }
}

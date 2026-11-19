package com.smartqueue.web.security;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LoginAttemptService {

    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCK_TIME_DURATION_MS = 15 * 60 * 1000; // 15 minutes

    private static class Attempt {
        int count;
        long lastAttemptTime;

        Attempt(int count, long lastAttemptTime) {
            this.count = count;
            this.lastAttemptTime = lastAttemptTime;
        }
    }

    private final Map<String, Attempt> attemptsCache = new ConcurrentHashMap<>();

    public void loginSucceeded(String key) {
        attemptsCache.remove(key.toLowerCase());
    }

    public void loginFailed(String key) {
        String k = key.toLowerCase();
        long now = System.currentTimeMillis();
        attemptsCache.compute(k, (unused, attempt) -> {
            if (attempt == null || (now - attempt.lastAttemptTime > LOCK_TIME_DURATION_MS)) {
                return new Attempt(1, now);
            }
            attempt.count++;
            attempt.lastAttemptTime = now;
            return attempt;
        });
    }

    public boolean isBlocked(String key) {
        String k = key.toLowerCase();
        Attempt attempt = attemptsCache.get(k);
        if (attempt == null) {
            return false;
        }
        if (System.currentTimeMillis() - attempt.lastAttemptTime > LOCK_TIME_DURATION_MS) {
            attemptsCache.remove(k);
            return false;
        }
        return attempt.count >= MAX_ATTEMPTS;
    }
}

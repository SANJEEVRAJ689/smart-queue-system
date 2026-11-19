package com.smartqueue;

import com.smartqueue.model.Priority;
import com.smartqueue.model.Token;
import com.smartqueue.model.TokenStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for Token model comparison, priority sorting, and sequence formatting.
 */
public class TokenGeneratorTest {

    @Test
    public void testPriorityTokensSortBeforeRegularTokens() {
        LocalDateTime now = LocalDateTime.now();

        // Regular token created earlier (10 minutes ago)
        Token regular = new Token(1, "AD-001", 1, "Alice", "123", Priority.NORMAL, TokenStatus.WAITING, LocalDate.now());
        regular.setCreatedAt(now.minusMinutes(10));

        // Priority token created later (2 minutes ago)
        Token priority = new Token(2, "AD-002", 1, "Bob (Senior)", "456", Priority.SENIOR, TokenStatus.WAITING, LocalDate.now());
        priority.setCreatedAt(now.minusMinutes(2));

        List<Token> queue = new ArrayList<>();
        queue.add(regular);
        queue.add(priority);

        // Sort using Token's natural Comparable interface
        Collections.sort(queue);

        // Priority token must jump to position 0
        assertEquals("AD-002", queue.get(0).getTokenNo(), "Priority token must precede regular token");
        assertEquals("AD-001", queue.get(1).getTokenNo());
    }

    @Test
    public void testFifoOrderingWhenSamePriority() {
        LocalDateTime now = LocalDateTime.now();

        Token first = new Token(1, "LB-001", 3, "First", "111", Priority.NORMAL, TokenStatus.WAITING, LocalDate.now());
        first.setCreatedAt(now.minusMinutes(5));

        Token second = new Token(2, "LB-002", 3, "Second", "222", Priority.NORMAL, TokenStatus.WAITING, LocalDate.now());
        second.setCreatedAt(now.minusMinutes(2));

        List<Token> queue = new ArrayList<>();
        queue.add(second);
        queue.add(first);

        Collections.sort(queue);

        // Earlier creation must come first
        assertEquals("LB-001", queue.get(0).getTokenNo());
        assertEquals("LB-002", queue.get(1).getTokenNo());
    }
}

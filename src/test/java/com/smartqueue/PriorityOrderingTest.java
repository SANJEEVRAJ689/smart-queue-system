package com.smartqueue;

import com.smartqueue.model.Priority;
import com.smartqueue.model.Token;
import com.smartqueue.model.TokenStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests verifying queue priority ordering rules and starvation prevention:
 * 1. EMERGENCY (Rank 0) > SENIOR (Rank 1) > NORMAL (Rank 2)
 * 2. Starvation Prevention: A NORMAL token waiting >= 30 minutes is elevated to SENIOR (Rank 1).
 * 3. Within the same priority rank, FIFO by created_at applies.
 */
public class PriorityOrderingTest {

    @Test
    @DisplayName("Priority rank: EMERGENCY beats SENIOR, SENIOR beats NORMAL")
    void testBasicPriorityOrdering() {
        Token normal = new Token(1, "AD-001", 1, "Alice", "1234567", Priority.NORMAL, TokenStatus.WAITING, LocalDate.now());
        normal.setCreatedAt(LocalDateTime.now().minusMinutes(5));

        Token senior = new Token(2, "AD-002", 1, "Bob", "1234567", Priority.SENIOR, TokenStatus.WAITING, LocalDate.now());
        senior.setCreatedAt(LocalDateTime.now().minusMinutes(4));

        Token emergency = new Token(3, "AD-003", 1, "Charlie", "1234567", Priority.EMERGENCY, TokenStatus.WAITING, LocalDate.now());
        emergency.setCreatedAt(LocalDateTime.now().minusMinutes(1)); // Booked last!

        List<Token> queue = new ArrayList<>(List.of(normal, senior, emergency));
        Collections.sort(queue);

        // Emergency booked last, but must be called first
        assertEquals("AD-003", queue.get(0).getTokenNumber(), "Emergency token must be at head of queue");
        assertEquals("AD-002", queue.get(1).getTokenNumber(), "Senior citizen token must be 2nd");
        assertEquals("AD-001", queue.get(2).getTokenNumber(), "Normal token must be 3rd");
    }

    @Test
    @DisplayName("Starvation prevention: NORMAL token waiting 35 min elevated to SENIOR rank")
    void testStarvationPreventionElevation() {
        // Starving normal token that has been waiting 35 minutes
        Token starvingNormal = new Token(1, "AD-001", 1, "Alice", "1234567", Priority.NORMAL, TokenStatus.WAITING, LocalDate.now());
        starvingNormal.setCreatedAt(LocalDateTime.now().minusMinutes(35));

        // Fresh normal token that has been waiting 5 minutes
        Token freshNormal = new Token(2, "AD-002", 1, "Bob", "1234567", Priority.NORMAL, TokenStatus.WAITING, LocalDate.now());
        freshNormal.setCreatedAt(LocalDateTime.now().minusMinutes(5));

        assertEquals(1, starvingNormal.getEffectiveRank(30), "Normal token waiting >= 30m must be elevated to Rank 1");
        assertEquals(2, freshNormal.getEffectiveRank(30), "Normal token waiting < 30m remains Rank 2");

        List<Token> queue = new ArrayList<>(List.of(freshNormal, starvingNormal));
        Collections.sort(queue);

        assertEquals("AD-001", queue.get(0).getTokenNumber(), "Starving token must be prioritized ahead of fresh normal token");
    }

    @Test
    @DisplayName("FIFO tie-breaker within identical priority rank")
    void testFifoWithinSamePriority() {
        Token first = new Token(1, "AD-001", 1, "Alice", "1234567", Priority.NORMAL, TokenStatus.WAITING, LocalDate.now());
        first.setCreatedAt(LocalDateTime.now().minusMinutes(10));

        Token second = new Token(2, "AD-002", 1, "Bob", "1234567", Priority.NORMAL, TokenStatus.WAITING, LocalDate.now());
        second.setCreatedAt(LocalDateTime.now().minusMinutes(5));

        List<Token> queue = new ArrayList<>(List.of(second, first));
        Collections.sort(queue);

        assertEquals("AD-001", queue.get(0).getTokenNumber(), "Earlier created token must come first");
        assertEquals("AD-002", queue.get(1).getTokenNumber(), "Later created token must come second");
    }
}

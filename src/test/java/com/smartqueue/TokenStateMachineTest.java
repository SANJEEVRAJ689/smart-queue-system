package com.smartqueue;

import com.smartqueue.model.TokenStatus;
import com.smartqueue.service.TokenService;
import com.smartqueue.util.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests verifying that TokenService strictly enforces the Token State Machine.
 *
 * Rules:
 * WAITING -> CALLED -> SERVING -> COMPLETED
 * CALLED -> SKIPPED (no-show after timeout)
 * SKIPPED -> WAITING (recall, goes to back of normal queue)
 * WAITING -> CANCELLED (by customer or admin)
 *
 * All illegal transitions must throw AppException.
 */
public class TokenStateMachineTest {

    private TokenService tokenService;

    @BeforeEach
    void setUp() {
        tokenService = new TokenService();
    }

    @Test
    @DisplayName("Legal transition: WAITING -> CALLED")
    void testWaitingToCalledIsAllowed() {
        assertDoesNotThrow(() -> tokenService.validateTransition(TokenStatus.WAITING, TokenStatus.CALLED));
    }

    @Test
    @DisplayName("Legal transition: WAITING -> CANCELLED")
    void testWaitingToCancelledIsAllowed() {
        assertDoesNotThrow(() -> tokenService.validateTransition(TokenStatus.WAITING, TokenStatus.CANCELLED));
    }

    @Test
    @DisplayName("Legal transition: CALLED -> SERVING")
    void testCalledToServingIsAllowed() {
        assertDoesNotThrow(() -> tokenService.validateTransition(TokenStatus.CALLED, TokenStatus.SERVING));
    }

    @Test
    @DisplayName("Legal transition: CALLED -> SKIPPED (No-Show)")
    void testCalledToSkippedIsAllowed() {
        assertDoesNotThrow(() -> tokenService.validateTransition(TokenStatus.CALLED, TokenStatus.SKIPPED));
    }

    @Test
    @DisplayName("Legal transition: SERVING -> COMPLETED")
    void testServingToCompletedIsAllowed() {
        assertDoesNotThrow(() -> tokenService.validateTransition(TokenStatus.SERVING, TokenStatus.COMPLETED));
    }

    @Test
    @DisplayName("Legal transition: SKIPPED -> WAITING (Recall)")
    void testSkippedToWaitingIsAllowed() {
        assertDoesNotThrow(() -> tokenService.validateTransition(TokenStatus.SKIPPED, TokenStatus.WAITING));
    }

    @Test
    @DisplayName("Illegal transition: WAITING -> SERVING (must be CALLED first)")
    void testWaitingToServingThrowsException() {
        AppException ex = assertThrows(AppException.class, () ->
                tokenService.validateTransition(TokenStatus.WAITING, TokenStatus.SERVING)
        );
        assertTrue(ex.getMessage().contains("Illegal state transition"));
    }

    @Test
    @DisplayName("Illegal transition: WAITING -> COMPLETED (cannot skip to completion)")
    void testWaitingToCompletedThrowsException() {
        assertThrows(AppException.class, () ->
                tokenService.validateTransition(TokenStatus.WAITING, TokenStatus.COMPLETED)
        );
    }

    @Test
    @DisplayName("Illegal transition: CALLED -> COMPLETED (must be SERVING first)")
    void testCalledToCompletedThrowsException() {
        assertThrows(AppException.class, () ->
                tokenService.validateTransition(TokenStatus.CALLED, TokenStatus.COMPLETED)
        );
    }

    @Test
    @DisplayName("Illegal transition: COMPLETED -> WAITING (completed is terminal)")
    void testCompletedToWaitingThrowsException() {
        assertThrows(AppException.class, () ->
                tokenService.validateTransition(TokenStatus.COMPLETED, TokenStatus.WAITING)
        );
    }

    @Test
    @DisplayName("Illegal transition: CANCELLED -> WAITING (cancelled is terminal)")
    void testCancelledToWaitingThrowsException() {
        assertThrows(AppException.class, () ->
                tokenService.validateTransition(TokenStatus.CANCELLED, TokenStatus.WAITING)
        );
    }

    @Test
    @DisplayName("Null transition rejects with AppException")
    void testNullStatusThrowsException() {
        assertThrows(AppException.class, () -> tokenService.validateTransition(null, TokenStatus.WAITING));
        assertThrows(AppException.class, () -> tokenService.validateTransition(TokenStatus.WAITING, null));
    }
}

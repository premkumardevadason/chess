package com.example.chess.service;

import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Circuit breaker pattern implementation for resource protection
 * Prevents system overload by temporarily stopping operations when thresholds are exceeded
 */
@Component
public class CircuitBreaker {
    
    private static final Logger logger = LoggerFactory.getLogger(CircuitBreaker.class);
    
    // Circuit breaker states
    public enum State {
        CLOSED,    // Normal operation
        OPEN,      // Circuit is open, blocking operations
        HALF_OPEN  // Testing if service has recovered
    }
    
    // Configuration
    private static final int FAILURE_THRESHOLD = 5;        // Failures before opening circuit
    private static final long TIMEOUT_MS = 30000;          // 30 seconds before trying half-open
    private static final int SUCCESS_THRESHOLD = 3;        // Successes needed to close circuit
    
    // State tracking
    private volatile State state = State.CLOSED;
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicLong lastFailureTime = new AtomicLong(0);
    private final AtomicLong lastSuccessTime = new AtomicLong(0);
    
    /**
     * Check if the circuit breaker allows the operation
     */
    public boolean isOpen() {
        return state == State.OPEN;
    }
    
    /**
     * Check if the circuit breaker is closed (allows operations)
     */
    public boolean isClosed() {
        return state == State.CLOSED;
    }
    
    /**
     * Check if the circuit breaker is half-open (testing recovery)
     */
    public boolean isHalfOpen() {
        return state == State.HALF_OPEN;
    }
    
    /**
     * Record a successful operation
     */
    public void recordSuccess() {
        lastSuccessTime.set(System.currentTimeMillis());
        
        if (state == State.HALF_OPEN) {
            int successes = successCount.incrementAndGet();
            if (successes >= SUCCESS_THRESHOLD) {
                closeCircuit();
            }
        } else if (state == State.CLOSED) {
            // Reset failure count on success
            failureCount.set(0);
        }
        
        logger.debug("Circuit breaker recorded success, state: {}", state);
    }
    
    /**
     * Record a failed operation
     */
    public void recordFailure() {
        lastFailureTime.set(System.currentTimeMillis());
        
        if (state == State.CLOSED) {
            int failures = failureCount.incrementAndGet();
            if (failures >= FAILURE_THRESHOLD) {
                openCircuit();
            }
        } else if (state == State.HALF_OPEN) {
            // Failure in half-open state, go back to open
            openCircuit();
        }
        
        logger.warn("Circuit breaker recorded failure, state: {}, failures: {}", 
            state, failureCount.get());
    }
    
    /**
     * Check if enough time has passed to try half-open state
     */
    public boolean shouldAttemptReset() {
        if (state == State.OPEN) {
            long timeSinceLastFailure = System.currentTimeMillis() - lastFailureTime.get();
            return timeSinceLastFailure >= TIMEOUT_MS;
        }
        return false;
    }
    
    /**
     * Attempt to reset the circuit breaker to half-open state
     */
    public void attemptReset() {
        if (shouldAttemptReset()) {
            state = State.HALF_OPEN;
            successCount.set(0);
            logger.info("Circuit breaker attempting reset to HALF_OPEN state");
        }
    }
    
    /**
     * Open the circuit breaker
     */
    private void openCircuit() {
        state = State.OPEN;
        logger.warn("Circuit breaker opened due to failure threshold exceeded");
    }
    
    /**
     * Close the circuit breaker
     */
    private void closeCircuit() {
        state = State.CLOSED;
        failureCount.set(0);
        successCount.set(0);
        logger.info("Circuit breaker closed, normal operation resumed");
    }
    
    /**
     * Get current circuit breaker state
     */
    public State getState() {
        return state;
    }
    
    /**
     * Get failure count
     */
    public int getFailureCount() {
        return failureCount.get();
    }
    
    /**
     * Get success count
     */
    public int getSuccessCount() {
        return successCount.get();
    }
    
    /**
     * Reset the circuit breaker to closed state (for testing)
     */
    public void reset() {
        state = State.CLOSED;
        failureCount.set(0);
        successCount.set(0);
        lastFailureTime.set(0);
        lastSuccessTime.set(0);
        logger.info("Circuit breaker manually reset");
    }
    
    /**
     * Get circuit breaker status information
     */
    public CircuitBreakerStatus getStatus() {
        return new CircuitBreakerStatus(
            state,
            failureCount.get(),
            successCount.get(),
            lastFailureTime.get(),
            lastSuccessTime.get()
        );
    }
    
    /**
     * Circuit breaker status information
     */
    public static class CircuitBreakerStatus {
        public final State state;
        public final int failureCount;
        public final int successCount;
        public final long lastFailureTime;
        public final long lastSuccessTime;
        
        public CircuitBreakerStatus(State state, int failureCount, int successCount,
                                  long lastFailureTime, long lastSuccessTime) {
            this.state = state;
            this.failureCount = failureCount;
            this.successCount = successCount;
            this.lastFailureTime = lastFailureTime;
            this.lastSuccessTime = lastSuccessTime;
        }
        
        @Override
        public String toString() {
            return String.format("CircuitBreaker{state=%s, failures=%d, successes=%d}",
                state, failureCount, successCount);
        }
    }
}

package com.example.chess.DB2;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Circuit Breaker Pattern Implementation for DB2 Migration
 * 
 * This class implements the circuit breaker pattern to prevent cascading failures
 * during DB2 migration operations. It automatically opens the circuit when too many
 * failures occur and allows it to close again after a recovery period.
 */
public class DB2MigrationCircuitBreaker {
    
    private static final Logger logger = LogManager.getLogger(DB2MigrationCircuitBreaker.class);
    
    // Circuit states
    public enum CircuitState {
        CLOSED,    // Normal operation - requests are allowed
        OPEN,      // Circuit is open - requests are blocked
        HALF_OPEN  // Testing if service has recovered
    }
    
    // Configuration constants
    private static final int DEFAULT_FAILURE_THRESHOLD = 5;
    private static final long DEFAULT_RECOVERY_TIMEOUT = 60000; // 1 minute
    private static final int DEFAULT_SUCCESS_THRESHOLD = 3;
    private static final long DEFAULT_MONITORING_WINDOW = 300000; // 5 minutes
    
    // Circuit state
    private final AtomicReference<CircuitState> state = new AtomicReference<>(CircuitState.CLOSED);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicLong lastFailureTime = new AtomicLong(0);
    private final AtomicLong lastSuccessTime = new AtomicLong(0);
    private final AtomicLong circuitOpenTime = new AtomicLong(0);
    
    // Configuration
    private final int failureThreshold;
    private final long recoveryTimeout;
    private final int successThreshold;
    private final long monitoringWindow;
    
    // Thread safety
    private final ReentrantLock stateLock = new ReentrantLock();
    
    // Metrics
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong totalFailures = new AtomicLong(0);
    private final AtomicLong totalSuccesses = new AtomicLong(0);
    private final AtomicLong totalCircuitOpens = new AtomicLong(0);
    
    /**
     * Constructor with default configuration
     */
    public DB2MigrationCircuitBreaker() {
        this(DEFAULT_FAILURE_THRESHOLD, DEFAULT_RECOVERY_TIMEOUT, 
             DEFAULT_SUCCESS_THRESHOLD, DEFAULT_MONITORING_WINDOW);
    }
    
    /**
     * Constructor with custom configuration
     * @param failureThreshold Number of failures before opening circuit
     * @param recoveryTimeout Time to wait before attempting to close circuit
     * @param successThreshold Number of successes before closing circuit
     * @param monitoringWindow Time window for monitoring failures
     */
    public DB2MigrationCircuitBreaker(int failureThreshold, long recoveryTimeout, 
                                     int successThreshold, long monitoringWindow) {
        this.failureThreshold = failureThreshold;
        this.recoveryTimeout = recoveryTimeout;
        this.successThreshold = successThreshold;
        this.monitoringWindow = monitoringWindow;
        
        logger.info("Circuit breaker initialized: failureThreshold={}, recoveryTimeout={}ms, " +
                   "successThreshold={}, monitoringWindow={}ms", 
                   failureThreshold, recoveryTimeout, successThreshold, monitoringWindow);
    }
    
    /**
     * Check if the circuit breaker allows execution
     * @return true if execution is allowed, false otherwise
     */
    public boolean canExecute() {
        totalRequests.incrementAndGet();
        
        CircuitState currentState = state.get();
        
        switch (currentState) {
            case CLOSED:
                // Check if we should open the circuit
                if (shouldOpenCircuit()) {
                    openCircuit();
                    return false;
                }
                return true;
                
            case OPEN:
                // Check if recovery timeout has passed
                if (shouldAttemptRecovery()) {
                    attemptRecovery();
                    return false; // Still not ready, but attempting recovery
                }
                return false;
                
            case HALF_OPEN:
                // Allow limited execution to test recovery
                return true;
                
            default:
                return false;
        }
    }
    
    /**
     * Record a successful operation
     */
    public void recordSuccess() {
        totalSuccesses.incrementAndGet();
        lastSuccessTime.set(System.currentTimeMillis());
        
        CircuitState currentState = state.get();
        
        switch (currentState) {
            case CLOSED:
                // Reset failure count on success
                failureCount.set(0);
                successCount.incrementAndGet();
                break;
                
            case HALF_OPEN:
                // Increment success count
                successCount.incrementAndGet();
                
                // Check if we should close the circuit
                if (successCount.get() >= successThreshold) {
                    closeCircuit();
                }
                break;
                
            case OPEN:
                // Should not happen in OPEN state
                logger.warn("Success recorded while circuit is OPEN");
                break;
        }
        
        logger.debug("Success recorded. Current state: {}, successCount: {}", currentState, successCount.get());
    }
    
    /**
     * Record a failed operation
     */
    public void recordFailure() {
        totalFailures.incrementAndGet();
        lastFailureTime.set(System.currentTimeMillis());
        
        CircuitState currentState = state.get();
        
        switch (currentState) {
            case CLOSED:
                // Increment failure count
                int failures = failureCount.incrementAndGet();
                logger.debug("Failure recorded. Failure count: {}/{}", failures, failureThreshold);
                
                // Check if we should open the circuit
                if (failures >= failureThreshold) {
                    openCircuit();
                }
                break;
                
            case HALF_OPEN:
                // Failure in half-open state, open circuit again
                openCircuit();
                break;
                
            case OPEN:
                // Already open, just log
                logger.debug("Failure recorded while circuit is OPEN");
                break;
        }
    }
    
    /**
     * Execute operation with circuit breaker protection
     * @param operation Operation to execute
     * @param <T> Return type
     * @return Result of operation
     * @throws Exception if operation fails
     */
    public <T> T execute(ThrowingSupplier<T> operation) throws Exception {
        if (!canExecute()) {
            throw new CircuitBreakerOpenException("Circuit breaker is OPEN - operation blocked");
        }
        
        try {
            T result = operation.get();
            recordSuccess();
            return result;
        } catch (Exception e) {
            recordFailure();
            throw e;
        }
    }
    
    /**
     * Execute operation with circuit breaker protection (void return)
     * @param operation Operation to execute
     * @throws Exception if operation fails
     */
    public void execute(ThrowingRunnable operation) throws Exception {
        if (!canExecute()) {
            throw new CircuitBreakerOpenException("Circuit breaker is OPEN - operation blocked");
        }
        
        try {
            operation.run();
            recordSuccess();
        } catch (Exception e) {
            recordFailure();
            throw e;
        }
    }
    
    /**
     * Check if circuit should be opened
     * @return true if circuit should be opened
     */
    private boolean shouldOpenCircuit() {
        long currentTime = System.currentTimeMillis();
        long lastFailure = lastFailureTime.get();
        
        // Check if we're within the monitoring window
        if (currentTime - lastFailure > monitoringWindow) {
            // Reset failure count if outside monitoring window
            failureCount.set(0);
            return false;
        }
        
        return failureCount.get() >= failureThreshold;
    }
    
    /**
     * Check if recovery should be attempted
     * @return true if recovery should be attempted
     */
    private boolean shouldAttemptRecovery() {
        long currentTime = System.currentTimeMillis();
        long circuitOpen = circuitOpenTime.get();
        
        return (currentTime - circuitOpen) >= recoveryTimeout;
    }
    
    /**
     * Open the circuit
     */
    private void openCircuit() {
        stateLock.lock();
        try {
            if (state.get() != CircuitState.OPEN) {
                state.set(CircuitState.OPEN);
                circuitOpenTime.set(System.currentTimeMillis());
                totalCircuitOpens.incrementAndGet();
                
                logger.warn("Circuit breaker OPENED. Failure count: {}/{}", 
                           failureCount.get(), failureThreshold);
            }
        } finally {
            stateLock.unlock();
        }
    }
    
    /**
     * Attempt to recover the circuit
     */
    private void attemptRecovery() {
        stateLock.lock();
        try {
            if (state.get() == CircuitState.OPEN) {
                state.set(CircuitState.HALF_OPEN);
                successCount.set(0);
                
                logger.info("Circuit breaker attempting recovery - moved to HALF_OPEN state");
            }
        } finally {
            stateLock.unlock();
        }
    }
    
    /**
     * Close the circuit
     */
    private void closeCircuit() {
        stateLock.lock();
        try {
            if (state.get() == CircuitState.HALF_OPEN) {
                state.set(CircuitState.CLOSED);
                failureCount.set(0);
                successCount.set(0);
                
                logger.info("Circuit breaker CLOSED - normal operation resumed");
            }
        } finally {
            stateLock.unlock();
        }
    }
    
    /**
     * Get current circuit state
     * @return Current circuit state
     */
    public CircuitState getState() {
        return state.get();
    }
    
    /**
     * Get circuit breaker statistics
     * @return Statistics map
     */
    public java.util.Map<String, Object> getStatistics() {
        java.util.Map<String, Object> stats = new java.util.HashMap<>();
        
        stats.put("currentState", state.get().name());
        stats.put("failureCount", failureCount.get());
        stats.put("successCount", successCount.get());
        stats.put("failureThreshold", failureThreshold);
        stats.put("successThreshold", successThreshold);
        stats.put("recoveryTimeout", recoveryTimeout);
        stats.put("monitoringWindow", monitoringWindow);
        
        // Timing information
        long currentTime = System.currentTimeMillis();
        stats.put("lastFailureTime", lastFailureTime.get());
        stats.put("lastSuccessTime", lastSuccessTime.get());
        stats.put("circuitOpenTime", circuitOpenTime.get());
        stats.put("timeSinceLastFailure", currentTime - lastFailureTime.get());
        stats.put("timeSinceLastSuccess", currentTime - lastSuccessTime.get());
        
        // Metrics
        stats.put("totalRequests", totalRequests.get());
        stats.put("totalFailures", totalFailures.get());
        stats.put("totalSuccesses", totalSuccesses.get());
        stats.put("totalCircuitOpens", totalCircuitOpens.get());
        
        // Calculate success rate
        if (totalRequests.get() > 0) {
            double successRate = (double) totalSuccesses.get() / totalRequests.get() * 100;
            stats.put("successRate", String.format("%.2f%%", successRate));
        }
        
        return stats;
    }
    
    /**
     * Reset circuit breaker to initial state
     */
    public void reset() {
        stateLock.lock();
        try {
            state.set(CircuitState.CLOSED);
            failureCount.set(0);
            successCount.set(0);
            lastFailureTime.set(0);
            lastSuccessTime.set(0);
            circuitOpenTime.set(0);
            
            logger.info("Circuit breaker reset to initial state");
        } finally {
            stateLock.unlock();
        }
    }
    
    /**
     * Force circuit to open state
     */
    public void forceOpen() {
        stateLock.lock();
        try {
            state.set(CircuitState.OPEN);
            circuitOpenTime.set(System.currentTimeMillis());
            totalCircuitOpens.incrementAndGet();
            
            logger.warn("Circuit breaker forced OPEN");
        } finally {
            stateLock.unlock();
        }
    }
    
    /**
     * Force circuit to closed state
     */
    public void forceClose() {
        stateLock.lock();
        try {
            state.set(CircuitState.CLOSED);
            failureCount.set(0);
            successCount.set(0);
            
            logger.info("Circuit breaker forced CLOSED");
        } finally {
            stateLock.unlock();
        }
    }
    
    /**
     * Check if circuit is healthy
     * @return true if circuit is healthy
     */
    public boolean isHealthy() {
        CircuitState currentState = state.get();
        return currentState == CircuitState.CLOSED || currentState == CircuitState.HALF_OPEN;
    }
    
    /**
     * Functional interface for operations that return a value
     * @param <T> Return type
     */
    @FunctionalInterface
    public interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
    
    /**
     * Functional interface for operations that don't return a value
     */
    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }
    
    /**
     * Exception thrown when circuit breaker is open
     */
    public static class CircuitBreakerOpenException extends RuntimeException {
        public CircuitBreakerOpenException(String message) {
            super(message);
        }
        
        public CircuitBreakerOpenException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

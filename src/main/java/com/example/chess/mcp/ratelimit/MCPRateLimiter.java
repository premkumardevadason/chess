package com.example.chess.mcp.ratelimit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class MCPRateLimiter {
    
    private static final Logger logger = LogManager.getLogger(MCPRateLimiter.class);
    
    private final int requestsPerMinute = 100;
    private final int movesPerMinute = 60;
    private final int burstLimit = 10;
    
    private final ConcurrentHashMap<String, RateLimitBucket> agentBuckets = new ConcurrentHashMap<>();
    
    public boolean allowRequest(String agentId, String requestType) {
        RateLimitBucket bucket = agentBuckets.computeIfAbsent(agentId, k -> new RateLimitBucket());
        
        LocalDateTime now = LocalDateTime.now();
        bucket.cleanup(now);
        
        // Check burst limit first (10 requests in 10 seconds)
        long recentRequests = bucket.getRequestsInWindow(now, java.time.Duration.ofSeconds(10));
        if (recentRequests >= burstLimit) {
            logger.warn("Burst limit exceeded for agent {}: {} requests in 10 seconds", agentId, recentRequests);
            return false;
        }
        
        int limit = getLimit(requestType);
        if (bucket.getRequestCount() >= limit) {
            logger.warn("Rate limit exceeded for agent {}: {} requests", agentId, bucket.getRequestCount());
            return false;
        }
        
        bucket.addRequest(now);
        return true;
    }
    
    public RateLimitStatus getStatus(String agentId) {
        RateLimitBucket bucket = agentBuckets.get(agentId);
        if (bucket == null) {
            return new RateLimitStatus(0, requestsPerMinute, 0);
        }
        
        bucket.cleanup(LocalDateTime.now());
        return new RateLimitStatus(
            bucket.getRequestCount(),
            requestsPerMinute,
            Math.max(0, requestsPerMinute - bucket.getRequestCount())
        );
    }
    
    private int getLimit(String requestType) {
        switch (requestType) {
            case "move":
                return movesPerMinute;
            case "burst":
                return burstLimit;
            default:
                return requestsPerMinute;
        }
    }
    
    private static class RateLimitBucket {
        private final java.util.List<LocalDateTime> requestTimes = new java.util.concurrent.CopyOnWriteArrayList<>();
        
        void addRequest(LocalDateTime time) {
            requestTimes.add(time);
        }
        
        int getRequestCount() {
            return requestTimes.size();
        }
        
        long getRequestsInWindow(LocalDateTime now, java.time.Duration window) {
            LocalDateTime cutoff = now.minus(window);
            return requestTimes.stream().filter(time -> time.isAfter(cutoff)).count();
        }
        
        void cleanup(LocalDateTime now) {
            LocalDateTime cutoff = now.minusMinutes(1);
            requestTimes.removeIf(time -> time.isBefore(cutoff));
        }
    }
    
    public static class RateLimitStatus {
        private final int currentRequests;
        private final int maxRequests;
        private final int remainingRequests;
        
        public RateLimitStatus(int currentRequests, int maxRequests, int remainingRequests) {
            this.currentRequests = currentRequests;
            this.maxRequests = maxRequests;
            this.remainingRequests = remainingRequests;
        }
        
        public int getCurrentRequests() { return currentRequests; }
        public int getMaxRequests() { return maxRequests; }
        public int getRemainingRequests() { return remainingRequests; }
    }
}
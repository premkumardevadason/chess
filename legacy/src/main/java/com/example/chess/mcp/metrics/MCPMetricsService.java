package com.example.chess.mcp.metrics;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class MCPMetricsService {
    
    private static final Logger logger = LogManager.getLogger(MCPMetricsService.class);
    
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong totalErrors = new AtomicLong(0);
    private final ConcurrentHashMap<String, AtomicLong> toolCallCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> agentRequestCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> responseTimeSum = new ConcurrentHashMap<>();
    private final LocalDateTime startTime = LocalDateTime.now();
    
    public void recordRequest(String agentId, String toolName, long responseTimeMs) {
        totalRequests.incrementAndGet();
        toolCallCounts.computeIfAbsent(toolName, k -> new AtomicLong(0)).incrementAndGet();
        agentRequestCounts.computeIfAbsent(agentId, k -> new AtomicLong(0)).incrementAndGet();
        responseTimeSum.merge(toolName, responseTimeMs, Long::sum);
        
        logger.debug("Recorded request: agent={}, tool={}, responseTime={}ms", 
                    agentId, toolName, responseTimeMs);
    }
    
    public void recordError(String agentId, String toolName, String errorType) {
        totalErrors.incrementAndGet();
        logger.warn("Recorded error: agent={}, tool={}, error={}", agentId, toolName, errorType);
    }
    
    public MCPMetrics getMetrics() {
        return new MCPMetrics(
            totalRequests.get(),
            totalErrors.get(),
            new ConcurrentHashMap<>(toolCallCounts),
            new ConcurrentHashMap<>(agentRequestCounts),
            startTime
        );
    }
    
    public double getAverageResponseTime(String toolName) {
        Long sum = responseTimeSum.get(toolName);
        AtomicLong count = toolCallCounts.get(toolName);
        if (sum == null || count == null || count.get() == 0) {
            return 0.0;
        }
        return (double) sum / count.get();
    }
    
    public static class MCPMetrics {
        private final long totalRequests;
        private final long totalErrors;
        private final ConcurrentHashMap<String, AtomicLong> toolCallCounts;
        private final ConcurrentHashMap<String, AtomicLong> agentRequestCounts;
        private final LocalDateTime startTime;
        
        public MCPMetrics(long totalRequests, long totalErrors, 
                         ConcurrentHashMap<String, AtomicLong> toolCallCounts,
                         ConcurrentHashMap<String, AtomicLong> agentRequestCounts,
                         LocalDateTime startTime) {
            this.totalRequests = totalRequests;
            this.totalErrors = totalErrors;
            this.toolCallCounts = toolCallCounts;
            this.agentRequestCounts = agentRequestCounts;
            this.startTime = startTime;
        }
        
        public long getTotalRequests() { return totalRequests; }
        public long getTotalErrors() { return totalErrors; }
        public ConcurrentHashMap<String, AtomicLong> getToolCallCounts() { return toolCallCounts; }
        public ConcurrentHashMap<String, AtomicLong> getAgentRequestCounts() { return agentRequestCounts; }
        public LocalDateTime getStartTime() { return startTime; }
    }
}
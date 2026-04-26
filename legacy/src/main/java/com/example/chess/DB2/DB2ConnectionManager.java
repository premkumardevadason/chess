package com.example.chess.DB2;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Secure Connection Management for DB2 Migration
 * 
 * This class provides secure database connection management with connection pooling,
 * connection health monitoring, and automatic retry mechanisms for DB2 migration operations.
 */
public class DB2ConnectionManager {
    
    private static final Logger logger = LogManager.getLogger(DB2ConnectionManager.class);
    
    // Connection pool settings
    private static final int DEFAULT_MAX_POOL_SIZE = 10;
    private static final int DEFAULT_MIN_POOL_SIZE = 2;
    private static final long DEFAULT_CONNECTION_TIMEOUT = 30000; // 30 seconds
    private static final long DEFAULT_IDLE_TIMEOUT = 300000; // 5 minutes
    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final long DEFAULT_RETRY_DELAY = 1000; // 1 second
    
    // Connection pool state
    private final ConcurrentHashMap<Connection, ConnectionInfo> connectionPool = new ConcurrentHashMap<>();
    private final AtomicInteger activeConnections = new AtomicInteger(0);
    private final AtomicInteger totalConnections = new AtomicInteger(0);
    private final AtomicLong totalConnectionTime = new AtomicLong(0);
    private final ReentrantLock poolLock = new ReentrantLock();
    
    // Configuration
    private final Properties config;
    private final String connectionType; // "ONPREM" or "RDS"
    private final int maxPoolSize;
    private final int minPoolSize;
    private final long connectionTimeout;
    private final long idleTimeout;
    private final int maxRetries;
    private final long retryDelay;
    
    /**
     * Connection information for monitoring
     */
    private static class ConnectionInfo {
        final long creationTime;
        long lastUsedTime;
        final AtomicInteger useCount;
        volatile boolean isValid;
        
        ConnectionInfo() {
            this.creationTime = System.currentTimeMillis();
            this.lastUsedTime = System.currentTimeMillis();
            this.useCount = new AtomicInteger(0);
            this.isValid = true;
        }
        
        void markUsed() {
            this.lastUsedTime = System.currentTimeMillis();
            this.useCount.incrementAndGet();
        }
        
        boolean isExpired(long timeout) {
            return (System.currentTimeMillis() - lastUsedTime) > timeout;
        }
    }
    
    /**
     * Constructor for connection manager
     * @param config Database configuration properties
     * @param connectionType Type of connection ("ONPREM" or "RDS")
     */
    public DB2ConnectionManager(Properties config, String connectionType) {
        this.config = config;
        this.connectionType = connectionType;
        
        // Load configuration with defaults
        this.maxPoolSize = Integer.parseInt(config.getProperty("maxPoolSize", String.valueOf(DEFAULT_MAX_POOL_SIZE)));
        this.minPoolSize = Integer.parseInt(config.getProperty("minPoolSize", String.valueOf(DEFAULT_MIN_POOL_SIZE)));
        this.connectionTimeout = Long.parseLong(config.getProperty("connectionTimeout", String.valueOf(DEFAULT_CONNECTION_TIMEOUT)));
        this.idleTimeout = Long.parseLong(config.getProperty("idleTimeout", String.valueOf(DEFAULT_IDLE_TIMEOUT)));
        this.maxRetries = Integer.parseInt(config.getProperty("maxRetries", String.valueOf(DEFAULT_MAX_RETRIES)));
        this.retryDelay = Long.parseLong(config.getProperty("retryDelay", String.valueOf(DEFAULT_RETRY_DELAY)));
        
        logger.info("DB2ConnectionManager initialized for {} with pool size: {}-{}", 
                   connectionType, minPoolSize, maxPoolSize);
        
        // Initialize connection pool
        initializePool();
    }
    
    /**
     * Get a database connection from the pool
     * @return Database connection
     * @throws SQLException if connection cannot be established
     */
    public Connection getConnection() throws SQLException {
        return getConnectionWithRetry();
    }
    
    /**
     * Get connection with retry mechanism
     * @return Database connection
     * @throws SQLException if connection cannot be established after retries
     */
    private Connection getConnectionWithRetry() throws SQLException {
        SQLException lastException = null;
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                Connection conn = getConnectionFromPool();
                if (conn != null && isValidConnection(conn)) {
                    return conn;
                }
            } catch (SQLException e) {
                lastException = e;
                logger.warn("Connection attempt {} failed for {}: {}", attempt, connectionType, e.getMessage());
                
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(retryDelay * attempt); // Exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new SQLException("Connection retry interrupted", ie);
                    }
                }
            }
        }
        
        // All retries failed
        String errorMsg = String.format("Failed to establish connection after %d attempts for %s", maxRetries, connectionType);
        logger.error(errorMsg);
        throw new SQLException(errorMsg, lastException);
    }
    
    /**
     * Get connection from pool or create new one
     * @return Database connection
     * @throws SQLException if connection creation fails
     */
    private Connection getConnectionFromPool() throws SQLException {
        // Try to get existing connection from pool
        for (Map.Entry<Connection, ConnectionInfo> entry : connectionPool.entrySet()) {
            Connection conn = entry.getKey();
            ConnectionInfo info = entry.getValue();
            
            if (info.isValid && !info.isExpired(idleTimeout)) {
                if (connectionPool.remove(conn) != null) {
                    activeConnections.incrementAndGet();
                    info.markUsed();
                    connectionPool.put(conn, info);
                    logger.debug("Reused existing connection from pool for {}", connectionType);
                    return conn;
                }
            }
        }
        
        // Create new connection if pool not full
        if (activeConnections.get() < maxPoolSize) {
            return createNewConnection();
        }
        
        // Wait for available connection
        return waitForAvailableConnection();
    }
    
    /**
     * Create new database connection
     * @return New database connection
     * @throws SQLException if connection creation fails
     */
    private Connection createNewConnection() throws SQLException {
        String url = config.getProperty("url");
        String user = config.getProperty("user");
        String password = config.getProperty("password");
        
        logger.debug("Creating new connection for {} to {}", connectionType, url);
        
        Connection conn = DriverManager.getConnection(url, user, password);
        
        // Configure connection
        conn.setAutoCommit(false);
        conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        
        // Set connection properties
        if (config.containsKey("loginTimeout")) {
            // Note: setLoginTimeout is not available on all Connection implementations
            // This is a best-effort approach for DB2 connections
            try {
                // Use reflection to set login timeout if method exists
                java.lang.reflect.Method setLoginTimeout = conn.getClass().getMethod("setLoginTimeout", int.class);
                setLoginTimeout.invoke(conn, Integer.parseInt(config.getProperty("loginTimeout")));
            } catch (Exception e) {
                logger.debug("Could not set login timeout on connection: {}", e.getMessage());
            }
        }
        
        // Add to pool
        ConnectionInfo info = new ConnectionInfo();
        connectionPool.put(conn, info);
        activeConnections.incrementAndGet();
        totalConnections.incrementAndGet();
        
        logger.info("New connection created for {} (active: {}/{})", 
                   connectionType, activeConnections.get(), maxPoolSize);
        
        return conn;
    }
    
    /**
     * Wait for available connection in pool
     * @return Available connection
     * @throws SQLException if timeout occurs
     */
    private Connection waitForAvailableConnection() throws SQLException {
        long startTime = System.currentTimeMillis();
        
        while (System.currentTimeMillis() - startTime < connectionTimeout) {
            // Check for available connections
            for (Map.Entry<Connection, ConnectionInfo> entry : connectionPool.entrySet()) {
                Connection conn = entry.getKey();
                ConnectionInfo info = entry.getValue();
                
                if (info.isValid && !info.isExpired(idleTimeout)) {
                    if (connectionPool.remove(conn) != null) {
                        activeConnections.incrementAndGet();
                        info.markUsed();
                        connectionPool.put(conn, info);
                        logger.debug("Got connection after waiting for {}", connectionType);
                        return conn;
                    }
                }
            }
            
            // Clean up expired connections
            cleanupExpiredConnections();
            
            try {
                Thread.sleep(100); // Wait 100ms before retry
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new SQLException("Connection wait interrupted", ie);
            }
        }
        
        throw new SQLException("Connection timeout waiting for available connection in pool");
    }
    
    /**
     * Return connection to pool
     * @param conn Connection to return
     */
    public void returnConnection(Connection conn) {
        if (conn == null) return;
        
        try {
            // Rollback any uncommitted transactions
            if (!conn.getAutoCommit() && !conn.isClosed()) {
                conn.rollback();
            }
            
            ConnectionInfo info = connectionPool.get(conn);
            if (info != null) {
                info.markUsed();
                activeConnections.decrementAndGet();
                logger.debug("Connection returned to pool for {}", connectionType);
            }
            
        } catch (SQLException e) {
            logger.warn("Error returning connection to pool: {}", e.getMessage());
            // Remove invalid connection
            connectionPool.remove(conn);
            activeConnections.decrementAndGet();
        }
    }
    
    /**
     * Close connection and remove from pool
     * @param conn Connection to close
     */
    public void closeConnection(Connection conn) {
        if (conn == null) return;
        
        try {
            if (!conn.isClosed()) {
                conn.close();
            }
        } catch (SQLException e) {
            logger.warn("Error closing connection: {}", e.getMessage());
        } finally {
            connectionPool.remove(conn);
            activeConnections.decrementAndGet();
        }
    }
    
    /**
     * Validate connection health
     * @param conn Connection to validate
     * @return true if connection is valid
     */
    private boolean isValidConnection(Connection conn) {
        if (conn == null) return false;
        
        try {
            return !conn.isClosed() && conn.isValid(1);
        } catch (SQLException e) {
            logger.debug("Connection validation failed: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Clean up expired connections
     */
    private void cleanupExpiredConnections() {
        connectionPool.entrySet().removeIf(entry -> {
            Connection conn = entry.getKey();
            ConnectionInfo info = entry.getValue();
            
            if (info.isExpired(idleTimeout) || !info.isValid) {
                try {
                    if (!conn.isClosed()) {
                        conn.close();
                    }
                } catch (SQLException e) {
                    logger.debug("Error closing expired connection: {}", e.getMessage());
                }
                
                if (info.isValid) {
                    activeConnections.decrementAndGet();
                }
                
                return true;
            }
            return false;
        });
    }
    
    /**
     * Initialize connection pool with minimum connections
     */
    private void initializePool() {
        try {
            for (int i = 0; i < minPoolSize; i++) {
                Connection conn = createNewConnection();
                returnConnection(conn); // Return to pool
            }
            logger.info("Initialized connection pool with {} connections for {}", minPoolSize, connectionType);
        } catch (SQLException e) {
            logger.error("Failed to initialize connection pool for {}: {}", connectionType, e.getMessage());
        }
    }
    
    /**
     * Get connection pool statistics
     * @return Map containing pool statistics
     */
    public Map<String, Object> getPoolStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("connectionType", connectionType);
        stats.put("activeConnections", activeConnections.get());
        stats.put("totalConnections", totalConnections.get());
        stats.put("poolSize", connectionPool.size());
        stats.put("maxPoolSize", maxPoolSize);
        stats.put("minPoolSize", minPoolSize);
        stats.put("connectionTimeout", connectionTimeout);
        stats.put("idleTimeout", idleTimeout);
        
        // Calculate average connection time
        if (totalConnections.get() > 0) {
            stats.put("averageConnectionTime", totalConnectionTime.get() / totalConnections.get());
        }
        
        return stats;
    }
    
    /**
     * Health check for connection pool
     * @return true if pool is healthy
     */
    public boolean isHealthy() {
        try {
            // Try to get a test connection
            Connection testConn = getConnection();
            if (testConn != null) {
                returnConnection(testConn);
                return true;
            }
        } catch (SQLException e) {
            logger.warn("Health check failed for {}: {}", connectionType, e.getMessage());
        }
        return false;
    }
    
    /**
     * Shutdown connection pool
     */
    public void shutdown() {
        logger.info("Shutting down connection pool for {}", connectionType);
        
        poolLock.lock();
        try {
            // Close all connections
            for (Connection conn : connectionPool.keySet()) {
                try {
                    if (!conn.isClosed()) {
                        conn.close();
                    }
                } catch (SQLException e) {
                    logger.warn("Error closing connection during shutdown: {}", e.getMessage());
                }
            }
            
            connectionPool.clear();
            activeConnections.set(0);
            
        } finally {
            poolLock.unlock();
        }
        
        logger.info("Connection pool shutdown completed for {}", connectionType);
    }
    
    /**
     * Execute database operation with automatic connection management
     * @param operation Database operation to execute
     * @param <T> Return type of operation
     * @return Result of operation
     * @throws SQLException if operation fails
     */
    public <T> T executeWithConnection(DatabaseOperation<T> operation) throws SQLException {
        Connection conn = null;
        long startTime = System.currentTimeMillis();
        
        try {
            conn = getConnection();
            T result = operation.execute(conn);
            conn.commit();
            return result;
            
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    logger.warn("Rollback failed: {}", rollbackEx.getMessage());
                }
            }
            throw e;
            
        } finally {
            if (conn != null) {
                returnConnection(conn);
            }
            
            long duration = System.currentTimeMillis() - startTime;
            totalConnectionTime.addAndGet(duration);
        }
    }
    
    /**
     * Functional interface for database operations
     * @param <T> Return type
     */
    @FunctionalInterface
    public interface DatabaseOperation<T> {
        T execute(Connection conn) throws SQLException;
    }
}

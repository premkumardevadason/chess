package com.example.chess.DB2;

import java.util.Properties;
import java.util.Map;
import java.util.HashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Secure Configuration for DB2 Migration
 * 
 * This class provides secure configuration management for DB2 migration operations.
 * Credentials are loaded from environment variables or system properties to avoid
 * hardcoding sensitive information in source code.
 */
public class DB2MigrationConfig {
    
    private static final Logger logger = LogManager.getLogger(DB2MigrationConfig.class);
    
    // Environment variable names
    private static final String ONPREM_HOST_ENV = "DB2_ONPREM_HOST";
    private static final String ONPREM_PORT_ENV = "DB2_ONPREM_PORT";
    private static final String ONPREM_DATABASE_ENV = "DB2_ONPREM_DATABASE";
    private static final String ONPREM_USER_ENV = "DB2_ONPREM_USER";
    private static final String ONPREM_PASSWORD_ENV = "DB2_ONPREM_PASSWORD";
    
    private static final String RDS_HOST_ENV = "DB2_RDS_HOST";
    private static final String RDS_PORT_ENV = "DB2_RDS_PORT";
    private static final String RDS_DATABASE_ENV = "DB2_RDS_DATABASE";
    private static final String RDS_USER_ENV = "DB2_RDS_USER";
    private static final String RDS_PASSWORD_ENV = "DB2_RDS_PASSWORD";
    
    // Default values (can be overridden by environment variables)
    private static final String DEFAULT_DB2_PORT = "50000";
    private static final String DEFAULT_DB2_DRIVER = "com.ibm.db2.jcc.DB2Driver";
    
    // Performance and connection settings
    private static final String DEFAULT_BATCH_SIZE = "1000";
    private static final String DEFAULT_MAX_RETRIES = "3";
    private static final String DEFAULT_CONNECTION_TIMEOUT = "300";
    private static final String DEFAULT_SOCKET_TIMEOUT = "300";
    
    /**
     * Get secure on-premises DB2 configuration
     * @return Properties object with connection details
     * @throws SecurityException if required credentials are missing
     */
    public static Properties getOnPremConfig() throws SecurityException {
        Properties props = new Properties();
        
        // Load configuration from environment variables
        String host = getRequiredEnv(ONPREM_HOST_ENV, "On-premises DB2 host");
        String port = getEnvOrDefault(ONPREM_PORT_ENV, DEFAULT_DB2_PORT);
        String database = getRequiredEnv(ONPREM_DATABASE_ENV, "On-premises DB2 database");
        String user = getRequiredEnv(ONPREM_USER_ENV, "On-premises DB2 username");
        String password = getRequiredEnv(ONPREM_PASSWORD_ENV, "On-premises DB2 password");
        
        // Build JDBC URL
        String url = String.format("jdbc:db2://%s:%s/%s", host, port, database);
        
        // Set connection properties
        props.setProperty("url", url);
        props.setProperty("user", user);
        props.setProperty("password", password);
        props.setProperty("driver", DEFAULT_DB2_DRIVER);
        
        // Performance and connection settings
        props.setProperty("batchSize", getEnvOrDefault("DB2_ONPREM_BATCH_SIZE", DEFAULT_BATCH_SIZE));
        props.setProperty("maxRetries", getEnvOrDefault("DB2_ONPREM_MAX_RETRIES", DEFAULT_MAX_RETRIES));
        props.setProperty("loginTimeout", getEnvOrDefault("DB2_ONPREM_LOGIN_TIMEOUT", DEFAULT_CONNECTION_TIMEOUT));
        props.setProperty("socketTimeout", getEnvOrDefault("DB2_ONPREM_SOCKET_TIMEOUT", DEFAULT_SOCKET_TIMEOUT));
        
        // Security settings
        props.setProperty("sslConnection", getEnvOrDefault("DB2_ONPREM_SSL", "true"));
        props.setProperty("encryption", getEnvOrDefault("DB2_ONPREM_ENCRYPTION", "true"));
        
        logger.info("On-premises DB2 configuration loaded for host: {}", host);
        return props;
    }
    
    /**
     * Get secure AWS RDS DB2 configuration
     * @return Properties object with connection details
     * @throws SecurityException if required credentials are missing
     */
    public static Properties getRDSConfig() throws SecurityException {
        Properties props = new Properties();
        
        // Load configuration from environment variables
        String host = getRequiredEnv(RDS_HOST_ENV, "AWS RDS DB2 host");
        String port = getEnvOrDefault(RDS_PORT_ENV, DEFAULT_DB2_PORT);
        String database = getRequiredEnv(RDS_DATABASE_ENV, "AWS RDS DB2 database");
        String user = getRequiredEnv(RDS_USER_ENV, "AWS RDS DB2 username");
        String password = getRequiredEnv(RDS_PASSWORD_ENV, "AWS RDS DB2 password");
        
        // Build JDBC URL
        String url = String.format("jdbc:db2://%s:%s/%s", host, port, database);
        
        // Set connection properties
        props.setProperty("url", url);
        props.setProperty("user", user);
        props.setProperty("password", password);
        props.setProperty("driver", DEFAULT_DB2_DRIVER);
        
        // Performance and connection settings
        props.setProperty("batchSize", getEnvOrDefault("DB2_RDS_BATCH_SIZE", DEFAULT_BATCH_SIZE));
        props.setProperty("maxRetries", getEnvOrDefault("DB2_RDS_MAX_RETRIES", DEFAULT_MAX_RETRIES));
        props.setProperty("loginTimeout", getEnvOrDefault("DB2_RDS_LOGIN_TIMEOUT", DEFAULT_CONNECTION_TIMEOUT));
        props.setProperty("socketTimeout", getEnvOrDefault("DB2_RDS_SOCKET_TIMEOUT", DEFAULT_SOCKET_TIMEOUT));
        
        // Security settings
        props.setProperty("sslConnection", getEnvOrDefault("DB2_RDS_SSL", "true"));
        props.setProperty("encryption", getEnvOrDefault("DB2_RDS_ENCRYPTION", "true"));
        
        logger.info("AWS RDS DB2 configuration loaded for host: {}", host);
        return props;
    }
    
    /**
     * Get FileNet P8 table names in dependency order
     * @return Array of table names for migration
     */
    public static String[] getFileNetTables() {
        return new String[]{
            "FNCE_OBJECT_STORE",
            "FNCE_CLASS_DEFINITION", 
            "FNCE_PROPERTY_DEFINITION",
            "FNCE_DOCUMENT",
            "FNCE_FOLDER",
            "FNCE_CONTENT_ELEMENT",
            "FNCE_VERSION_SERIES",
            "FNCE_SECURITY_POLICY",
            "FNCE_ACCESS_PERMISSION",
            "FNCE_WORKFLOW_DEFINITION",
            "FNCE_WORKFLOW_ROSTER",
            "FNCE_WORK_ITEM",
            "FNCE_AUDIT_ENTRY",
            "FNCE_SUBSCRIPTION",
            "FNCE_EVENT_ACTION"
        };
    }
    
    /**
     * Get migration configuration settings
     * @return Map of configuration key-value pairs
     */
    public static Map<String, String> getMigrationConfig() {
        Map<String, String> config = new HashMap<>();
        
        // General migration settings
        config.put("batchSize", getEnvOrDefault("DB2_MIGRATION_BATCH_SIZE", DEFAULT_BATCH_SIZE));
        config.put("maxRetries", getEnvOrDefault("DB2_MIGRATION_MAX_RETRIES", DEFAULT_MAX_RETRIES));
        config.put("connectionTimeout", getEnvOrDefault("DB2_MIGRATION_CONNECTION_TIMEOUT", DEFAULT_CONNECTION_TIMEOUT));
        config.put("socketTimeout", getEnvOrDefault("DB2_MIGRATION_SOCKET_TIMEOUT", DEFAULT_SOCKET_TIMEOUT));
        
        // Performance settings
        config.put("parallelThreads", getEnvOrDefault("DB2_MIGRATION_PARALLEL_THREADS", "4"));
        config.put("memoryBufferSize", getEnvOrDefault("DB2_MIGRATION_MEMORY_BUFFER_SIZE", "64MB"));
        config.put("compressionLevel", getEnvOrDefault("DB2_MIGRATION_COMPRESSION_LEVEL", "6"));
        
        // Validation settings
        config.put("enableDataValidation", getEnvOrDefault("DB2_MIGRATION_ENABLE_VALIDATION", "true"));
        config.put("enableChecksumValidation", getEnvOrDefault("DB2_MIGRATION_ENABLE_CHECKSUM", "true"));
        config.put("enableRowCountValidation", getEnvOrDefault("DB2_MIGRATION_ENABLE_ROW_COUNT", "true"));
        
        return config;
    }
    
    /**
     * Validate that all required configuration is present
     * @throws SecurityException if any required configuration is missing
     */
    public static void validateConfiguration() throws SecurityException {
        try {
            getOnPremConfig();
            getRDSConfig();
            logger.info("DB2 migration configuration validation passed");
        } catch (SecurityException e) {
            logger.error("DB2 migration configuration validation failed: {}", e.getMessage());
            throw e;
        }
    }
    
    /**
     * Get required environment variable value
     * @param envName Environment variable name
     * @param description Description for error message
     * @return Environment variable value
     * @throws SecurityException if environment variable is not set
     */
    private static String getRequiredEnv(String envName, String description) throws SecurityException {
        String value = System.getenv(envName);
        if (value == null || value.trim().isEmpty()) {
            String errorMsg = String.format("Required environment variable '%s' (%s) is not set", envName, description);
            logger.error(errorMsg);
            throw new SecurityException(errorMsg);
        }
        return value.trim();
    }
    
    /**
     * Get environment variable value with default fallback
     * @param envName Environment variable name
     * @param defaultValue Default value if environment variable is not set
     * @return Environment variable value or default value
     */
    private static String getEnvOrDefault(String envName, String defaultValue) {
        String value = System.getenv(envName);
        return (value != null && !value.trim().isEmpty()) ? value.trim() : defaultValue;
    }
    
    /**
     * Mask sensitive information in configuration for logging
     * @param props Properties object containing configuration
     * @return Properties object with masked sensitive information
     */
    public static Properties getMaskedConfig(Properties props) {
        Properties masked = new Properties();
        for (String key : props.stringPropertyNames()) {
            if (key.toLowerCase().contains("password")) {
                masked.setProperty(key, "***MASKED***");
            } else {
                masked.setProperty(key, props.getProperty(key));
            }
        }
        return masked;
    }
}
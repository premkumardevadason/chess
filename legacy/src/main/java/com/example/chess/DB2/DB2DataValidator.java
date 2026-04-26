package com.example.chess.DB2;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.security.MessageDigest;
import java.nio.file.Path;
import java.nio.file.Files;
import java.io.IOException;
import java.util.Base64;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Comprehensive Data Validation for DB2 Migration
 * 
 * This class provides robust data validation capabilities for DB2 migration operations,
 * including data integrity checks, checksum validation, and business rule verification.
 */
public class DB2DataValidator {
    
    private static final Logger logger = LogManager.getLogger(DB2DataValidator.class);
    
    // Validation result constants
    public static final String VALIDATION_PASSED = "PASSED";
    public static final String VALIDATION_FAILED = "FAILED";
    public static final String VALIDATION_WARNING = "WARNING";
    
    /**
     * Validation result for a table
     */
    public static class ValidationResult {
        private final String tableName;
        private final String status;
        private final long sourceRecordCount;
        private final long targetRecordCount;
        private final String checksumSource;
        private final String checksumTarget;
        private final List<String> errors;
        private final List<String> warnings;
        private final long validationTime;
        
        public ValidationResult(String tableName, String status, long sourceRecordCount, 
                              long targetRecordCount, String checksumSource, String checksumTarget) {
            this.tableName = tableName;
            this.status = status;
            this.sourceRecordCount = sourceRecordCount;
            this.targetRecordCount = targetRecordCount;
            this.checksumSource = checksumSource;
            this.checksumTarget = checksumTarget;
            this.errors = new ArrayList<>();
            this.warnings = new ArrayList<>();
            this.validationTime = System.currentTimeMillis();
        }
        
        // Getters
        public String getTableName() { return tableName; }
        public String getStatus() { return status; }
        public long getSourceRecordCount() { return sourceRecordCount; }
        public long getTargetRecordCount() { return sourceRecordCount; }
        public String getChecksumSource() { return checksumSource; }
        public String getChecksumTarget() { return checksumTarget; }
        public List<String> getErrors() { return errors; }
        public List<String> getWarnings() { return warnings; }
        public long getValidationTime() { return validationTime; }
        
        // Add validation issues
        public void addError(String error) { errors.add(error); }
        public void addWarning(String warning) { warnings.add(warning); }
        
        // Check if validation passed
        public boolean isValid() { return VALIDATION_PASSED.equals(status); }
        public boolean hasErrors() { return !errors.isEmpty(); }
        public boolean hasWarnings() { return !warnings.isEmpty(); }
    }
    
    /**
     * Validate table data integrity between source and target
     * @param sourceConn Source database connection
     * @param targetConn Target database connection
     * @param tableName Table name to validate
     * @return ValidationResult with detailed validation information
     */
    public static ValidationResult validateTableIntegrity(Connection sourceConn, Connection targetConn, String tableName) {
        logger.info("Starting validation for table: {}", tableName);
        
        try {
            // Get record counts
            long sourceCount = getRecordCount(sourceConn, tableName);
            long targetCount = getRecordCount(targetConn, tableName);
            
            // Get table checksums
            String sourceChecksum = calculateTableChecksum(sourceConn, tableName);
            String targetChecksum = calculateTableChecksum(targetConn, tableName);
            
            // Determine validation status
            String status = determineValidationStatus(sourceCount, targetCount, sourceChecksum, targetChecksum);
            
            ValidationResult result = new ValidationResult(tableName, status, sourceCount, targetCount, 
                                                        sourceChecksum, targetChecksum);
            
            // Perform additional validations
            performDataIntegrityChecks(sourceConn, targetConn, tableName, result);
            
            logger.info("Validation completed for table {}: {}", tableName, status);
            return result;
            
        } catch (SQLException e) {
            logger.error("Validation failed for table {}: {}", tableName, e.getMessage());
            ValidationResult result = new ValidationResult(tableName, VALIDATION_FAILED, 0, 0, "", "");
            result.addError("SQL Exception during validation: " + e.getMessage());
            return result;
        }
    }
    
    /**
     * Validate file checksums for exported data
     * @param exportPath Path to exported data directory
     * @param tableName Table name to validate
     * @return true if checksum validation passes
     */
    public static boolean validateFileChecksum(Path exportPath, String tableName) {
        try {
            Path tableFile = exportPath.resolve(tableName + ".csv.gz");
            Path metaFile = exportPath.resolve(tableName + ".meta");
            
            if (!Files.exists(tableFile) || !Files.exists(metaFile)) {
                logger.warn("Files for table {} not found during checksum validation", tableName);
                return false;
            }
            
            // Calculate cryptographic checksum
            String calculatedChecksum = calculateFileChecksum(tableFile);
            
            // Store checksum for later comparison
            Path checksumFile = exportPath.resolve(tableName + ".checksum");
            Files.write(checksumFile, calculatedChecksum.getBytes());
            
            logger.info("Checksum validation completed for table {}: {}", tableName, calculatedChecksum);
            return true;
            
        } catch (IOException e) {
            logger.error("Checksum validation failed for table {}: {}", tableName, e.getMessage());
            return false;
        }
    }
    
    /**
     * Perform comprehensive data integrity checks
     * @param sourceConn Source database connection
     * @param targetConn Target database connection
     * @param tableName Table name to validate
     * @param result ValidationResult to populate with findings
     */
    private static void performDataIntegrityChecks(Connection sourceConn, Connection targetConn, 
                                                  String tableName, ValidationResult result) {
        try {
            // Check for referential integrity violations
            checkReferentialIntegrity(sourceConn, targetConn, tableName, result);
            
            // Check for data type consistency
            checkDataTypeConsistency(sourceConn, targetConn, tableName, result);
            
            // Check for business rule violations
            checkBusinessRules(sourceConn, targetConn, tableName, result);
            
            // Check for data corruption
            checkDataCorruption(sourceConn, targetConn, tableName, result);
            
        } catch (SQLException e) {
            result.addError("Data integrity check failed: " + e.getMessage());
            logger.error("Data integrity check failed for table {}: {}", tableName, e.getMessage());
        }
    }
    
    /**
     * Check referential integrity between tables
     * @param sourceConn Source database connection
     * @param targetConn Target database connection
     * @param tableName Table name to validate
     * @param result ValidationResult to populate with findings
     */
    private static void checkReferentialIntegrity(Connection sourceConn, Connection targetConn, 
                                                 String tableName, ValidationResult result) throws SQLException {
        // Get foreign key constraints for the table
        String fkQuery = "SELECT FK_NAME, FKCOLNAMES, PK_NAME, PKCOLNAMES FROM SYSCAT.REFERENCES " +
                         "WHERE TABNAME = ? AND TABSCHEMA = CURRENT SCHEMA";
        
        try (PreparedStatement stmt = sourceConn.prepareStatement(fkQuery)) {
            stmt.setString(1, tableName.toUpperCase());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String fkName = rs.getString("FK_NAME");
                    String fkColumns = rs.getString("FKCOLNAMES");
                    String pkName = rs.getString("PK_NAME");
                    String pkColumns = rs.getString("PKCOLNAMES");
                    
                    // Validate foreign key relationships
                    validateForeignKeyRelationship(sourceConn, targetConn, tableName, 
                                                fkColumns, pkName, pkColumns, result);
                }
            }
        }
    }
    
    /**
     * Validate foreign key relationship
     * @param sourceConn Source database connection
     * @param targetConn Target database connection
     * @param tableName Table name
     * @param fkColumns Foreign key columns
     * @param pkName Primary key table name
     * @param pkColumns Primary key columns
     * @param result ValidationResult to populate with findings
     */
    private static void validateForeignKeyRelationship(Connection sourceConn, Connection targetConn,
                                                     String tableName, String fkColumns, 
                                                     String pkName, String pkColumns, ValidationResult result) {
        try {
            // Check for orphaned records in source
            String orphanQuery = String.format(
                "SELECT COUNT(*) FROM %s s LEFT JOIN %s p ON s.%s = p.%s WHERE p.%s IS NULL",
                tableName, pkName, fkColumns, pkColumns, pkColumns
            );
            
            long orphanCount = getCountFromQuery(sourceConn, orphanQuery);
            if (orphanCount > 0) {
                result.addWarning(String.format("Found %d orphaned records in source table %s", orphanCount, tableName));
            }
            
            // Check for orphaned records in target
            orphanCount = getCountFromQuery(targetConn, orphanQuery);
            if (orphanCount > 0) {
                result.addError(String.format("Found %d orphaned records in target table %s", orphanCount, tableName));
            }
            
        } catch (SQLException e) {
            result.addError("Foreign key validation failed: " + e.getMessage());
        }
    }
    
    /**
     * Check data type consistency between source and target
     * @param sourceConn Source database connection
     * @param targetConn Target database connection
     * @param tableName Table name to validate
     * @param result ValidationResult to populate with findings
     */
    private static void checkDataTypeConsistency(Connection sourceConn, Connection targetConn,
                                                String tableName, ValidationResult result) throws SQLException {
        String metadataQuery = "SELECT COLNAME, TYPENAME, LENGTH, SCALE FROM SYSCAT.COLUMNS " +
                              "WHERE TABNAME = ? AND TABSCHEMA = CURRENT SCHEMA ORDER BY COLNO";
        
        Map<String, String> sourceMetadata = new HashMap<>();
        Map<String, String> targetMetadata = new HashMap<>();
        
        // Get source metadata
        try (PreparedStatement stmt = sourceConn.prepareStatement(metadataQuery)) {
            stmt.setString(1, tableName.toUpperCase());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String colName = rs.getString("COLNAME");
                    String typeInfo = String.format("%s(%d,%d)", 
                        rs.getString("TYPENAME"), 
                        rs.getInt("LENGTH"), 
                        rs.getInt("SCALE"));
                    sourceMetadata.put(colName, typeInfo);
                }
            }
        }
        
        // Get target metadata
        try (PreparedStatement stmt = targetConn.prepareStatement(metadataQuery)) {
            stmt.setString(1, tableName.toUpperCase());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String colName = rs.getString("COLNAME");
                    String typeInfo = String.format("%s(%d,%d)", 
                        rs.getString("TYPENAME"), 
                        rs.getInt("LENGTH"), 
                        rs.getInt("SCALE"));
                    targetMetadata.put(colName, typeInfo);
                }
            }
        }
        
        // Compare metadata
        for (String colName : sourceMetadata.keySet()) {
            if (!targetMetadata.containsKey(colName)) {
                result.addError(String.format("Column %s missing in target table", colName));
            } else if (!sourceMetadata.get(colName).equals(targetMetadata.get(colName))) {
                result.addWarning(String.format("Column %s type mismatch: source=%s, target=%s", 
                    colName, sourceMetadata.get(colName), targetMetadata.get(colName)));
            }
        }
    }
    
    /**
     * Check business rules specific to FileNet P8
     * @param sourceConn Source database connection
     * @param targetConn Target database connection
     * @param tableName Table name to validate
     * @param result ValidationResult to populate with findings
     */
    private static void checkBusinessRules(Connection sourceConn, Connection targetConn,
                                          String tableName, ValidationResult result) {
        // FileNet P8 specific business rules
        switch (tableName) {
            case "FNCE_DOCUMENT":
                checkDocumentBusinessRules(sourceConn, targetConn, result);
                break;
            case "FNCE_FOLDER":
                checkFolderBusinessRules(sourceConn, targetConn, result);
                break;
            case "FNCE_OBJECT_STORE":
                checkObjectStoreBusinessRules(sourceConn, targetConn, result);
                break;
            default:
                // No specific business rules for this table
                break;
        }
    }
    
    /**
     * Check FileNet document business rules
     * @param sourceConn Source database connection
     * @param targetConn Target database connection
     * @param result ValidationResult to populate with findings
     */
    private static void checkDocumentBusinessRules(Connection sourceConn, Connection targetConn, 
                                                  ValidationResult result) {
        try {
            // Check for documents without content elements
            String orphanQuery = "SELECT COUNT(*) FROM FNCE_DOCUMENT d " +
                                "LEFT JOIN FNCE_CONTENT_ELEMENT ce ON d.ID = ce.DOCUMENT_ID " +
                                "WHERE ce.ID IS NULL";
            
            long orphanCount = getCountFromQuery(sourceConn, orphanQuery);
            if (orphanCount > 0) {
                result.addWarning(String.format("Found %d documents without content elements in source", orphanCount));
            }
            
            orphanCount = getCountFromQuery(targetConn, orphanQuery);
            if (orphanCount > 0) {
                result.addError(String.format("Found %d documents without content elements in target", orphanCount));
            }
            
        } catch (SQLException e) {
            result.addError("Document business rule validation failed: " + e.getMessage());
        }
    }
    
    /**
     * Check FileNet folder business rules
     * @param sourceConn Source database connection
     * @param targetConn Target database connection
     * @param result ValidationResult to populate with findings
     */
    private static void checkFolderBusinessRules(Connection sourceConn, Connection targetConn, 
                                                ValidationResult result) {
        try {
            // Check for circular folder references
            String circularQuery = "WITH RECURSIVE folder_tree AS (" +
                                  "  SELECT ID, PARENT_ID, 1 as level FROM FNCE_FOLDER WHERE PARENT_ID IS NULL " +
                                  "  UNION ALL " +
                                  "  SELECT f.ID, f.PARENT_ID, ft.level + 1 FROM FNCE_FOLDER f " +
                                  "  JOIN folder_tree ft ON f.PARENT_ID = ft.ID " +
                                  "  WHERE ft.level < 100 " +
                                  ") SELECT COUNT(*) FROM folder_tree WHERE level >= 100";
            
            long circularCount = getCountFromQuery(sourceConn, circularQuery);
            if (circularCount > 0) {
                result.addWarning("Potential circular folder references detected in source");
            }
            
            circularCount = getCountFromQuery(targetConn, circularQuery);
            if (circularCount > 0) {
                result.addError("Potential circular folder references detected in target");
            }
            
        } catch (SQLException e) {
            result.addError("Folder business rule validation failed: " + e.getMessage());
        }
    }
    
    /**
     * Check FileNet object store business rules
     * @param sourceConn Source database connection
     * @param targetConn Target database connection
     * @param result ValidationResult to populate with findings
     */
    private static void checkObjectStoreBusinessRules(Connection sourceConn, Connection targetConn, 
                                                     ValidationResult result) {
        try {
            // Check for object stores without proper security policies
            String securityQuery = "SELECT COUNT(*) FROM FNCE_OBJECT_STORE os " +
                                  "LEFT JOIN FNCE_SECURITY_POLICY sp ON os.SECURITY_POLICY_ID = sp.ID " +
                                  "WHERE sp.ID IS NULL";
            
            long unsecuredCount = getCountFromQuery(sourceConn, securityQuery);
            if (unsecuredCount > 0) {
                result.addWarning(String.format("Found %d object stores without security policies in source", unsecuredCount));
            }
            
            unsecuredCount = getCountFromQuery(targetConn, securityQuery);
            if (unsecuredCount > 0) {
                result.addError(String.format("Found %d object stores without security policies in target", unsecuredCount));
            }
            
        } catch (SQLException e) {
            result.addError("Object store business rule validation failed: " + e.getMessage());
        }
    }
    
    /**
     * Check for data corruption
     * @param sourceConn Source database connection
     * @param targetConn Target database connection
     * @param tableName Table name to validate
     * @param result ValidationResult to populate with findings
     */
    private static void checkDataCorruption(Connection sourceConn, Connection targetConn,
                                           String tableName, ValidationResult result) {
        try {
            // Check for NULL values in required fields
            String nullQuery = "SELECT COUNT(*) FROM " + tableName + " WHERE ";
            String[] requiredColumns = getRequiredColumns(tableName);
            
            if (requiredColumns.length > 0) {
                StringBuilder nullConditions = new StringBuilder();
                for (int i = 0; i < requiredColumns.length; i++) {
                    if (i > 0) nullConditions.append(" OR ");
                    nullConditions.append(requiredColumns[i]).append(" IS NULL");
                }
                
                long nullCount = getCountFromQuery(sourceConn, nullQuery + nullConditions.toString());
                if (nullCount > 0) {
                    result.addWarning(String.format("Found %d records with NULL values in required fields in source", nullCount));
                }
                
                nullCount = getCountFromQuery(targetConn, nullQuery + nullConditions.toString());
                if (nullCount > 0) {
                    result.addError(String.format("Found %d records with NULL values in required fields in target", nullCount));
                }
            }
            
        } catch (SQLException e) {
            result.addError("Data corruption check failed: " + e.getMessage());
        }
    }
    
    /**
     * Get required columns for a table based on FileNet P8 schema
     * @param tableName Table name
     * @return Array of required column names
     */
    private static String[] getRequiredColumns(String tableName) {
        switch (tableName) {
            case "FNCE_DOCUMENT":
                return new String[]{"ID", "NAME", "OBJECT_STORE_ID"};
            case "FNCE_FOLDER":
                return new String[]{"ID", "NAME", "OBJECT_STORE_ID"};
            case "FNCE_OBJECT_STORE":
                return new String[]{"ID", "NAME"};
            case "FNCE_CONTENT_ELEMENT":
                return new String[]{"ID", "DOCUMENT_ID", "CONTENT"};
            default:
                return new String[]{"ID"}; // Most tables have ID as required
        }
    }
    
    /**
     * Calculate cryptographic checksum for a file
     * @param filePath Path to the file
     * @return SHA-256 checksum as Base64 string
     */
    public static String calculateFileChecksum(Path filePath) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(Files.readAllBytes(filePath));
            return Base64.getEncoder().encodeToString(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            logger.error("SHA-256 algorithm not available: {}", e.getMessage());
            return "CHECKSUM_ERROR";
        } catch (IOException e) {
            logger.error("Failed to calculate file checksum for {}: {}", filePath, e.getMessage());
            return "CHECKSUM_ERROR";
        }
    }
    
    /**
     * Calculate table checksum for data integrity validation
     * @param conn Database connection
     * @param tableName Table name
     * @return SHA-256 checksum as Base64 string
     */
    public static String calculateTableChecksum(Connection conn, String tableName) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String query = "SELECT * FROM " + tableName + " ORDER BY ID";
            
            try (PreparedStatement stmt = conn.prepareStatement(query);
                 ResultSet rs = stmt.executeQuery()) {
                
                while (rs.next()) {
                    StringBuilder rowData = new StringBuilder();
                    for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
                        Object value = rs.getObject(i);
                        rowData.append(value != null ? value.toString() : "NULL");
                        rowData.append("|");
                    }
                    digest.update(rowData.toString().getBytes());
                }
            }
            
            byte[] hash = digest.digest();
            return Base64.getEncoder().encodeToString(hash);
            
        } catch (java.security.NoSuchAlgorithmException e) {
            logger.error("SHA-256 algorithm not available: {}", e.getMessage());
            return "CHECKSUM_ERROR";
        } catch (SQLException e) {
            logger.error("Failed to calculate table checksum for {}: {}", tableName, e.getMessage());
            return "CHECKSUM_ERROR";
        }
    }
    
    /**
     * Get record count for a table
     * @param conn Database connection
     * @param tableName Table name
     * @return Record count
     * @throws SQLException if query fails
     */
    private static long getRecordCount(Connection conn, String tableName) throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + tableName;
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }
    
    /**
     * Execute a count query and return the result
     * @param conn Database connection
     * @param query SQL query to execute
     * @return Count result
     * @throws SQLException if query fails
     */
    private static long getCountFromQuery(Connection conn, String query) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }
    
    /**
     * Determine validation status based on counts and checksums
     * @param sourceCount Source record count
     * @param targetCount Target record count
     * @param sourceChecksum Source checksum
     * @param targetChecksum Target checksum
     * @return Validation status
     */
    private static String determineValidationStatus(long sourceCount, long targetCount, 
                                                   String sourceChecksum, String targetChecksum) {
        if (sourceCount != targetCount) {
            return VALIDATION_FAILED;
        }
        
        if (!sourceChecksum.equals(targetChecksum)) {
            return VALIDATION_FAILED;
        }
        
        if (sourceChecksum.equals("CHECKSUM_ERROR") || targetChecksum.equals("CHECKSUM_ERROR")) {
            return VALIDATION_WARNING;
        }
        
        return VALIDATION_PASSED;
    }
}

package com.example.chess.DB2;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.nio.file.Files;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Unit tests for DB2DataValidator class
 */
public class DB2DataValidatorTest {
    
    @TempDir
    Path tempDir;
    
    private Path testFile;
    private String testContent;
    
    @BeforeEach
    void setUp() throws IOException {
        testContent = "This is test content for checksum validation\nLine 2\nLine 3";
        testFile = tempDir.resolve("test.txt");
        Files.write(testFile, testContent.getBytes());
    }
    
    @Test
    void testCalculateFileChecksum() throws IOException {
        // Calculate expected checksum manually
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] expectedHash = digest.digest(testContent.getBytes());
            String expectedChecksum = Base64.getEncoder().encodeToString(expectedHash);
            
            // Calculate checksum using validator
            String actualChecksum = DB2DataValidator.calculateFileChecksum(testFile);
            
            // Verify checksum matches
            assertEquals(expectedChecksum, actualChecksum, "File checksum should match expected value");
        } catch (java.security.NoSuchAlgorithmException e) {
            fail("SHA-256 algorithm not available: " + e.getMessage());
        }
    }
    
    @Test
    void testCalculateFileChecksumNonExistentFile() {
        Path nonExistentFile = tempDir.resolve("nonexistent.txt");
        String checksum = DB2DataValidator.calculateFileChecksum(nonExistentFile);
        
        assertEquals("CHECKSUM_ERROR", checksum, "Non-existent file should return CHECKSUM_ERROR");
    }
    
    @Test
    void testValidationResultCreation() {
        String tableName = "TEST_TABLE";
        String status = DB2DataValidator.VALIDATION_PASSED;
        long sourceCount = 100;
        long targetCount = 100;
        String sourceChecksum = "abc123";
        String targetChecksum = "abc123";
        
        DB2DataValidator.ValidationResult result = new DB2DataValidator.ValidationResult(
            tableName, status, sourceCount, targetCount, sourceChecksum, targetChecksum
        );
        
        // Verify all fields are set correctly
        assertEquals(tableName, result.getTableName());
        assertEquals(status, result.getStatus());
        assertEquals(sourceCount, result.getSourceRecordCount());
        assertEquals(targetCount, result.getTargetRecordCount());
        assertEquals(sourceChecksum, result.getChecksumSource());
        assertEquals(targetChecksum, result.getChecksumTarget());
        assertTrue(result.isValid());
        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
    }
    
    @Test
    void testValidationResultWithErrors() {
        DB2DataValidator.ValidationResult result = new DB2DataValidator.ValidationResult(
            "TEST_TABLE", DB2DataValidator.VALIDATION_FAILED, 100, 95, "abc123", "def456"
        );
        
        // Add some errors and warnings
        result.addError("Connection timeout");
        result.addError("Data type mismatch");
        result.addWarning("Performance issue");
        
        // Verify error and warning handling
        assertFalse(result.isValid());
        assertTrue(result.hasErrors());
        assertTrue(result.hasWarnings());
        assertEquals(2, result.getErrors().size());
        assertEquals(1, result.getWarnings().size());
        
        assertTrue(result.getErrors().contains("Connection timeout"));
        assertTrue(result.getErrors().contains("Data type mismatch"));
        assertTrue(result.getWarnings().contains("Performance issue"));
    }
    
    @Test
    void testValidationConstants() {
        // Verify validation constants are defined correctly
        assertEquals("PASSED", DB2DataValidator.VALIDATION_PASSED);
        assertEquals("FAILED", DB2DataValidator.VALIDATION_FAILED);
        assertEquals("WARNING", DB2DataValidator.VALIDATION_WARNING);
    }
    
    @Test
    void testValidationResultTimestamp() {
        long beforeCreation = System.currentTimeMillis();
        
        DB2DataValidator.ValidationResult result = new DB2DataValidator.ValidationResult(
            "TEST_TABLE", DB2DataValidator.VALIDATION_PASSED, 100, 100, "abc123", "abc123"
        );
        
        long afterCreation = System.currentTimeMillis();
        
        // Verify timestamp is within expected range
        assertTrue(result.getValidationTime() >= beforeCreation);
        assertTrue(result.getValidationTime() <= afterCreation);
    }
    
    @Test
    void testValidationResultErrorHandling() {
        DB2DataValidator.ValidationResult result = new DB2DataValidator.ValidationResult(
            "TEST_TABLE", DB2DataValidator.VALIDATION_PASSED, 100, 100, "abc123", "abc123"
        );
        
        // Test adding multiple errors
        result.addError("Error 1");
        result.addError("Error 2");
        result.addError("Error 3");
        
        assertEquals(3, result.getErrors().size());
        assertTrue(result.hasErrors());
        
        // Test adding multiple warnings
        result.addWarning("Warning 1");
        result.addWarning("Warning 2");
        
        assertEquals(2, result.getWarnings().size());
        assertTrue(result.hasWarnings());
    }
    
    @Test
    void testValidationResultStatusValidation() {
        // Test PASSED status
        DB2DataValidator.ValidationResult passedResult = new DB2DataValidator.ValidationResult(
            "TABLE1", DB2DataValidator.VALIDATION_PASSED, 100, 100, "abc123", "abc123"
        );
        assertTrue(passedResult.isValid());
        
        // Test FAILED status
        DB2DataValidator.ValidationResult failedResult = new DB2DataValidator.ValidationResult(
            "TABLE2", DB2DataValidator.VALIDATION_FAILED, 100, 95, "abc123", "def456"
        );
        assertFalse(failedResult.isValid());
        
        // Test WARNING status
        DB2DataValidator.ValidationResult warningResult = new DB2DataValidator.ValidationResult(
            "TABLE3", DB2DataValidator.VALIDATION_WARNING, 100, 100, "abc123", "abc123"
        );
        assertFalse(warningResult.isValid());
    }
}

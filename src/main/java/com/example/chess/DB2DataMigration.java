package com.example.chess;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * DB2 Air-Gapped Data Migration Utility for IBM FileNet P8
 * Step 1: Export from on-prem DB2 10.5 to files
 * Step 2: Transfer files to AWS environment
 * Step 3: Import files to AWS RDS DB2 11.5
 */
public class DB2DataMigration {
    
    private static final Logger logger = LogManager.getLogger(DB2DataMigration.class);
    
    // Connection configurations
    private static final String ONPREM_URL = "jdbc:db2://onprem-server:50000/FNDB";
    private static final String ONPREM_USER = "db2admin";
    private static final String ONPREM_PASSWORD = "password";
    
    private static final String RDS_URL = "jdbc:db2://rds-endpoint:50000/FNDB";
    private static final String RDS_USER = "db2admin";
    private static final String RDS_PASSWORD = "password";
    
    private static final int BATCH_SIZE = 1000;
    private static final int MAX_RETRIES = 3;
    
    private Connection onPremConn;
    private Connection rdsConn;
    private AtomicLong totalRecords = new AtomicLong(0);
    private AtomicLong migratedRecords = new AtomicLong(0);
    private List<TableStats> tableStatsList = new ArrayList<>();
    
    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            return;
        }
        
        DB2DataMigration migration = new DB2DataMigration();
        try {
            switch (args[0].toLowerCase()) {
                case "export" -> migration.exportData(args.length > 1 ? args[1] : "./export");
                case "import" -> migration.importData(args.length > 1 ? args[1] : "./export");
                default -> printUsage();
            }
        } catch (Exception e) {
            logger.error("Migration failed: {}", e.getMessage(), e);
        }
    }
    
    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("  java DB2DataMigration export [export_directory]");
        System.out.println("  java DB2DataMigration import [export_directory]");
    }
    
    public void exportData(String exportDir) throws Exception {
        logger.info("*** Starting DB2 FileNet P8 Data Export ***");
        
        Path exportPath = Paths.get(exportDir);
        Files.createDirectories(exportPath);
        
        try {
            initializeOnPremConnection();
            
            String[] tables = getFileNetTables();
            
            for (String table : tables) {
                exportTable(table, exportPath);
            }
            
            // Create manifest file
            createManifest(exportPath, tables);
            
            // Generate export report
            generateExportReport(exportPath);
            
            logger.info("*** Export completed: {} records exported ***", totalRecords.get());
            
        } finally {
            closeOnPremConnection();
        }
    }
    
    public void importData(String exportDir) throws Exception {
        logger.info("*** Starting DB2 FileNet P8 Data Import ***");
        
        Path exportPath = Paths.get(exportDir);
        if (!Files.exists(exportPath)) {
            throw new IllegalArgumentException("Export directory does not exist: " + exportDir);
        }
        
        try {
            initializeRDSConnection();
            
            // Read manifest
            String[] tables = readManifest(exportPath);
            
            for (String table : tables) {
                importTable(table, exportPath);
            }
            
            // Generate import report
            generateImportReport(exportPath);
            
            logger.info("*** Import completed: {} records imported ***", migratedRecords.get());
            
        } finally {
            closeRDSConnection();
        }
    }
    
    private void initializeOnPremConnection() throws SQLException {
        logger.info("Connecting to on-prem DB2 10.5...");
        onPremConn = DriverManager.getConnection(ONPREM_URL, ONPREM_USER, ONPREM_PASSWORD);
        onPremConn.setAutoCommit(false);
        logger.info("On-prem connection established");
    }
    
    private void initializeRDSConnection() throws SQLException {
        logger.info("Connecting to AWS RDS DB2 11.5...");
        rdsConn = DriverManager.getConnection(RDS_URL, RDS_USER, RDS_PASSWORD);
        rdsConn.setAutoCommit(false);
        logger.info("RDS connection established");
    }
    
    private String[] getFileNetTables() {
        // FileNet P8 core tables in dependency order
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
    
    private void exportTable(String tableName, Path exportPath) throws Exception {
        logger.info("Exporting table: {}", tableName);
        
        if (!tableExists(onPremConn, tableName)) {
            logger.warn("Table {} not found, skipping", tableName);
            return;
        }
        
        TableMetadata metadata = getTableMetadata(onPremConn, tableName);
        long tableRecords = getRecordCount(onPremConn, tableName);
        
        if (tableRecords == 0) {
            logger.info("Table {} is empty, skipping", tableName);
            return;
        }
        
        // Export to compressed CSV file
        Path tableFile = exportPath.resolve(tableName + ".csv.gz");
        Path metaFile = exportPath.resolve(tableName + ".meta");
        
        exportTableData(tableName, metadata, tableFile);
        exportTableMetadata(metadata, metaFile);
        
        totalRecords.addAndGet(tableRecords);
        
        // Store table stats for report
        storeTableStats(tableName, tableRecords, "EXPORTED");
        
        logger.info("Table {} exported: {} records", tableName, tableRecords);
    }
    
    private void importTable(String tableName, Path exportPath) throws Exception {
        logger.info("Importing table: {}", tableName);
        
        Path tableFile = exportPath.resolve(tableName + ".csv.gz");
        Path metaFile = exportPath.resolve(tableName + ".meta");
        
        if (!Files.exists(tableFile) || !Files.exists(metaFile)) {
            logger.warn("Files for table {} not found, skipping", tableName);
            return;
        }
        
        TableMetadata metadata = importTableMetadata(metaFile);
        long imported = importTableData(tableName, metadata, tableFile);
        
        migratedRecords.addAndGet(imported);
        
        // Store table stats for report
        storeTableStats(tableName, imported, "IMPORTED");
        
        logger.info("Table {} imported: {} records", tableName, imported);
    }
    
    private void exportTableData(String tableName, TableMetadata metadata, Path tableFile) throws Exception {
        String selectSql = "SELECT * FROM " + tableName;
        
        try (PreparedStatement stmt = onPremConn.prepareStatement(selectSql);
             ResultSet rs = stmt.executeQuery();
             FileOutputStream fos = new FileOutputStream(tableFile.toFile());
             GZIPOutputStream gzos = new GZIPOutputStream(fos);
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(gzos, "UTF-8"))) {
            
            while (rs.next()) {
                StringBuilder line = new StringBuilder();
                
                for (int i = 1; i <= metadata.columnCount; i++) {
                    if (i > 1) line.append("|");
                    
                    Object value = rs.getObject(i);
                    if (value != null) {
                        String strValue = value.toString().replace("|", "\\|").replace("\n", "\\n");
                        line.append(strValue);
                    }
                }
                
                writer.println(line.toString());
            }
        }
    }
    
    private long importTableData(String tableName, TableMetadata metadata, Path tableFile) throws Exception {
        String insertSql = buildInsertSql(tableName, metadata);
        long imported = 0;
        
        try (FileInputStream fis = new FileInputStream(tableFile.toFile());
             GZIPInputStream gzis = new GZIPInputStream(fis);
             BufferedReader reader = new BufferedReader(new InputStreamReader(gzis, "UTF-8"));
             PreparedStatement stmt = rdsConn.prepareStatement(insertSql)) {
            
            String line;
            int batchCount = 0;
            
            while ((line = reader.readLine()) != null) {
                String[] values = line.split("\\|", -1);
                
                for (int i = 0; i < metadata.columnCount; i++) {
                    String value = i < values.length ? values[i] : "";
                    
                    if (value.isEmpty()) {
                        stmt.setNull(i + 1, metadata.columnTypes[i]);
                    } else {
                        value = value.replace("\\|", "|").replace("\\n", "\n");
                        stmt.setString(i + 1, value);
                    }
                }
                
                stmt.addBatch();
                batchCount++;
                imported++;
                
                if (batchCount >= BATCH_SIZE) {
                    stmt.executeBatch();
                    rdsConn.commit();
                    batchCount = 0;
                }
            }
            
            if (batchCount > 0) {
                stmt.executeBatch();
                rdsConn.commit();
            }
        }
        
        return imported;
    }
    
    private void exportTableMetadata(TableMetadata metadata, Path metaFile) throws IOException {
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(metaFile))) {
            writer.println(metadata.columnCount);
            
            for (int i = 0; i < metadata.columnCount; i++) {
                writer.println(metadata.columnNames[i] + "|" + metadata.columnTypes[i]);
            }
        }
    }
    
    private TableMetadata importTableMetadata(Path metaFile) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(metaFile)) {
            int columnCount = Integer.parseInt(reader.readLine());
            
            String[] columnNames = new String[columnCount];
            int[] columnTypes = new int[columnCount];
            
            for (int i = 0; i < columnCount; i++) {
                String[] parts = reader.readLine().split("\\|", 2);
                columnNames[i] = parts[0];
                columnTypes[i] = Integer.parseInt(parts[1]);
            }
            
            return new TableMetadata(columnNames, columnTypes, columnCount);
        }
    }
    
    private void createManifest(Path exportPath, String[] tables) throws IOException {
        Path manifestFile = exportPath.resolve("manifest.txt");
        
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(manifestFile))) {
            writer.println("# DB2 FileNet P8 Export Manifest");
            writer.println("# Generated: " + new Date());
            writer.println("# Total Records: " + totalRecords.get());
            writer.println();
            
            for (String table : tables) {
                Path tableFile = exportPath.resolve(table + ".csv.gz");
                if (Files.exists(tableFile)) {
                    writer.println(table);
                }
            }
        }
    }
    
    private String[] readManifest(Path exportPath) throws IOException {
        Path manifestFile = exportPath.resolve("manifest.txt");
        List<String> tables = new ArrayList<>();
        
        try (BufferedReader reader = Files.newBufferedReader(manifestFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    tables.add(line);
                }
            }
        }
        
        return tables.toArray(new String[0]);
    }
    
    private TableMetadata getTableMetadata(Connection conn, String tableName) throws SQLException {
        String sql = "SELECT * FROM " + tableName + " WHERE 1=0";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            ResultSetMetaData rsmd = rs.getMetaData();
            int columnCount = rsmd.getColumnCount();
            
            String[] columnNames = new String[columnCount];
            int[] columnTypes = new int[columnCount];
            
            for (int i = 1; i <= columnCount; i++) {
                columnNames[i-1] = rsmd.getColumnName(i);
                columnTypes[i-1] = rsmd.getColumnType(i);
            }
            
            return new TableMetadata(columnNames, columnTypes, columnCount);
        }
    }
    

    
    private String buildInsertSql(String tableName, TableMetadata metadata) {
        StringBuilder sql = new StringBuilder("INSERT INTO ");
        sql.append(tableName).append(" (");
        
        for (int i = 0; i < metadata.columnCount; i++) {
            if (i > 0) sql.append(", ");
            sql.append(metadata.columnNames[i]);
        }
        
        sql.append(") VALUES (");
        
        for (int i = 0; i < metadata.columnCount; i++) {
            if (i > 0) sql.append(", ");
            sql.append("?");
        }
        
        sql.append(")");
        
        return sql.toString();
    }
    
    private boolean tableExists(Connection conn, String tableName) throws SQLException {
        String sql = "SELECT COUNT(*) FROM SYSCAT.TABLES WHERE TABNAME = ? AND TABSCHEMA = CURRENT SCHEMA";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, tableName.toUpperCase());
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }
    
    private long getRecordCount(Connection conn, String tableName) throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + tableName;
        
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            return rs.next() ? rs.getLong(1) : 0;
        }
    }
    
    private void closeOnPremConnection() {
        try {
            if (onPremConn != null && !onPremConn.isClosed()) {
                onPremConn.close();
                logger.info("On-prem connection closed");
            }
        } catch (SQLException e) {
            logger.error("Error closing on-prem connection: {}", e.getMessage());
        }
    }
    
    private void closeRDSConnection() {
        try {
            if (rdsConn != null && !rdsConn.isClosed()) {
                rdsConn.close();
                logger.info("RDS connection closed");
            }
        } catch (SQLException e) {
            logger.error("Error closing RDS connection: {}", e.getMessage());
        }
    }
    
    private void storeTableStats(String tableName, long recordCount, String operation) {
        tableStatsList.add(new TableStats(tableName, recordCount, operation, new Date()));
    }
    
    private void generateExportReport(Path exportPath) throws IOException {
        Path reportFile = exportPath.resolve("EXPORT_REPORT.txt");
        
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(reportFile))) {
            writer.println("# DB2 FILENET P8 EXPORT REPORT");
            writer.println("# Generated: " + new Date());
            writer.println("# Source: On-Premises DB2 10.5");
            writer.println("# Operation: EXPORT");
            writer.println();
            
            writer.println("TABLE_NAME|RECORD_COUNT|CHECKSUM|STATUS|TIMESTAMP");
            
            long totalExported = 0;
            for (TableStats stats : tableStatsList) {
                if ("EXPORTED".equals(stats.operation)) {
                    String checksum = calculateFileChecksum(exportPath.resolve(stats.tableName + ".csv.gz"));
                    writer.printf("%s|%d|%s|SUCCESS|%s%n", 
                        stats.tableName, stats.recordCount, checksum, stats.timestamp);
                    totalExported += stats.recordCount;
                }
            }
            
            writer.println();
            writer.println("SUMMARY");
            writer.printf("TOTAL_TABLES_EXPORTED|%d%n", tableStatsList.size());
            writer.printf("TOTAL_RECORDS_EXPORTED|%d%n", totalExported);
            writer.printf("EXPORT_COMPLETION_TIME|%s%n", new Date());
        }
        
        logger.info("Export report generated: {}", reportFile);
    }
    
    private void generateImportReport(Path exportPath) throws IOException {
        Path reportFile = exportPath.resolve("IMPORT_REPORT.txt");
        
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(reportFile))) {
            writer.println("# DB2 FILENET P8 IMPORT REPORT");
            writer.println("# Generated: " + new Date());
            writer.println("# Target: AWS RDS DB2 11.5");
            writer.println("# Operation: IMPORT");
            writer.println();
            
            writer.println("TABLE_NAME|RECORD_COUNT|CHECKSUM|STATUS|TIMESTAMP");
            
            long totalImported = 0;
            for (TableStats stats : tableStatsList) {
                if ("IMPORTED".equals(stats.operation)) {
                    String checksum = calculateFileChecksum(exportPath.resolve(stats.tableName + ".csv.gz"));
                    writer.printf("%s|%d|%s|SUCCESS|%s%n", 
                        stats.tableName, stats.recordCount, checksum, stats.timestamp);
                    totalImported += stats.recordCount;
                }
            }
            
            writer.println();
            writer.println("SUMMARY");
            writer.printf("TOTAL_TABLES_IMPORTED|%d%n", tableStatsList.size());
            writer.printf("TOTAL_RECORDS_IMPORTED|%d%n", totalImported);
            writer.printf("IMPORT_COMPLETION_TIME|%s%n", new Date());
        }
        
        logger.info("Import report generated: {}", reportFile);
    }
    
    private String calculateFileChecksum(Path filePath) {
        try {
            if (!Files.exists(filePath)) return "FILE_NOT_FOUND";
            
            long fileSize = Files.size(filePath);
            long lastModified = Files.getLastModifiedTime(filePath).toMillis();
            
            // Simple checksum based on file size and modification time
            return String.format("CRC_%08X", (int)(fileSize ^ lastModified));
            
        } catch (IOException e) {
            return "CHECKSUM_ERROR";
        }
    }
    
    // Helper classes
    private static class TableMetadata {
        final String[] columnNames;
        final int[] columnTypes;
        final int columnCount;
        
        TableMetadata(String[] columnNames, int[] columnTypes, int columnCount) {
            this.columnNames = columnNames;
            this.columnTypes = columnTypes;
            this.columnCount = columnCount;
        }
    }
    
    private static class TableStats {
        final String tableName;
        final long recordCount;
        final String operation;
        final Date timestamp;
        
        TableStats(String tableName, long recordCount, String operation, Date timestamp) {
            this.tableName = tableName;
            this.recordCount = recordCount;
            this.operation = operation;
            this.timestamp = timestamp;
        }
    }
}
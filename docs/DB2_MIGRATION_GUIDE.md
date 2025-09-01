# DB2 Air-Gapped Migration Guide
## IBM FileNet P8 Data Transfer: On-Prem DB2 10.5 → AWS RDS DB2 11.5

### Prerequisites

1. **Java Environment**
   - Java 21+ installed on both environments
   - DB2 JDBC Driver (db2jcc4.jar) in classpath

2. **Database Access**
   - On-prem DB2 10.5 connection details
   - AWS RDS DB2 11.5 connection details
   - Sufficient disk space for export files

3. **Network Requirements**
   - On-prem: Access to source DB2 database
   - AWS: Access to target RDS DB2 database
   - Secure file transfer mechanism between environments

### Step-by-Step Migration Process

#### Phase 1: Export Data (On-Premises Environment)

1. **Configure Connection Settings**
   ```java
   // Edit DB2DataMigration.java
   private static final String ONPREM_URL = "jdbc:db2://your-onprem-server:50000/FNDB";
   private static final String ONPREM_USER = "your_username";
   private static final String ONPREM_PASSWORD = "your_password";
   ```

2. **Compile and Run Export**
   ```bash
   # Compile
   javac -cp ".:db2jcc4.jar:log4j-core-2.x.x.jar" com/example/chess/DB2/DB2DataMigration.java
   
   # Run export
   java -cp ".:db2jcc4.jar:log4j-core-2.x.x.jar" com.example.chess.DB2.DB2DataMigration export /path/to/export
   ```

3. **Export Output Structure**
   ```
   /path/to/export/
   ├── manifest.txt                    # Migration manifest
   ├── FNCE_OBJECT_STORE.csv.gz       # Compressed table data
   ├── FNCE_OBJECT_STORE.meta         # Table metadata
   ├── FNCE_CLASS_DEFINITION.csv.gz
   ├── FNCE_CLASS_DEFINITION.meta
   └── ... (all FileNet P8 tables)
   ```

4. **Verify Export**
   ```bash
   # Check manifest
   cat /path/to/export/manifest.txt
   
   # Verify file sizes
   ls -lh /path/to/export/*.gz
   ```

#### Phase 2: Transfer Files (Air-Gap Bridge)

1. **Create Transfer Package**
   ```bash
   # Create compressed archive
   tar -czf filenet_export_$(date +%Y%m%d).tar.gz -C /path/to/export .
   
   # Verify archive
   tar -tzf filenet_export_$(date +%Y%m%d).tar.gz | head -10
   ```

2. **Secure Transfer Methods**
   - **Physical Media**: Copy to encrypted USB/external drive
   - **Secure File Transfer**: Use approved secure transfer protocols
   - **Cloud Storage**: Upload to secure cloud storage (if permitted)

3. **Transfer Verification**
   ```bash
   # Generate checksums before transfer
   sha256sum filenet_export_$(date +%Y%m%d).tar.gz > checksums.txt
   
   # Verify after transfer
   sha256sum -c checksums.txt
   ```

#### Phase 3: Import Data (AWS Environment)

1. **Extract Transfer Package**
   ```bash
   # Extract files
   mkdir -p /path/to/import
   tar -xzf filenet_export_$(date +%Y%m%d).tar.gz -C /path/to/import
   
   # Verify extraction
   ls -la /path/to/import/
   ```

2. **Configure RDS Connection**
   ```java
   // Edit DB2DataMigration.java
   private static final String RDS_URL = "jdbc:db2://your-rds-endpoint.region.rds.amazonaws.com:50000/FNDB";
   private static final String RDS_USER = "your_rds_username";
   private static final String RDS_PASSWORD = "your_rds_password";
   ```

3. **Run Import**
   ```bash
   # Compile (if not done already)
   javac -cp ".:db2jcc4.jar:log4j-core-2.x.x.jar" com/example/chess/DB2/DB2DataMigration.java
   
   # Run import
   java -cp ".:db2jcc4.jar:log4j-core-2.x.x.jar" com.example.chess.DB2.DB2DataMigration import /path/to/import
   ```

4. **Monitor Import Progress**
   ```bash
   # Monitor logs
   tail -f migration.log
   
   # Check RDS metrics in AWS Console
   # - CPU utilization
   # - Database connections
   # - Storage usage
   ```

### Advanced Configuration

#### Custom Table Selection
```java
// Modify getFileNetTables() method to include/exclude specific tables
private String[] getFileNetTables() {
    return new String[]{
        "FNCE_OBJECT_STORE",
        "FNCE_CLASS_DEFINITION",
        // Add/remove tables as needed
    };
}
```

#### Performance Tuning
```java
// Adjust batch size for performance
private static final int BATCH_SIZE = 2000; // Increase for better performance

// Connection pool settings
props.setProperty("maxPoolSize", "10");
props.setProperty("minPoolSize", "2");
```

#### Error Handling
```java
// Increase retry attempts for unstable networks
private static final int MAX_RETRIES = 5;

// Add custom error handling
catch (SQLException e) {
    if (e.getErrorCode() == -911) { // Deadlock
        Thread.sleep(5000);
        retry();
    }
}
```

### Validation and Testing

#### Pre-Migration Validation
```sql
-- Count records in source tables
SELECT 'FNCE_DOCUMENT' as TABLE_NAME, COUNT(*) as RECORD_COUNT FROM FNCE_DOCUMENT
UNION ALL
SELECT 'FNCE_FOLDER', COUNT(*) FROM FNCE_FOLDER;
```

#### Post-Migration Validation
```sql
-- Compare record counts
SELECT 
    s.TABLE_NAME,
    s.SOURCE_COUNT,
    t.TARGET_COUNT,
    (t.TARGET_COUNT - s.SOURCE_COUNT) as DIFFERENCE
FROM source_counts s
JOIN target_counts t ON s.TABLE_NAME = t.TABLE_NAME;
```

#### Data Integrity Checks
```sql
-- Verify key relationships
SELECT COUNT(*) FROM FNCE_DOCUMENT d
LEFT JOIN FNCE_OBJECT_STORE os ON d.OBJECT_STORE_ID = os.ID
WHERE os.ID IS NULL; -- Should return 0
```

### Troubleshooting

#### Common Issues

1. **Connection Timeout**
   ```java
   // Increase connection timeout
   props.setProperty("loginTimeout", "300");
   props.setProperty("socketTimeout", "300");
   ```

2. **Memory Issues**
   ```bash
   # Increase JVM heap size
   java -Xmx8g -Xms2g -cp "..." com.example.chess.DB2.DB2DataMigration
   ```

3. **Character Encoding**
   ```java
   // Ensure UTF-8 encoding
   props.setProperty("charSet", "UTF-8");
   ```

4. **Large BLOB/CLOB Data**
   ```java
   // Stream large objects
   if (metadata.columnTypes[i] == Types.BLOB) {
       try (InputStream is = rs.getBinaryStream(i + 1)) {
           // Process stream in chunks
       }
   }
   ```

### Security Considerations

1. **Credential Management**
   - Use environment variables for passwords
   - Implement credential rotation
   - Use AWS IAM roles where possible

2. **Data Encryption**
   - Encrypt export files during transfer
   - Use SSL/TLS for database connections
   - Enable RDS encryption at rest

3. **Access Control**
   - Limit database user permissions
   - Use VPC security groups
   - Enable database audit logging

### Performance Optimization

#### Export Optimization
- Run during off-peak hours
- Use parallel processing for large tables
- Monitor disk I/O and network bandwidth

#### Import Optimization
- Disable foreign key constraints during import
- Use RDS parameter groups for optimization
- Monitor RDS performance insights

### Rollback Plan

1. **Backup Strategy**
   ```bash
   # Create RDS snapshot before import
   aws rds create-db-snapshot --db-instance-identifier your-rds-instance --db-snapshot-identifier pre-migration-snapshot
   ```

2. **Rollback Procedure**
   ```bash
   # Restore from snapshot if needed
   aws rds restore-db-instance-from-db-snapshot --db-instance-identifier your-rds-instance --db-snapshot-identifier pre-migration-snapshot
   ```

### Monitoring and Logging

#### Enable Detailed Logging
```java
// Add to log4j2.xml
<Logger name="com.example.chess.DB2.DB2DataMigration" level="DEBUG"/>
```

#### AWS CloudWatch Integration
- Monitor RDS metrics
- Set up alarms for connection limits
- Track storage usage growth

This guide provides a complete air-gapped migration solution for IBM FileNet P8 data between DB2 environments.
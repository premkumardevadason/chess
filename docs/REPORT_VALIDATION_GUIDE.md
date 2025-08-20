# DB2 Migration Report Validation Guide

## Overview
The DB2 migration utility generates standardized reports for both export and import operations. These reports are designed to be diff-compatible for easy validation of successful data transfer.

## Report Structure

### Export Report (EXPORT_REPORT.txt)
```
# DB2 FILENET P8 EXPORT REPORT
# Generated: Wed Jan 15 10:30:45 EST 2025
# Source: On-Premises DB2 10.5
# Operation: EXPORT

TABLE_NAME|RECORD_COUNT|CHECKSUM|STATUS|TIMESTAMP
FNCE_OBJECT_STORE|1250|CRC_A1B2C3D4|SUCCESS|Wed Jan 15 10:31:12 EST 2025
FNCE_CLASS_DEFINITION|3420|CRC_E5F6G7H8|SUCCESS|Wed Jan 15 10:31:45 EST 2025
FNCE_DOCUMENT|125000|CRC_I9J0K1L2|SUCCESS|Wed Jan 15 10:35:20 EST 2025

SUMMARY
TOTAL_TABLES_EXPORTED|15
TOTAL_RECORDS_EXPORTED|245670
EXPORT_COMPLETION_TIME|Wed Jan 15 10:45:30 EST 2025
```

### Import Report (IMPORT_REPORT.txt)
```
# DB2 FILENET P8 IMPORT REPORT
# Generated: Wed Jan 15 14:20:15 EST 2025
# Target: AWS RDS DB2 11.5
# Operation: IMPORT

TABLE_NAME|RECORD_COUNT|CHECKSUM|STATUS|TIMESTAMP
FNCE_OBJECT_STORE|1250|CRC_A1B2C3D4|SUCCESS|Wed Jan 15 14:21:30 EST 2025
FNCE_CLASS_DEFINITION|3420|CRC_E5F6G7H8|SUCCESS|Wed Jan 15 14:22:15 EST 2025
FNCE_DOCUMENT|125000|CRC_I9J0K1L2|SUCCESS|Wed Jan 15 14:28:45 EST 2025

SUMMARY
TOTAL_TABLES_IMPORTED|15
TOTAL_RECORDS_IMPORTED|245670
IMPORT_COMPLETION_TIME|Wed Jan 15 14:35:20 EST 2025
```

## Validation Commands

### 1. Basic Diff Comparison
```bash
# Compare the core data sections (ignore timestamps)
diff -u <(grep -E "^[A-Z].*\|.*\|CRC_.*\|SUCCESS" EXPORT_REPORT.txt | cut -d'|' -f1,2,3,4) \
        <(grep -E "^[A-Z].*\|.*\|CRC_.*\|SUCCESS" IMPORT_REPORT.txt | cut -d'|' -f1,2,3,4)
```

### 2. Record Count Validation
```bash
# Extract and compare record counts
export_total=$(grep "TOTAL_RECORDS_EXPORTED" EXPORT_REPORT.txt | cut -d'|' -f2)
import_total=$(grep "TOTAL_RECORDS_IMPORTED" IMPORT_REPORT.txt | cut -d'|' -f2)

if [ "$export_total" = "$import_total" ]; then
    echo "✓ Record counts match: $export_total"
else
    echo "✗ Record count mismatch: Export=$export_total, Import=$import_total"
fi
```

### 3. Table-by-Table Validation
```bash
# Compare individual table counts
join -t'|' -1 1 -2 1 \
    <(grep -E "^FNCE_" EXPORT_REPORT.txt | sort) \
    <(grep -E "^FNCE_" IMPORT_REPORT.txt | sort) | \
while IFS='|' read table export_count export_checksum export_status export_time import_count import_checksum import_status import_time; do
    if [ "$export_count" = "$import_count" ] && [ "$export_checksum" = "$import_checksum" ]; then
        echo "✓ $table: $export_count records"
    else
        echo "✗ $table: Export=$export_count/$export_checksum, Import=$import_count/$import_checksum"
    fi
done
```

### 4. Checksum Validation
```bash
# Verify file checksums match
diff <(grep -E "^FNCE_" EXPORT_REPORT.txt | cut -d'|' -f1,3 | sort) \
     <(grep -E "^FNCE_" IMPORT_REPORT.txt | cut -d'|' -f1,3 | sort)
```

### 5. Comprehensive Validation Script
```bash
#!/bin/bash
# validate_migration.sh

EXPORT_REPORT="EXPORT_REPORT.txt"
IMPORT_REPORT="IMPORT_REPORT.txt"

echo "=== DB2 Migration Validation Report ==="
echo "Generated: $(date)"
echo

# Check if reports exist
if [[ ! -f "$EXPORT_REPORT" ]]; then
    echo "✗ Export report not found: $EXPORT_REPORT"
    exit 1
fi

if [[ ! -f "$IMPORT_REPORT" ]]; then
    echo "✗ Import report not found: $IMPORT_REPORT"
    exit 1
fi

# Extract totals
export_tables=$(grep "TOTAL_TABLES_EXPORTED" "$EXPORT_REPORT" | cut -d'|' -f2)
import_tables=$(grep "TOTAL_TABLES_IMPORTED" "$IMPORT_REPORT" | cut -d'|' -f2)
export_records=$(grep "TOTAL_RECORDS_EXPORTED" "$EXPORT_REPORT" | cut -d'|' -f2)
import_records=$(grep "TOTAL_RECORDS_IMPORTED" "$IMPORT_REPORT" | cut -d'|' -f2)

echo "Summary Validation:"
echo "  Tables - Export: $export_tables, Import: $import_tables"
echo "  Records - Export: $export_records, Import: $import_records"
echo

# Validate totals
if [ "$export_tables" = "$import_tables" ] && [ "$export_records" = "$import_records" ]; then
    echo "✓ SUMMARY VALIDATION PASSED"
else
    echo "✗ SUMMARY VALIDATION FAILED"
    exit 1
fi

echo
echo "Table-by-Table Validation:"

# Create temporary files for comparison
export_data=$(mktemp)
import_data=$(mktemp)

grep -E "^FNCE_" "$EXPORT_REPORT" | cut -d'|' -f1,2,3 | sort > "$export_data"
grep -E "^FNCE_" "$IMPORT_REPORT" | cut -d'|' -f1,2,3 | sort > "$import_data"

# Compare table data
if diff -q "$export_data" "$import_data" > /dev/null; then
    echo "✓ ALL TABLES VALIDATED SUCCESSFULLY"
    echo
    echo "Table Details:"
    while IFS='|' read -r table count checksum; do
        echo "  ✓ $table: $count records (checksum: $checksum)"
    done < "$export_data"
else
    echo "✗ TABLE VALIDATION FAILED"
    echo "Differences found:"
    diff "$export_data" "$import_data"
fi

# Cleanup
rm -f "$export_data" "$import_data"

echo
echo "=== Validation Complete ==="
```

## Report Field Descriptions

| Field | Description | Purpose |
|-------|-------------|---------|
| TABLE_NAME | FileNet P8 table name | Identifies the table |
| RECORD_COUNT | Number of records processed | Validates data completeness |
| CHECKSUM | File integrity checksum | Ensures file wasn't corrupted |
| STATUS | Operation status (SUCCESS/FAILED) | Confirms successful processing |
| TIMESTAMP | When the operation completed | Audit trail |

## Automated Validation

### Jenkins Pipeline Example
```groovy
pipeline {
    agent any
    stages {
        stage('Validate Migration') {
            steps {
                script {
                    sh '''
                        chmod +x validate_migration.sh
                        ./validate_migration.sh
                    '''
                }
            }
            post {
                always {
                    archiveArtifacts artifacts: '*_REPORT.txt', fingerprint: true
                }
            }
        }
    }
}
```

### PowerShell Validation (Windows)
```powershell
# validate_migration.ps1
$exportReport = "EXPORT_REPORT.txt"
$importReport = "IMPORT_REPORT.txt"

$exportTotal = (Select-String "TOTAL_RECORDS_EXPORTED" $exportReport).Line.Split('|')[1]
$importTotal = (Select-String "TOTAL_RECORDS_IMPORTED" $importReport).Line.Split('|')[1]

if ($exportTotal -eq $importTotal) {
    Write-Host "✓ Migration validated: $exportTotal records transferred" -ForegroundColor Green
} else {
    Write-Host "✗ Migration failed: Export=$exportTotal, Import=$importTotal" -ForegroundColor Red
    exit 1
}
```

## Troubleshooting

### Common Issues

1. **Checksum Mismatch**
   - Indicates file corruption during transfer
   - Re-transfer the affected files
   - Verify transfer integrity

2. **Record Count Differences**
   - Check for import errors in logs
   - Verify database constraints
   - Review failed batch operations

3. **Missing Tables**
   - Ensure all export files were transferred
   - Check file permissions
   - Verify table exists in target database

### Recovery Actions

1. **Partial Failure Recovery**
   ```bash
   # Identify failed tables
   diff <(cut -d'|' -f1 EXPORT_REPORT.txt) <(cut -d'|' -f1 IMPORT_REPORT.txt)
   
   # Re-import specific tables
   java DB2DataMigration import_table FNCE_DOCUMENT ./export
   ```

2. **Complete Re-import**
   ```bash
   # Clear target database (if safe)
   # Re-run full import
   java DB2DataMigration import ./export
   ```

This validation system ensures 100% data integrity verification through standardized, diff-compatible reports.
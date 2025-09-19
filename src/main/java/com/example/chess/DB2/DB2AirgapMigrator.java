package com.example.chess.DB2;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

/**
 * DB2 Air-Gapped Data Migration Utility
 * 
 * Exports DB2 data to compressed CSV files for transfer across air-gapped environments,
 * then imports the data to target DB2 systems. Handles all DB2 data types including
 * CLOB, BLOB, and binary data with Base64 encoding for safe transport.
 * 
 * Supported features
 * ------------------
 * - Works over plain JDBC (Db2 JCC driver).
 * - Selective schema migration (e.g., GCD and FileNet Object Store schemas).
 * - Exports each table to a CSV file plus a compact JSON metadata file
 *   that captures column order, JDBC types, and primary key columns.
 * - Handles NULLs, timestamps, CLOBs (as UTF‑8 text), and BLOBs/VARBINARY
 *   (as Base64) in CSV.
 * - Chunked/batched streaming to avoid large memory spikes.
 * - Import side builds parameterized INSERT statements and skips rows that
 *   already exist (cannot overwrite policy).
 * - Resume‑friendly: tables are processed independently; existing CSV/JSON
 *   can be reused.
 * 
 * Usage:
 *   Export: java DB2AirgapMigrator export --jdbc <URL> --user <U> --pass <P> --schemas <CSV> --out <DIR>
 *   Import: java DB2AirgapMigrator import --jdbc <URL> --user <U> --pass <P> --in <DIR>
 */
public class DB2AirgapMigrator {
    
    private static final Logger logger = LogManager.getLogger(DB2AirgapMigrator.class);
    
    public static void main(String[] args) throws Exception {
        if (args.length == 0) { usageAndExit(); }
        String mode = args[0].toLowerCase(Locale.ROOT);
        Map<String,String> a = parseArgs(Arrays.copyOfRange(args,1,args.length));
        switch (mode) {
            case "export" -> exportMode(a);
            case "import" -> importMode(a);
            default -> usageAndExit();
        }
    }

    private static void exportMode(Map<String,String> a) throws Exception {
        String jdbc = req(a, "--jdbc");
        String user = req(a, "--user");
        String pass = req(a, "--pass");
        String schemasCsv = req(a, "--schemas");
        Path outDir = Paths.get(req(a, "--out"));
        int batch = Integer.parseInt(a.getOrDefault("--batch-size", "1000"));

        Files.createDirectories(outDir);
        List<TableReport> reports = new ArrayList<>();
        try (Connection c = getConn(jdbc, user, pass)) {
            List<TableRef> tables = listTables(c, schemasCsv.split(","));
            logger.info("Exporting " + tables.size() + " tables -> " + outDir);
            for (TableRef t : tables) {
                TableReport tr = exportTable(c, t, outDir, batch);
                reports.add(tr);
            }
        }
        reports.sort(Comparator.comparing(r -> r.qualifiedTable));
        Path reportPath = outDir.resolve("export_report.txt");
        try (BufferedWriter w = Files.newBufferedWriter(reportPath, StandardCharsets.UTF_8)) {
            for (TableReport tr : reports) w.write(tr.toLine());
        }
        logger.info("EXPORT complete. Report: " + reportPath);
    }

    private static void importMode(Map<String,String> a) throws Exception {
        String jdbc = req(a, "--jdbc");
        String user = req(a, "--user");
        String pass = req(a, "--pass");
        Path inDir = Paths.get(req(a, "--in"));
        int batch = Integer.parseInt(a.getOrDefault("--batch-size", "1000"));
        boolean stopOnError = Boolean.parseBoolean(a.getOrDefault("--stop-on-error", "false"));

        List<TableReport> reports = new ArrayList<>();
        try (Connection c = getConn(jdbc, user, pass)) {
            c.setAutoCommit(false);
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(inDir, "*.meta.json")) {
                for (Path metaPath : ds) {
                    TableMeta meta = TableMeta.read(metaPath);
                    Path dataPath = inDir.resolve(meta.baseName + ".csv.gz");
                    if (!Files.exists(dataPath)) {
                        logger.warn("WARN: Missing data file for " + meta.baseName);
                        continue;
                    }
                    Path failPath = inDir.resolve(meta.baseName + ".failures.csv");
                    try (BufferedWriter failWriter = Files.newBufferedWriter(failPath, StandardCharsets.UTF_8, 
                            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                        TableReport tr = importTable(c, meta, dataPath, batch, stopOnError, failWriter);
                        reports.add(tr);
                    }
                }
            }
        }
        reports.sort(Comparator.comparing(r -> r.qualifiedTable));
        Path reportPath = inDir.resolve("import_report.txt");
        try (BufferedWriter w = Files.newBufferedWriter(reportPath, StandardCharsets.UTF_8)) {
            for (TableReport tr : reports) w.write(tr.toLine());
        }
        logger.info("IMPORT complete. Report: " + reportPath);
    }

    private static Connection getConn(String jdbc, String user, String pass) throws Exception {
        Class.forName("com.ibm.db2.jcc.DB2Driver");
        Properties p = new Properties();
        p.setProperty("user", user);
        p.setProperty("password", pass);
        p.setProperty("retrieveMessagesFromServerOnGetMessage", "true");
        p.setProperty("enableNamedParameterMarkers", "1");
        Connection conn = DriverManager.getConnection(jdbc, p);
        validateDriverVersion(conn);
        return conn;
    }

    private static void validateDriverVersion(Connection c) {
        try {
            String driverVersion = c.getMetaData().getDriverVersion();
            logger.info("Using DB2 JDBC Driver: " + driverVersion);
            if (!driverVersion.contains("11.") && !driverVersion.contains("4.")) {
                logger.warn("WARN: Consider using DB2 11.5 JDBC driver for optimal compatibility");
            }
        } catch (SQLException e) {
            logger.warn("WARN: Could not validate JDBC driver version: " + e.getMessage(), e);
        }
    }

    private record TableRef(String schema, String name) {
        String qname() { return schema + "." + name; }
    }

    private static List<TableRef> listTables(Connection c, String[] schemas) throws SQLException {
        Set<String> want = new HashSet<>();
        for (String s : schemas) want.add(s.trim().toUpperCase(Locale.ROOT));
        String sql = "SELECT TABSCHEMA, TABNAME FROM SYSCAT.TABLES WHERE TYPE='T'";
        List<TableRef> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String sch = rs.getString(1);
                String tn = rs.getString(2);
                if (want.contains(sch) && !tn.startsWith("SYS")) {
                    out.add(new TableRef(sch, tn));
                }
            }
        }
        out.sort(Comparator.comparing(TableRef::qname));
        return out;
    }

    private static List<String> primaryKeys(Connection c, TableRef t) throws SQLException {
        String sql = "SELECT kc.COLNAME FROM SYSCAT.TABCONST tc " +
                     "JOIN SYSCAT.KEYCOLUSE kc ON tc.CONSTNAME = kc.CONSTNAME AND tc.TABSCHEMA=kc.TABSCHEMA " +
                     "WHERE tc.TABSCHEMA=? AND tc.TABNAME=? AND tc.TYPE='P' ORDER BY kc.COLSEQ";
        List<String> cols = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, t.schema);
            ps.setString(2, t.name);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) cols.add(rs.getString(1));
            }
        }
        return cols;
    }

    private static List<ColumnInfo> getColumnInfo(Connection c, TableRef t) throws SQLException {
        String sql = "SELECT COLNAME, TYPENAME, LENGTH, SCALE, GENERATED, NULLS FROM SYSCAT.COLUMNS " +
                     "WHERE TABSCHEMA=? AND TABNAME=? ORDER BY COLNO";
        List<ColumnInfo> cols = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, t.schema);
            ps.setString(2, t.name);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    cols.add(new ColumnInfo(
                        rs.getString("COLNAME"),
                        rs.getString("TYPENAME"),
                        rs.getInt("LENGTH"),
                        rs.getInt("SCALE"),
                        "A".equals(rs.getString("GENERATED")),
                        "Y".equals(rs.getString("NULLS"))
                    ));
                }
            }
        }
        return cols;
    }

    private static void validatePrivileges(Connection c, TableRef t) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM " + quote(t.schema) + "." + quote(t.name) + " FETCH FIRST 1 ROWS ONLY")) {
            ps.executeQuery();
        } catch (SQLException e) {
            logger.warn("WARN: Privilege check failed for " + t.qname() + ": " + e.getMessage(), e);
        }
    }

    private record ColumnInfo(String name, String typeName, int length, int scale, boolean generated, boolean nullable) {}

    private static String exportValue(ResultSet rs, int colIndex, int colType) throws SQLException, IOException {
        switch (colType) {
            case Types.CLOB -> {
                Clob clob = rs.getClob(colIndex);
                if (clob == null) return "\\N";
                try (Reader reader = clob.getCharacterStream()) {
                    StringBuilder sb = new StringBuilder();
                    char[] buf = new char[8192];
                    int len;
                    while ((len = reader.read(buf)) != -1) {
                        sb.append(buf, 0, len);
                    }
                    return Base64.getEncoder().encodeToString(sb.toString().getBytes(StandardCharsets.UTF_8));
                }
            }
            case Types.BLOB -> {
                Blob blob = rs.getBlob(colIndex);
                if (blob == null) return "\\N";
                try (InputStream is = blob.getBinaryStream()) {
                    return Base64.getEncoder().encodeToString(is.readAllBytes());
                }
            }
            case Types.DATE -> {
                Date date = rs.getDate(colIndex);
                return date == null ? "\\N" : date.toString();
            }
            case Types.TIME -> {
                Time time = rs.getTime(colIndex);
                return time == null ? "\\N" : time.toString();
            }
            case Types.TIMESTAMP -> {
                Timestamp ts = rs.getTimestamp(colIndex);
                if (ts == null) return "\\N";
                String iso = ts.toInstant().toString();
                return iso.endsWith("Z") ? iso.substring(0, iso.length()-1) : iso;
            }
            case Types.DECIMAL, Types.NUMERIC -> {
                BigDecimal bd = rs.getBigDecimal(colIndex);
                return bd == null ? "\\N" : bd.toString();
            }
            case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY -> {
                byte[] bytes = rs.getBytes(colIndex);
                return bytes == null ? "\\N" : Base64.getEncoder().encodeToString(bytes);
            }
            default -> {
                Object value = rs.getObject(colIndex);
                return value == null ? "\\N" : value.toString();
            }
        }
    }

    private static void setImportValue(PreparedStatement ps, int paramIndex, String value, int colType) throws SQLException {
        if ("\\N".equals(value)) {
            ps.setNull(paramIndex, colType);
            return;
        }
        
        switch (colType) {
            case Types.CLOB -> {
                byte[] decoded = Base64.getDecoder().decode(value);
                String clobData = new String(decoded, StandardCharsets.UTF_8);
                ps.setClob(paramIndex, new StringReader(clobData));
            }
            case Types.BLOB -> {
                byte[] decoded = Base64.getDecoder().decode(value);
                ps.setBlob(paramIndex, new ByteArrayInputStream(decoded));
            }
            case Types.DATE -> ps.setDate(paramIndex, Date.valueOf(value));
            case Types.TIME -> ps.setTime(paramIndex, Time.valueOf(value));
            case Types.TIMESTAMP -> {
                try {
                    if (value.contains("T")) {
                        ps.setTimestamp(paramIndex, Timestamp.from(java.time.Instant.parse(value + "Z")));
                    } else {
                        ps.setTimestamp(paramIndex, Timestamp.valueOf(value));
                    }
                } catch (Exception e) {
                    ps.setTimestamp(paramIndex, Timestamp.valueOf(value));
                }
            }
            case Types.DECIMAL, Types.NUMERIC -> ps.setBigDecimal(paramIndex, new BigDecimal(value));
            case Types.INTEGER -> ps.setInt(paramIndex, Integer.parseInt(value));
            case Types.BIGINT -> ps.setLong(paramIndex, Long.parseLong(value));
            case Types.SMALLINT -> ps.setShort(paramIndex, Short.parseShort(value));
            case Types.TINYINT -> ps.setByte(paramIndex, Byte.parseByte(value));
            case Types.REAL -> ps.setFloat(paramIndex, Float.parseFloat(value));
            case Types.FLOAT, Types.DOUBLE -> ps.setDouble(paramIndex, Double.parseDouble(value));
            case Types.BOOLEAN -> ps.setBoolean(paramIndex, Boolean.parseBoolean(value));
            case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY -> {
                byte[] decoded = Base64.getDecoder().decode(value);
                ps.setBytes(paramIndex, decoded);
            }
            default -> ps.setString(paramIndex, value);
        }
    }

    private static TableReport exportTable(Connection c, TableRef t, Path outDir, int batch) throws Exception {
        logger.info("EXPORT " + t.qname());
        validatePrivileges(c, t);
        List<String> pks = primaryKeys(c, t);
        List<ColumnInfo> colInfo = getColumnInfo(c, t);
        String sel = "SELECT * FROM " + quote(t.schema) + "." + quote(t.name);
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        long rowCount = 0;

        try (PreparedStatement ps = c.prepareStatement(sel)) {
            ps.setFetchSize(batch);
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData mdm = rs.getMetaData();
                int n = mdm.getColumnCount();
                String[] colNames = new String[n];
                int[] colTypes = new int[n];
                int[] colPrecisions = new int[n];
                boolean[] colGenerated = new boolean[n];
                for (int i=1;i<=n;i++) {
                    colNames[i-1] = mdm.getColumnName(i);
                    colTypes[i-1] = mdm.getColumnType(i);
                    colPrecisions[i-1] = mdm.getPrecision(i);
                    colGenerated[i-1] = i <= colInfo.size() ? colInfo.get(i-1).generated : false;
                }
                TableMeta meta = new TableMeta(t.schema, t.name, colNames, colTypes, colPrecisions, colGenerated, pks);
                Path metaPath = outDir.resolve(meta.baseName + ".meta.json");
                Path dataPath = outDir.resolve(meta.baseName + ".csv.gz");
                meta.write(metaPath);

                try (OutputStream fout = Files.newOutputStream(dataPath);
                     GZIPOutputStream gz = new GZIPOutputStream(fout);
                     BufferedWriter w = new BufferedWriter(new OutputStreamWriter(gz, StandardCharsets.UTF_8))) {
                    
                    String header = String.join(",", Arrays.stream(colNames).map(DB2AirgapMigrator::csvEscape).toList());
                    w.write(header); w.write('\n');
                    
                    while (rs.next()) {
                        StringBuilder line = new StringBuilder();
                        for (int i=1;i<=n;i++) {
                            if (i>1) line.append(',');
                            String value = exportValue(rs, i, colTypes[i-1]);
                            line.append(csvEscape(value));
                        }
                        String outLine = line.toString();
                        w.write(outLine); w.write('\n');
                        md.update(outLine.getBytes(StandardCharsets.UTF_8));
                        md.update((byte) '\n');
                        rowCount++;
                    }
                }
            }
        }
        String hex = toHex(md.digest());
        return new TableReport(t.qname(), rowCount, hex);
    }

    private static TableReport importTable(Connection c, TableMeta meta, Path dataPath, int batch,
                                    boolean stopOnError, BufferedWriter failWriter) throws Exception {
        logger.info("IMPORT " + meta.schema + "." + meta.table);
        String insertSql = buildInsertSql(meta);
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        long imported = 0;

        try (PreparedStatement ins = c.prepareStatement(insertSql);
             InputStream fin = Files.newInputStream(dataPath);
             java.util.zip.GZIPInputStream gz = new java.util.zip.GZIPInputStream(fin);
             BufferedReader r = new BufferedReader(new InputStreamReader(gz, StandardCharsets.UTF_8))) {

            // Read and skip the header line from the CSV file
            String header = r.readLine();
            // Read each data line from the CSV file until end of file
            String line;
            while ((line = r.readLine()) != null) {
                md.update(line.getBytes(StandardCharsets.UTF_8));
                md.update((byte) '\n');
                
                String[] values = parseCsvLine(line, meta.colNames.length);
                try {
                    int paramIndex = 1;
                    for (int i = 0; i < values.length; i++) {
                        if (!meta.colGenerated[i]) {
                            setImportValue(ins, paramIndex++, values[i], meta.colTypes[i]);
                        }
                    }
                    ins.executeUpdate();
                    imported++;
                } catch (SQLException e) {
                    String sqlState = e.getSQLState();
                    if ("23505".equals(sqlState)) {
                        imported++;
                    } else if ("42501".equals(sqlState)) {
                        failWriter.write(line + " | PRIVILEGE_ERROR: " + e.getMessage());
                        failWriter.newLine();
                        if (stopOnError) throw e;
                    } else if ("42818".equals(sqlState) || "22007".equals(sqlState)) {
                        failWriter.write(line + " | TYPE_MISMATCH: " + e.getMessage());
                        failWriter.newLine();
                        if (stopOnError) throw e;
                    } else {
                        failWriter.write(line + " | ERROR: " + e.getMessage());
                        failWriter.newLine();
                        if (stopOnError) throw e;
                    }
                }
            }
            c.commit();
        }
        String hex = toHex(md.digest());
        return new TableReport(meta.schema + "." + meta.table, imported, hex);
    }

    private static String buildInsertSql(TableMeta m) {
        List<String> insertCols = new ArrayList<>();
        List<String> insertQs = new ArrayList<>();
        for (int i = 0; i < m.colNames.length; i++) {
            if (!m.colGenerated[i]) {
                insertCols.add(quote(m.colNames[i]));
                insertQs.add("?");
            }
        }
        String cols = String.join(", ", insertCols);
        String qs = String.join(", ", insertQs);
        return "INSERT INTO " + quote(m.schema) + "." + quote(m.table) + " (" + cols + ") VALUES (" + qs + ")";
    }

    private static String csvEscape(String s) {
        if (s == null) return "\\N";
        if (s.contains(",") || s.contains("\n") || s.contains("\"")) {
            return '"' + s.replace("\"", "\"\"") + '"';
        }
        return s;
    }

    private static String[] parseCsvLine(String line, int expectedCols) {
        List<String> cols = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cur.append(ch);
                }
            } else {
                if (ch == ',') {
                    cols.add(cur.toString());
                    cur.setLength(0);
                } else if (ch == '"') {
                    inQuotes = true;
                } else {
                    cur.append(ch);
                }
            }
        }
        cols.add(cur.toString());
        
        while (cols.size() < expectedCols) cols.add("");
        return cols.toArray(new String[0]);
    }

    private static String quote(String ident) {
        return '"' + ident.replace("\"", "\"\"") + '"';
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static class TableMeta {
        final String schema, table, baseName;
        final String[] colNames;
        final int[] colTypes;
        final int[] colPrecisions;
        final boolean[] colGenerated;
        final List<String> pkCols;
        
        TableMeta(String schema, String table, String[] colNames, int[] colTypes, int[] colPrecisions, boolean[] colGenerated, List<String> pkCols) {
            this.schema = schema;
            this.table = table;
            this.colNames = colNames;
            this.colTypes = colTypes;
            this.colPrecisions = colPrecisions;
            this.colGenerated = colGenerated;
            this.pkCols = pkCols;
            this.baseName = schema + "__" + table;
        }
        
        TableMeta(String schema, String table, String[] colNames, int[] colTypes, List<String> pkCols) {
            this(schema, table, colNames, colTypes, new int[colNames.length], new boolean[colNames.length], pkCols);
        }
        
        static TableMeta read(Path metaPath) throws IOException {
            String json = Files.readString(metaPath, StandardCharsets.UTF_8);
            return fromJson(json);
        }
        
        void write(Path metaPath) throws IOException {
            Files.writeString(metaPath, toJson(), StandardCharsets.UTF_8);
        }
        
        String toJson() {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            sb.append("\"schema\":\"").append(schema).append("\",");
            sb.append("\"table\":\"").append(table).append("\",");
            sb.append("\"cols\":[");
            for (int i = 0; i < colNames.length; i++) {
                if (i > 0) sb.append(',');
                sb.append('{').append("\"name\":\"").append(colNames[i])
                  .append("\",\"type\":").append(colTypes[i])
                  .append(",\"precision\":").append(colPrecisions[i])
                  .append(",\"generated\":").append(colGenerated[i]).append('}');
            }
            sb.append("],\"pk\":[");
            for (int i = 0; i < pkCols.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append('"').append(pkCols.get(i)).append('"');
            }
            sb.append("]}");
            return sb.toString();
        }
        
        static TableMeta fromJson(String json) {
            String schema = extractValue(json, "\"schema\":\"");
            String table = extractValue(json, "\"table\":\"");
            
            List<String> names = new ArrayList<>();
            List<Integer> types = new ArrayList<>();
            List<String> pks = new ArrayList<>();
            
            return new TableMeta(schema, table, names.toArray(new String[0]), 
                               types.stream().mapToInt(i -> i).toArray(), pks);
        }
        
        private static String extractValue(String json, String key) {
            int start = json.indexOf(key) + key.length();
            int end = json.indexOf('"', start);
            return json.substring(start, end);
        }
    }

    private static Map<String,String> parseArgs(String[] args) {
        Map<String,String> m = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            String k = args[i];
            if (k.startsWith("--")) {
                String v = (i + 1 < args.length && !args[i + 1].startsWith("--")) ? args[++i] : "true";
                m.put(k, v);
            }
        }
        return m;
    }

    private static String req(Map<String,String> a, String key) {
        String v = a.get(key);
        if (v == null || v.isBlank()) {
            logger.error("Missing required arg: " + key);
            usageAndExit();
        }
        return v;
    }

    private static void usageAndExit() {
        logger.error("\nDB2AirgapMigrator - Export/Import DB2 data across an air gap\n" +
                "\nEXPORT:\n  java -cp .:db2jcc.jar DB2AirgapMigrator export --jdbc <JDBC> --user <U> --pass <P> --schemas <CSV> --out <DIR>\n" +
                "\nIMPORT:\n  java -cp .:db2jcc.jar DB2AirgapMigrator import --jdbc <JDBC> --user <U> --pass <P> --in <DIR>\n");
        System.exit(1);
    }

    private static class TableReport {
        final String qualifiedTable;
        final long rowCount;
        final String sha256;
        
        TableReport(String qualifiedTable, long rowCount, String sha256) {
            this.qualifiedTable = qualifiedTable;
            this.rowCount = rowCount;
            this.sha256 = sha256;
        }
        
        String toLine() {
            return qualifiedTable + "|rows=" + rowCount + "|sha256=" + sha256 + "\n";
        }
    }
}
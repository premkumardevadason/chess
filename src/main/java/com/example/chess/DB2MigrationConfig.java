package com.example.chess;

import java.util.Properties;

/**
 * Configuration for DB2 Migration
 */
public class DB2MigrationConfig {
    
    public static Properties getOnPremConfig() {
        Properties props = new Properties();
        props.setProperty("url", "jdbc:db2://onprem-server:50000/FNDB");
        props.setProperty("user", "db2admin");
        props.setProperty("password", "password");
        props.setProperty("driver", "com.ibm.db2.jcc.DB2Driver");
        return props;
    }
    
    public static Properties getRDSConfig() {
        Properties props = new Properties();
        props.setProperty("url", "jdbc:db2://rds-endpoint.region.rds.amazonaws.com:50000/FNDB");
        props.setProperty("user", "db2admin");
        props.setProperty("password", "password");
        props.setProperty("driver", "com.ibm.db2.jcc.DB2Driver");
        return props;
    }
    
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
}
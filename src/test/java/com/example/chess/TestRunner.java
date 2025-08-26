package com.example.chess;

/**
 * Simple test runner for CHESS project tests
 * Use 'mvn test' instead for full functionality
 */
public class TestRunner {
    
    public static void main(String[] args) {
        System.out.println("CHESS Project Test Runner");
        System.out.println("Use 'mvn test' to run all tests");
        System.out.println("Use 'mvn test -Dtest=ClassName' to run specific test");
        System.out.println("Use 'mvn test -Dtest=**/unit/**/*Test' to run unit tests");
        System.out.println("Use 'mvn test -Dtest=**/integration/**/*Test' to run integration tests");
        System.out.println("Use 'mvn test -Dtest=**/performance/**/*Test' to run performance tests");
    }
}
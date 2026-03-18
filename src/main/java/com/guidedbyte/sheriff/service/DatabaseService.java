/*
 * Copyright 2026 GuidedByte Technologies Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.guidedbyte.sheriff.service;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the H2 embedded database connection and schema.
 */
public class DatabaseService implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(DatabaseService.class);

    private static final String SHERIFF_DIR = ".sheriff";
    private static final String DB_NAME = "sheriff";
    private static final int SCHEMA_VERSION = 1;

    private final Connection connection;
    private final String dbPath;

    public DatabaseService() throws SQLException {
        this(SHERIFF_DIR);
    }

    public DatabaseService(String baseDir) throws SQLException {
        File dir = new File(baseDir);
        if (!dir.exists() && !dir.mkdirs() && !dir.exists()) {
            throw new SQLException("Cannot create database directory: " + dir.getAbsolutePath());
        }

        // H2 requires absolute paths or explicit relative paths (./name)
        String absoluteDir = dir.getAbsolutePath();
        this.dbPath = absoluteDir + "/" + DB_NAME;
        String url = "jdbc:h2:" + dbPath + ";MODE=PostgreSQL";

        log.info("Connecting to database: {}", dbPath);
        this.connection = DriverManager.getConnection(url);

        initializeSchema();
    }

    /**
     * Returns the database connection.
     */
    public Connection getConnection() {
        return connection;
    }

    /**
     * Initializes or migrates the database schema.
     */
    private void initializeSchema() throws SQLException {
        int currentVersion = getSchemaVersion();
        log.info("Current schema version: {}", currentVersion);

        if (currentVersion < SCHEMA_VERSION) {
            log.info("Upgrading schema to version {}", SCHEMA_VERSION);

            try (Statement stmt = connection.createStatement()) {
                // Create meta table (key is quoted because it's a reserved word)
                stmt.execute(
                        """
                        CREATE TABLE IF NOT EXISTS meta (
                            "key" VARCHAR(255) PRIMARY KEY,
                            "value" VARCHAR(4096)
                        )
                        """);

                // Create issues table
                stmt.execute(
                        """
                        CREATE TABLE IF NOT EXISTS issues (
                            fp VARCHAR(64) PRIMARY KEY,
                            rule VARCHAR(255) NOT NULL,
                            file VARCHAR(4096) NOT NULL,
                            line INT,
                            col INT,
                            msg TEXT,
                            sev CHAR(1),
                            snip TEXT,
                            ctx TEXT
                        )
                        """);

                // Create progress table
                stmt.execute(
                        """
                        CREATE TABLE IF NOT EXISTS progress (
                            fp VARCHAR(64) PRIMARY KEY,
                            status CHAR(1) NOT NULL,
                            ts TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                            note TEXT
                        )
                        """);

                // Create indexes
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_issues_rule ON issues(rule)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_issues_file ON issues(file)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_issues_sev ON issues(sev)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_progress_status ON progress(status)");

                // Update schema version
                setSchemaVersion(SCHEMA_VERSION);
            }

            log.info("Schema upgrade complete");
        }
    }

    /**
     * Gets the current schema version from the meta table.
     */
    private int getSchemaVersion() {
        try (Statement stmt = connection.createStatement()) {
            // Check if meta table exists
            ResultSet rs = stmt.executeQuery(
                    """
                    SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
                    WHERE TABLE_NAME = 'META'
                    """);
            if (rs.next() && rs.getInt(1) == 0) {
                return 0;
            }

            rs = stmt.executeQuery("SELECT \"value\" FROM meta WHERE \"key\" = 'schema_version'");
            if (rs.next()) {
                return Integer.parseInt(rs.getString("value"));
            }
        } catch (SQLException e) {
            log.warn("Could not read schema version: {}", e.getMessage());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Corrupted schema_version in meta table: " + e.getMessage(), e);
        }
        return 0;
    }

    /**
     * Sets the schema version in the meta table.
     */
    private void setSchemaVersion(int version) throws SQLException {
        try (var stmt = connection.prepareStatement("MERGE INTO meta (\"key\", \"value\") VALUES (?, ?)")) {
            stmt.setString(1, "schema_version");
            stmt.setString(2, String.valueOf(version));
            stmt.executeUpdate();
        }
    }

    /**
     * Sets a metadata value.
     */
    public void setMeta(String key, String value) throws SQLException {
        try (var stmt = connection.prepareStatement("MERGE INTO meta (\"key\", \"value\") VALUES (?, ?)")) {
            stmt.setString(1, key);
            stmt.setString(2, value);
            stmt.executeUpdate();
        }
    }

    /**
     * Gets a metadata value.
     */
    public String getMeta(String key) throws SQLException {
        try (var stmt = connection.prepareStatement("SELECT \"value\" FROM meta WHERE \"key\" = ?")) {
            stmt.setString(1, key);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("value");
            }
        }
        return null;
    }

    /**
     * Clears all issues and progress.
     */
    public void clearIssues() throws SQLException {
        log.info("Clearing issues and progress");

        try (Statement stmt = connection.createStatement()) {
            stmt.execute("TRUNCATE TABLE issues");
            stmt.execute("TRUNCATE TABLE progress");
        }
    }

    @Override
    public void close() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            log.info("Closing database connection");
            connection.close();
        }
    }
}

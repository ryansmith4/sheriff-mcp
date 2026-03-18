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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.guidedbyte.sheriff.model.state.IssueStatus;
import com.guidedbyte.sheriff.model.state.ProgressState;
import com.guidedbyte.sheriff.model.state.Scope;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Repository for progress tracking.
 */
public class ProgressRepository {

    private static final Logger log = LoggerFactory.getLogger(ProgressRepository.class);

    private final Connection connection;

    public ProgressRepository(DatabaseService db) {
        this.connection = db.getConnection();
    }

    /**
     * Marks issues as having a specific status.
     *
     * @param fingerprints list of issue fingerprints
     * @param status the status to set
     * @return number of issues updated
     */
    public int markStatus(List<String> fingerprints, IssueStatus status) throws SQLException {
        if (fingerprints == null || fingerprints.isEmpty()) {
            return 0;
        }

        String sql =
                """
                MERGE INTO progress (fp, status, ts, note)
                VALUES (?, ?, ?, ?)
                """;

        int updated = 0;
        connection.setAutoCommit(false);

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            for (String fp : fingerprints) {
                stmt.setString(1, fp);
                stmt.setString(2, String.valueOf(status.getCode()));
                stmt.setTimestamp(3, new Timestamp(System.currentTimeMillis()));
                stmt.setString(4, null);
                stmt.addBatch();
            }
            int[] results = stmt.executeBatch();
            for (int r : results) {
                if (r > 0) {
                    updated += r;
                } else if (r == Statement.SUCCESS_NO_INFO) {
                    updated++;
                }
            }
            connection.commit();
            log.info("Marked {} issues as {}", updated, status);
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException ex) {
                throw new IllegalStateException("Failed to restore autoCommit; connection may be unusable", ex);
            }
        }

        return updated;
    }

    /**
     * Gets the progress state for a fingerprint.
     */
    public ProgressState getProgress(String fingerprint) throws SQLException {
        try (var stmt = connection.prepareStatement("SELECT * FROM progress WHERE fp = ?")) {
            stmt.setString(1, fingerprint);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return mapProgress(rs);
            }
        }
        return null;
    }

    /**
     * Gets progress counts (fixed, skipped) overall.
     */
    public Map<String, Integer> getProgressCounts() throws SQLException {
        Map<String, Integer> counts = new HashMap<>();
        counts.put("fixed", 0);
        counts.put("skip", 0);

        String sql =
                """
                SELECT status, COUNT(*) as cnt
                FROM progress
                GROUP BY status
                """;

        try (var stmt = connection.createStatement()) {
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                String status = rs.getString("status");
                int count = rs.getInt("cnt");
                if ("F".equals(status)) {
                    counts.put("fixed", count);
                } else if ("S".equals(status)) {
                    counts.put("skip", count);
                }
            }
        }

        return counts;
    }

    /**
     * Gets progress counts within a scope.
     */
    public Map<String, Integer> getProgressCountsInScope(Scope scope) throws SQLException {
        Map<String, Integer> counts = new HashMap<>();
        counts.put("fixed", 0);
        counts.put("skip", 0);

        if (scope == null || scope.isEmpty()) {
            return getProgressCounts();
        }

        StringBuilder sql = new StringBuilder();
        sql.append(
                """
                SELECT p.status, COUNT(*) as cnt
                FROM progress p
                JOIN issues i ON p.fp = i.fp
                WHERE 1=1
                """);

        List<Object> params = new ArrayList<>();
        appendScopeConditions(sql, params, scope);
        sql.append(" GROUP BY p.status");

        try (PreparedStatement stmt = connection.prepareStatement(sql.toString())) {
            setParameters(stmt, params);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String status = rs.getString("status");
                int count = rs.getInt("cnt");
                if ("F".equals(status)) {
                    counts.put("fixed", count);
                } else if ("S".equals(status)) {
                    counts.put("skip", count);
                }
            }
        }

        return counts;
    }

    /**
     * Gets count of completed files within a scope.
     * A file is complete when all its issues (in scope) have been fixed or skipped.
     */
    public int getCompletedFileCount(Scope scope) throws SQLException {
        StringBuilder sql = new StringBuilder();
        sql.append(
                """
                SELECT COUNT(DISTINCT i.file) as cnt
                FROM issues i
                WHERE 1=1
                """);

        List<Object> params = new ArrayList<>();
        // Apply scope to outer query so only relevant files are considered
        appendScopeConditions(sql, params, scope);

        sql.append(
                """
                 AND NOT EXISTS (
                    SELECT 1 FROM issues i2
                    LEFT JOIN progress p ON i2.fp = p.fp
                    WHERE i2.file = i.file
                    AND (p.status IS NULL OR p.status NOT IN ('F', 'S'))
                """);

        // Apply same scope to inner subquery
        appendScopeConditions(sql, params, scope, "i2");
        sql.append(")");

        try (PreparedStatement stmt = connection.prepareStatement(sql.toString())) {
            setParameters(stmt, params);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("cnt");
            }
        }
        return 0;
    }

    /**
     * Reopens issues by removing their progress entries.
     *
     * @param fingerprints list of issue fingerprints to reopen
     * @return number of issues reopened
     */
    public int reopen(List<String> fingerprints) throws SQLException {
        if (fingerprints == null || fingerprints.isEmpty()) {
            return 0;
        }

        String sql = "DELETE FROM progress WHERE fp = ?";
        int deleted = 0;

        connection.setAutoCommit(false);
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            for (String fp : fingerprints) {
                stmt.setString(1, fp);
                deleted += stmt.executeUpdate();
            }
            connection.commit();
            log.info("Reopened {} issues", deleted);
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException ex) {
                throw new IllegalStateException("Failed to restore autoCommit; connection may be unusable", ex);
            }
        }

        return deleted;
    }

    /**
     * Checks if a fingerprint has progress (is marked as fixed or skipped).
     */
    public boolean hasProgress(String fingerprint) throws SQLException {
        try (var stmt = connection.prepareStatement("SELECT 1 FROM progress WHERE fp = ?")) {
            stmt.setString(1, fingerprint);
            return stmt.executeQuery().next();
        }
    }

    /**
     * Clears all progress data.
     */
    public void clear() throws SQLException {
        try (var stmt = connection.createStatement()) {
            stmt.execute("TRUNCATE TABLE progress");
        }
        log.info("Cleared progress data");
    }

    private void appendScopeConditions(StringBuilder sql, List<Object> params, Scope scope) {
        appendScopeConditions(sql, params, scope, "i");
    }

    private void appendScopeConditions(StringBuilder sql, List<Object> params, Scope scope, String alias) {
        if (scope == null || scope.isEmpty()) {
            return;
        }

        if (scope.rule() != null) {
            if (scope.rule().contains("*")) {
                sql.append(" AND ").append(alias).append(".rule LIKE ?");
                params.add(scope.rule().replace("**", "%").replace("*", "%"));
            } else {
                sql.append(" AND ").append(alias).append(".rule = ?");
                params.add(scope.rule());
            }
        }

        if (scope.getSeverityCode() != null) {
            sql.append(" AND ").append(alias).append(".sev = ?");
            params.add(scope.getSeverityCode());
        }

        if (scope.file() != null) {
            String escaped = scope.file().replace("%", "\\%").replace("_", "\\_");
            if (scope.file().contains("*")) {
                sql.append(" AND ").append(alias).append(".file LIKE ? ESCAPE '\\'");
                params.add(escaped.replace("**", "%").replace("*", "%"));
            } else {
                sql.append(" AND ").append(alias).append(".file LIKE ? ESCAPE '\\'");
                params.add("%" + escaped + "%");
            }
        }
    }

    private void setParameters(PreparedStatement stmt, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            stmt.setObject(i + 1, params.get(i));
        }
    }

    private ProgressState mapProgress(ResultSet rs) throws SQLException {
        String status = rs.getString("status");
        Timestamp ts = rs.getTimestamp("ts");
        return new ProgressState(
                rs.getString("fp"),
                status != null ? IssueStatus.fromCode(status.charAt(0)) : null,
                ts != null ? ts.toInstant() : null,
                rs.getString("note"));
    }
}

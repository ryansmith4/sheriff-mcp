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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.guidedbyte.sheriff.model.state.Scope;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Repository for issue data.
 */
public class IssueRepository {

    private static final Logger log = LoggerFactory.getLogger(IssueRepository.class);

    private final Connection connection;

    public IssueRepository(DatabaseService db) {
        this.connection = db.getConnection();
    }

    /**
     * Represents an issue from the database.
     */
    public record Issue(
            String fp,
            String rule,
            String file,
            Integer line,
            Integer col,
            String msg,
            String sev,
            String snip,
            String ctx) {}

    /**
     * Inserts an issue into the database.
     */
    public void insert(Issue issue) throws SQLException {
        String sql =
                """
                INSERT INTO issues (fp, rule, file, line, col, msg, sev, snip, ctx)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, issue.fp());
            stmt.setString(2, issue.rule());
            stmt.setString(3, issue.file());
            stmt.setObject(4, issue.line());
            stmt.setObject(5, issue.col());
            stmt.setString(6, issue.msg());
            stmt.setString(7, issue.sev());
            stmt.setString(8, issue.snip());
            stmt.setString(9, issue.ctx());
            stmt.executeUpdate();
        }
    }

    /**
     * Batch inserts issues.
     */
    public void insertBatch(List<Issue> issues) throws SQLException {
        String sql =
                """
                INSERT INTO issues (fp, rule, file, line, col, msg, sev, snip, ctx)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        connection.setAutoCommit(false);
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            for (Issue issue : issues) {
                stmt.setString(1, issue.fp());
                stmt.setString(2, issue.rule());
                stmt.setString(3, issue.file());
                stmt.setObject(4, issue.line());
                stmt.setObject(5, issue.col());
                stmt.setString(6, issue.msg());
                stmt.setString(7, issue.sev());
                stmt.setString(8, issue.snip());
                stmt.setString(9, issue.ctx());
                stmt.addBatch();
            }
            stmt.executeBatch();
            connection.commit();
            log.info("Inserted {} issues", issues.size());
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
    }

    /**
     * Gets total issue count.
     */
    public int getTotal() throws SQLException {
        try (var stmt = connection.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM issues");
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    /**
     * Gets issue counts by severity.
     */
    public Map<String, Integer> getSeverityCounts() throws SQLException {
        Map<String, Integer> counts = new LinkedHashMap<>();
        String sql = "SELECT sev, COUNT(*) as cnt FROM issues GROUP BY sev ORDER BY sev";

        try (var stmt = connection.createStatement()) {
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                String sev = rs.getString("sev");
                if (sev != null) {
                    counts.put(sev, rs.getInt("cnt"));
                }
            }
        }
        return counts;
    }

    /**
     * Gets issue counts by rule.
     */
    public Map<String, Integer> getRuleCounts() throws SQLException {
        Map<String, Integer> counts = new LinkedHashMap<>();
        String sql = "SELECT rule, COUNT(*) as cnt FROM issues GROUP BY rule ORDER BY cnt DESC";

        try (var stmt = connection.createStatement()) {
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                counts.put(rs.getString("rule"), rs.getInt("cnt"));
            }
        }
        return counts;
    }

    /**
     * Gets count of unique files.
     */
    public int getFileCount() throws SQLException {
        try (var stmt = connection.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT COUNT(DISTINCT file) FROM issues");
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    /**
     * Gets pending issues for a scope, grouped by file.
     * Returns all issues for the next file that has pending issues.
     */
    public List<Issue> getNextBatch(Scope scope) throws SQLException {
        return getNextBatch(scope, null);
    }

    /**
     * Gets pending issues for a scope, grouped by file, with optional limit.
     *
     * @param scope filter scope (optional)
     * @param limit maximum number of issues to return (optional, null for all in file)
     * @return list of issues for the next file
     */
    public List<Issue> getNextBatch(Scope scope, Integer limit) throws SQLException {
        // First, find the next file with pending issues
        String nextFile = findNextFileWithPendingIssues(scope);
        if (nextFile == null) {
            return List.of();
        }

        // Then get all pending issues for that file (optionally limited)
        return getPendingIssuesForFile(nextFile, scope, limit);
    }

    /**
     * Finds the next file that has pending issues matching the scope.
     */
    private String findNextFileWithPendingIssues(Scope scope) throws SQLException {
        StringBuilder sql = new StringBuilder();
        sql.append(
                """
                SELECT i.file FROM issues i
                LEFT JOIN progress p ON i.fp = p.fp
                WHERE (p.status IS NULL OR p.status NOT IN ('F', 'S'))
                """);

        List<Object> params = new ArrayList<>();
        appendScopeConditions(sql, params, scope);
        sql.append(" ORDER BY i.file LIMIT 1");

        try (PreparedStatement stmt = connection.prepareStatement(sql.toString())) {
            setParameters(stmt, params);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("file");
            }
        }
        return null;
    }

    /**
     * Gets pending issues for a specific file with optional limit.
     */
    private List<Issue> getPendingIssuesForFile(String file, Scope scope, Integer limit) throws SQLException {
        StringBuilder sql = new StringBuilder();
        sql.append(
                """
                SELECT i.* FROM issues i
                LEFT JOIN progress p ON i.fp = p.fp
                WHERE i.file = ?
                AND (p.status IS NULL OR p.status NOT IN ('F', 'S'))
                """);

        List<Object> params = new ArrayList<>();
        params.add(file);

        // Only apply rule and severity filters (file is already matched)
        if (scope != null && scope.rule() != null) {
            if (scope.rule().contains("*")) {
                sql.append(" AND i.rule LIKE ?");
                params.add(scope.rule().replace("**", "%").replace("*", "%"));
            } else {
                sql.append(" AND i.rule = ?");
                params.add(scope.rule());
            }
        }
        if (scope != null && scope.getSeverityCode() != null) {
            sql.append(" AND i.sev = ?");
            params.add(scope.getSeverityCode());
        }

        sql.append(" ORDER BY i.line");

        if (limit != null && limit > 0) {
            sql.append(" LIMIT ?");
            params.add(limit);
        }

        List<Issue> issues = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(sql.toString())) {
            setParameters(stmt, params);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                issues.add(mapIssue(rs));
            }
        }
        return issues;
    }

    /**
     * Gets counts of pending issues within a scope.
     */
    public Map<String, Integer> getPendingCounts(Scope scope) throws SQLException {
        Map<String, Integer> counts = new HashMap<>();

        StringBuilder sql = new StringBuilder();
        sql.append(
                """
                SELECT COUNT(*) as total,
                       COUNT(DISTINCT i.file) as files
                FROM issues i
                LEFT JOIN progress p ON i.fp = p.fp
                WHERE (p.status IS NULL OR p.status NOT IN ('F', 'S'))
                """);

        List<Object> params = new ArrayList<>();
        appendScopeConditions(sql, params, scope);

        try (PreparedStatement stmt = connection.prepareStatement(sql.toString())) {
            setParameters(stmt, params);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                counts.put("total", rs.getInt("total"));
                counts.put("files", rs.getInt("files"));
            }
        }
        return counts;
    }

    /**
     * Gets total counts within a scope.
     */
    public int getTotalInScope(Scope scope) throws SQLException {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT COUNT(*) FROM issues i WHERE 1=1");

        List<Object> params = new ArrayList<>();
        appendScopeConditions(sql, params, scope);

        try (PreparedStatement stmt = connection.prepareStatement(sql.toString())) {
            setParameters(stmt, params);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    /**
     * Checks if a fingerprint exists.
     */
    public boolean exists(String fingerprint) throws SQLException {
        try (var stmt = connection.prepareStatement("SELECT 1 FROM issues WHERE fp = ?")) {
            stmt.setString(1, fingerprint);
            return stmt.executeQuery().next();
        }
    }

    /**
     * Gets breakdown by rule with progress status.
     * Returns total, fixed, skipped, remaining counts per rule.
     */
    public List<Map<String, Object>> getBreakdownByRule() throws SQLException {
        String sql =
                """
                SELECT i.rule,
                       COUNT(*) as total,
                       SUM(CASE WHEN p.status = 'F' THEN 1 ELSE 0 END) as fixed,
                       SUM(CASE WHEN p.status = 'S' THEN 1 ELSE 0 END) as skip
                FROM issues i
                LEFT JOIN progress p ON i.fp = p.fp
                GROUP BY i.rule
                ORDER BY COUNT(*) DESC
                """;

        List<Map<String, Object>> results = new ArrayList<>();
        try (var stmt = connection.createStatement()) {
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("rule", rs.getString("rule"));
                row.put("total", rs.getInt("total"));
                row.put("fixed", rs.getInt("fixed"));
                row.put("skip", rs.getInt("skip"));
                results.add(row);
            }
        }
        return results;
    }

    /**
     * Gets breakdown by severity with progress status.
     */
    public List<Map<String, Object>> getBreakdownBySeverity() throws SQLException {
        String sql =
                """
                SELECT i.sev,
                       COUNT(*) as total,
                       SUM(CASE WHEN p.status = 'F' THEN 1 ELSE 0 END) as fixed,
                       SUM(CASE WHEN p.status = 'S' THEN 1 ELSE 0 END) as skip
                FROM issues i
                LEFT JOIN progress p ON i.fp = p.fp
                GROUP BY i.sev
                ORDER BY i.sev
                """;

        List<Map<String, Object>> results = new ArrayList<>();
        try (var stmt = connection.createStatement()) {
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("sev", rs.getString("sev"));
                row.put("total", rs.getInt("total"));
                row.put("fixed", rs.getInt("fixed"));
                row.put("skip", rs.getInt("skip"));
                results.add(row);
            }
        }
        return results;
    }

    /**
     * Gets all remaining (unfixed/unskipped) issues with optional scope filter.
     */
    public List<Issue> getRemainingIssues(Scope scope) throws SQLException {
        StringBuilder sql = new StringBuilder();
        sql.append(
                """
                SELECT i.* FROM issues i
                LEFT JOIN progress p ON i.fp = p.fp
                WHERE (p.status IS NULL OR p.status NOT IN ('F', 'S'))
                """);

        List<Object> params = new ArrayList<>();
        appendScopeConditions(sql, params, scope);
        sql.append(" ORDER BY i.file, i.line");

        List<Issue> issues = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(sql.toString())) {
            setParameters(stmt, params);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                issues.add(mapIssue(rs));
            }
        }
        return issues;
    }

    /**
     * Gets breakdown by file with progress status.
     * Limited to top 20 files by remaining count.
     */
    public List<Map<String, Object>> getBreakdownByFile() throws SQLException {
        String sql =
                """
                SELECT i.file,
                       COUNT(*) as total,
                       SUM(CASE WHEN p.status = 'F' THEN 1 ELSE 0 END) as fixed,
                       SUM(CASE WHEN p.status = 'S' THEN 1 ELSE 0 END) as skip,
                       COUNT(*) - SUM(CASE WHEN p.status IN ('F', 'S') THEN 1 ELSE 0 END) as remaining
                FROM issues i
                LEFT JOIN progress p ON i.fp = p.fp
                GROUP BY i.file
                ORDER BY remaining DESC, total DESC
                LIMIT 20
                """;

        List<Map<String, Object>> results = new ArrayList<>();
        try (var stmt = connection.createStatement()) {
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("file", rs.getString("file"));
                row.put("total", rs.getInt("total"));
                row.put("fixed", rs.getInt("fixed"));
                row.put("skip", rs.getInt("skip"));
                results.add(row);
            }
        }
        return results;
    }

    private void appendScopeConditions(StringBuilder sql, List<Object> params, Scope scope) {
        if (scope == null || scope.isEmpty()) {
            return;
        }

        if (scope.rule() != null) {
            if (scope.rule().contains("*")) {
                sql.append(" AND i.rule LIKE ?");
                params.add(scope.rule().replace("**", "%").replace("*", "%"));
            } else {
                sql.append(" AND i.rule = ?");
                params.add(scope.rule());
            }
        }

        if (scope.getSeverityCode() != null) {
            sql.append(" AND i.sev = ?");
            params.add(scope.getSeverityCode());
        }

        if (scope.file() != null) {
            // Escape SQL LIKE special characters before converting glob wildcards
            String escaped = scope.file().replace("%", "\\%").replace("_", "\\_");
            if (scope.file().contains("*")) {
                sql.append(" AND i.file LIKE ? ESCAPE '\\'");
                params.add(escaped.replace("**", "%").replace("*", "%"));
            } else {
                sql.append(" AND i.file LIKE ? ESCAPE '\\'");
                params.add("%" + escaped + "%");
            }
        }
    }

    private void setParameters(PreparedStatement stmt, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            stmt.setObject(i + 1, params.get(i));
        }
    }

    private Issue mapIssue(ResultSet rs) throws SQLException {
        return new Issue(
                rs.getString("fp"),
                rs.getString("rule"),
                rs.getString("file"),
                rs.getObject("line", Integer.class),
                rs.getObject("col", Integer.class),
                rs.getString("msg"),
                rs.getString("sev"),
                rs.getString("snip"),
                rs.getString("ctx"));
    }
}

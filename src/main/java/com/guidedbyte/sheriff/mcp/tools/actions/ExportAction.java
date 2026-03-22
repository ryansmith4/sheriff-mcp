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
package com.guidedbyte.sheriff.mcp.tools.actions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.guidedbyte.sheriff.model.response.ErrorResponse;
import com.guidedbyte.sheriff.model.response.ExportResponse;
import com.guidedbyte.sheriff.model.state.Scope;
import com.guidedbyte.sheriff.service.DatabaseService;
import com.guidedbyte.sheriff.service.IssueRepository;
import com.guidedbyte.sheriff.util.SeverityUtil;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles the 'export' action - exports remaining issues to a file.
 */
public class ExportAction {

    private static final Logger log = LoggerFactory.getLogger(ExportAction.class);

    private final DatabaseService db;
    private final IssueRepository issueRepo;
    private final ObjectMapper mapper;

    public ExportAction(DatabaseService db, IssueRepository issueRepo) {
        this.db = db;
        this.issueRepo = issueRepo;
        this.mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * Executes the export action.
     *
     * @param scope optional filter scope
     * @param outputPath optional output file path (default: sheriff-export-{timestamp}.json)
     * @param format output format: "json" (default) or "list"
     * @return ExportResponse or ErrorResponse
     */
    public Object execute(Scope scope, String outputPath, String format) {
        log.info("Exporting remaining issues with scope: {}, format: {}", scope, format);

        try {
            // Check if SARIF is loaded
            String sarifPath = db.getMeta("sarif_path");
            if (sarifPath == null) {
                return new ErrorResponse("SARIF_NOT_LOADED", "No SARIF file loaded. Call 'load' first.");
            }

            // Validate scope
            if (scope != null && scope.severity() != null && scope.getSeverityCode() == null) {
                return new ErrorResponse("INVALID_SCOPE", "Invalid scope filter");
            }

            // Get remaining issues
            List<IssueRepository.Issue> issues = issueRepo.getRemainingIssues(scope);

            if (issues.isEmpty()) {
                return new ErrorResponse("NO_ISSUES", "No remaining issues to export");
            }

            // Determine output path
            String actualPath = outputPath;
            if (actualPath == null || actualPath.isBlank()) {
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
                actualPath = "sheriff-export-" + timestamp + ".json";
            }

            // Validate and sanitize the output path to prevent directory traversal
            Path path = validateOutputPath(actualPath);
            if (path == null) {
                return new ErrorResponse(
                        "INVALID_PATH",
                        "Output path must be a simple filename or relative path within current directory");
            }

            // Determine format
            String actualFormat = (format == null || format.isBlank()) ? "json" : format.toLowerCase();

            // Export based on format
            String content;
            switch (actualFormat) {
                case "list" -> content = exportAsList(issues);
                case "json" -> content = exportAsJson(issues, sarifPath);
                default -> {
                    return new ErrorResponse("INVALID_FORMAT", "Format must be 'json' or 'list'");
                }
            }

            // Write to file
            Files.writeString(path, content);

            log.info("Exported {} issues to {}", issues.size(), path.toAbsolutePath());

            ExportResponse.ScopeInfo scopeInfo = null;
            if (scope != null && !scope.isEmpty()) {
                scopeInfo = new ExportResponse.ScopeInfo(scope.rule(), scope.severity(), scope.file());
            }

            return new ExportResponse(path.toAbsolutePath().toString(), actualFormat, issues.size(), scopeInfo);

        } catch (SQLException e) {
            log.error("Database error: {}", e.getMessage(), e);
            return new ErrorResponse("DB_ERROR", "Database error occurred");
        } catch (IOException e) {
            log.error("File write error: {}", e.getMessage(), e);
            return new ErrorResponse("IO_ERROR", "Failed to write export file");
        }
    }

    /**
     * Validates and sanitizes the output path to prevent directory traversal attacks.
     * Returns null if the path is invalid.
     */
    private Path validateOutputPath(String outputPath) {
        try {
            Path requestedPath = Path.of(outputPath);

            // Get current working directory
            Path cwd = Path.of("").toAbsolutePath();

            // Normalize the path to resolve any .. or . components
            Path normalizedPath = cwd.resolve(requestedPath).normalize();

            // Ensure the normalized path is still within the current working directory
            if (!normalizedPath.startsWith(cwd)) {
                log.warn("Rejected path traversal attempt: {} resolved to {}", outputPath, normalizedPath);
                return null;
            }

            // Check for suspicious patterns in the original input
            if (outputPath.contains("..") || outputPath.startsWith("/") || outputPath.startsWith("\\")) {
                log.warn("Rejected suspicious path pattern: {}", outputPath);
                return null;
            }

            // Check for absolute paths (Unix / or Windows C:\ style)
            if (requestedPath.isAbsolute() || outputPath.matches("^[a-zA-Z]:.*")) {
                log.warn("Rejected absolute path: {}", outputPath);
                return null;
            }

            return normalizedPath;
        } catch (Exception e) {
            log.warn("Invalid path: {}", outputPath, e);
            return null;
        }
    }

    /**
     * Exports issues as a simple list (one line per issue).
     */
    private String exportAsList(List<IssueRepository.Issue> issues) {
        StringBuilder sb = new StringBuilder();
        for (IssueRepository.Issue issue : issues) {
            sb.append(issue.file());
            if (issue.line() != null) {
                sb.append(":").append(issue.line());
                if (issue.col() != null) {
                    sb.append(":").append(issue.col());
                }
            }
            sb.append(" [")
                    .append(issue.rule())
                    .append("] ")
                    .append(issue.msg())
                    .append("\n");
        }
        return sb.toString();
    }

    /**
     * Exports issues as structured JSON.
     */
    private String exportAsJson(List<IssueRepository.Issue> issues, String sarifPath) throws IOException {
        Map<String, Object> export = new LinkedHashMap<>();
        export.put("source", sarifPath);
        export.put("exportedAt", LocalDateTime.now().toString());
        export.put("count", issues.size());

        List<Map<String, Object>> issueList = new ArrayList<>();
        for (IssueRepository.Issue issue : issues) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("fingerprint", issue.fp());
            item.put("rule", issue.rule());
            item.put("file", issue.file());
            if (issue.line() != null) {
                item.put("line", issue.line());
            }
            if (issue.col() != null) {
                item.put("column", issue.col());
            }
            item.put("message", issue.msg());
            item.put("severity", mapSeverityCode(issue.sev()));
            if (issue.snip() != null) {
                item.put("snippet", issue.snip());
            }
            issueList.add(item);
        }

        export.put("issues", issueList);

        return mapper.writeValueAsString(export);
    }

    private String mapSeverityCode(String code) {
        return SeverityUtil.mapSeverityCode(code);
    }
}

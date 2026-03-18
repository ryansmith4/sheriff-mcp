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

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.guidedbyte.sheriff.model.response.ErrorResponse;
import com.guidedbyte.sheriff.model.response.LoadResponse;
import com.guidedbyte.sheriff.model.sarif.SarifReport;
import com.guidedbyte.sheriff.service.BatchService;
import com.guidedbyte.sheriff.service.DatabaseService;
import com.guidedbyte.sheriff.service.IssueRepository;
import com.guidedbyte.sheriff.service.ProgressRepository;
import com.guidedbyte.sheriff.service.SarifParser;
import com.guidedbyte.sheriff.service.SarifParser.SarifParseException;
import com.guidedbyte.sheriff.util.FingerprintMatcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles the 'load' action - loads SARIF file(s) into the database.
 */
public class LoadAction {

    private static final Logger log = LoggerFactory.getLogger(LoadAction.class);

    private final SarifParser parser;
    private final DatabaseService db;
    private final IssueRepository issueRepo;
    private final ProgressRepository progressRepo;
    private final BatchService batchService;

    public LoadAction(
            SarifParser parser,
            DatabaseService db,
            IssueRepository issueRepo,
            ProgressRepository progressRepo,
            BatchService batchService) {
        this.parser = parser;
        this.db = db;
        this.issueRepo = issueRepo;
        this.progressRepo = progressRepo;
        this.batchService = batchService;
    }

    /**
     * Executes the load action.
     *
     * @param target path to SARIF file or directory
     * @return LoadResponse or ErrorResponse
     */
    public Object execute(String target) {
        log.info("Loading SARIF from: {}", sanitizePath(target));

        if (target == null || target.isBlank()) {
            return new ErrorResponse("INVALID_TARGET", "Target path is required");
        }

        try {
            // Find SARIF files
            List<Path> sarifFiles = parser.findSarifFiles(target);
            if (sarifFiles.isEmpty()) {
                return new ErrorResponse("SARIF_NOT_FOUND", "No SARIF files found at specified target path");
            }

            // Compute combined hash for change detection (all SARIF files)
            StringBuilder combinedHash = new StringBuilder();
            for (Path sf : sarifFiles) {
                combinedHash.append(parser.computeFileHash(sf.toFile()));
            }
            String newHash =
                    sarifFiles.size() == 1 ? combinedHash.toString() : FingerprintMatcher.hash(combinedHash.toString());
            String oldHash = db.getMeta("sarif_hash");

            // Check if SARIF has changed
            boolean sarifChanged = !newHash.equals(oldHash);

            // Get prior progress before potentially clearing
            Map<String, Integer> priorProgress = progressRepo.getProgressCounts();

            if (sarifChanged) {
                log.info("SARIF file changed, reloading...");

                // Parse all SARIF files first — fail before clearing existing data
                List<SarifReport> reports = new ArrayList<>();
                for (Path sarifFile : sarifFiles) {
                    reports.add(parser.parse(sarifFile.toFile()));
                }

                db.clearIssues();

                // Load all parsed reports
                int totalLoaded = 0;
                for (SarifReport report : reports) {
                    totalLoaded += batchService.loadSarifReport(report);
                }

                // Update metadata
                db.setMeta("sarif_path", target);
                db.setMeta("sarif_hash", newHash);
                db.setMeta("loaded_at", String.valueOf(System.currentTimeMillis()));

                log.info("Loaded {} issues from {} file(s)", totalLoaded, sarifFiles.size());
            } else {
                log.info("SARIF unchanged, using cached data");
            }

            // Build response
            int total = issueRepo.getTotal();
            Map<String, Integer> sevCounts = issueRepo.getSeverityCounts();
            Map<String, Integer> ruleCounts = issueRepo.getRuleCounts();
            int fileCount = issueRepo.getFileCount();

            // Limit rule counts to top 10
            Map<String, Integer> topRules = new LinkedHashMap<>();
            ruleCounts.entrySet().stream().limit(10).forEach(e -> topRules.put(e.getKey(), e.getValue()));

            LoadResponse.PriorProgress prior = new LoadResponse.PriorProgress(
                    priorProgress.getOrDefault("fixed", 0), priorProgress.getOrDefault("skip", 0));

            String filePath = sarifFiles.size() == 1 ? sarifFiles.get(0).toString() : target;

            return new LoadResponse(filePath, total, sevCounts, topRules, fileCount, prior);

        } catch (SarifParseException e) {
            log.error("Failed to parse SARIF: {}", e.getMessage(), e);
            return new ErrorResponse("SARIF_PARSE_ERROR", "Failed to parse SARIF file");
        } catch (SQLException e) {
            log.error("Database error: {}", e.getMessage(), e);
            return new ErrorResponse("DB_ERROR", "Database error occurred");
        }
    }

    private static String sanitizePath(String path) {
        if (path == null) {
            return "null";
        }
        String sanitized = path.replace("\n", "").replace("\r", "");
        return sanitized.length() > 500 ? sanitized.substring(0, 500) + "..." : sanitized;
    }
}

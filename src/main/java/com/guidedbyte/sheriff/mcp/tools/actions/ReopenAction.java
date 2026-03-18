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

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.guidedbyte.sheriff.model.response.ErrorResponse;
import com.guidedbyte.sheriff.model.response.ReopenResponse;
import com.guidedbyte.sheriff.service.DatabaseService;
import com.guidedbyte.sheriff.service.IssueRepository;
import com.guidedbyte.sheriff.service.ProgressRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles the 'reopen' action - reopens issues that were marked as fixed or skipped.
 */
public class ReopenAction {

    private static final Logger log = LoggerFactory.getLogger(ReopenAction.class);

    private final DatabaseService db;
    private final IssueRepository issueRepo;
    private final ProgressRepository progressRepo;

    public ReopenAction(DatabaseService db, IssueRepository issueRepo, ProgressRepository progressRepo) {
        this.db = db;
        this.issueRepo = issueRepo;
        this.progressRepo = progressRepo;
    }

    /**
     * Executes the reopen action.
     *
     * @param fingerprints list of issue fingerprints to reopen
     * @return ReopenResponse or ErrorResponse
     */
    public Object execute(List<String> fingerprints) {
        log.info("Reopening {} issues", fingerprints != null ? fingerprints.size() : 0);

        try {
            // Check if SARIF is loaded
            String sarifPath = db.getMeta("sarif_path");
            if (sarifPath == null) {
                return new ErrorResponse("SARIF_NOT_LOADED", "No SARIF file loaded. Call 'load' first.");
            }

            // Validate fingerprints
            if (fingerprints == null || fingerprints.isEmpty()) {
                return new ErrorResponse("INVALID_FINGERPRINT", "At least one fingerprint is required");
            }

            // Filter out null/blank entries
            List<String> cleanFingerprints = fingerprints.stream()
                    .filter(fp -> fp != null && !fp.isBlank())
                    .toList();
            if (cleanFingerprints.isEmpty()) {
                return new ErrorResponse("INVALID_FINGERPRINT", "At least one non-blank fingerprint is required");
            }

            // Verify fingerprints exist and have progress
            List<String> validFingerprints = new ArrayList<>();
            List<String> invalidFingerprints = new ArrayList<>();
            List<String> notMarkedFingerprints = new ArrayList<>();

            for (String fp : cleanFingerprints) {
                if (!issueRepo.exists(fp)) {
                    invalidFingerprints.add(fp);
                } else if (!progressRepo.hasProgress(fp)) {
                    notMarkedFingerprints.add(fp);
                } else {
                    validFingerprints.add(fp);
                }
            }

            if (!invalidFingerprints.isEmpty()) {
                log.warn("Invalid fingerprints: {} not found", invalidFingerprints.size());
            }
            if (!notMarkedFingerprints.isEmpty()) {
                log.warn("Fingerprints not marked (already open): {} items", notMarkedFingerprints.size());
            }

            if (validFingerprints.isEmpty()) {
                if (!invalidFingerprints.isEmpty()) {
                    return new ErrorResponse("INVALID_FINGERPRINT", "None of the provided fingerprints exist");
                }
                return new ErrorResponse("ALREADY_OPEN", "None of the provided issues are marked as fixed or skipped");
            }

            // Reopen issues
            int reopened = progressRepo.reopen(validFingerprints);

            // Calculate progress
            int total = issueRepo.getTotal();
            Map<String, Integer> progress = progressRepo.getProgressCounts();
            int fixed = progress.getOrDefault("fixed", 0);
            int skip = progress.getOrDefault("skip", 0);
            int remaining = total - fixed - skip;

            ReopenResponse.Progress prog = new ReopenResponse.Progress(total, fixed, skip, remaining);

            return new ReopenResponse(reopened, prog);

        } catch (SQLException e) {
            log.error("Database error: {}", e.getMessage(), e);
            return new ErrorResponse("DB_ERROR", "Database error occurred");
        }
    }
}

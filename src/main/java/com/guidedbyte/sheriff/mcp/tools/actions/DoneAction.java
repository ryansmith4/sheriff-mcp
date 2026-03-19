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

import com.guidedbyte.sheriff.model.response.DoneResponse;
import com.guidedbyte.sheriff.model.response.ErrorResponse;
import com.guidedbyte.sheriff.model.state.IssueStatus;
import com.guidedbyte.sheriff.service.DatabaseService;
import com.guidedbyte.sheriff.service.IssueRepository;
import com.guidedbyte.sheriff.service.ProgressRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles the 'done' action - marks issues as fixed or skipped.
 */
public class DoneAction {

    private static final Logger log = LoggerFactory.getLogger(DoneAction.class);

    private final DatabaseService db;
    private final IssueRepository issueRepo;
    private final ProgressRepository progressRepo;

    public DoneAction(DatabaseService db, IssueRepository issueRepo, ProgressRepository progressRepo) {
        this.db = db;
        this.issueRepo = issueRepo;
        this.progressRepo = progressRepo;
    }

    /**
     * Executes the done action.
     *
     * @param fingerprints list of issue fingerprints to mark
     * @param status status to set: "fixed"/"fix"/"f" or "skip"/"skipped"/"s"
     * @return DoneResponse or ErrorResponse
     */
    public Object execute(List<String> fingerprints, String status) {
        log.info(
                "Marking {} issues as {}",
                fingerprints != null ? fingerprints.size() : 0,
                status != null ? status.replaceAll("[\\r\\n\\t]", "") : "null");

        try {
            // Check if SARIF is loaded
            String sarifPath = db.getMeta("sarif_path");
            if (sarifPath == null) {
                return new ErrorResponse("SARIF_NOT_LOADED", "No SARIF file loaded. Call 'load' first.");
            }

            // Validate status
            IssueStatus issueStatus = parseStatus(status);
            if (issueStatus == null) {
                return new ErrorResponse("INVALID_STATUS", "Status must be 'fixed' or 'skip'");
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

            // Verify fingerprints exist
            List<String> validFingerprints = new ArrayList<>();
            List<String> invalidFingerprints = new ArrayList<>();

            for (String fp : cleanFingerprints) {
                if (issueRepo.exists(fp)) {
                    validFingerprints.add(fp);
                } else {
                    invalidFingerprints.add(fp);
                }
            }

            if (!invalidFingerprints.isEmpty()) {
                log.warn("Invalid fingerprints: {} not found", invalidFingerprints.size());
            }

            if (validFingerprints.isEmpty()) {
                return new ErrorResponse(
                        "INVALID_FINGERPRINT",
                        "None of the provided fingerprints exist. "
                                + "If the SARIF data was recently reloaded, use 'next' to get current fingerprints.");
            }

            // Mark issues
            int marked = progressRepo.markStatus(validFingerprints, issueStatus);

            // Calculate progress
            int total = issueRepo.getTotal();
            Map<String, Integer> progress = progressRepo.getProgressCounts();
            int fixed = progress.getOrDefault("fixed", 0);
            int skip = progress.getOrDefault("skip", 0);
            int remaining = total - fixed - skip;

            DoneResponse.Progress prog = new DoneResponse.Progress(total, fixed, skip, remaining);

            return new DoneResponse(marked, prog);

        } catch (SQLException e) {
            log.error("Database error: {}", e.getMessage(), e);
            return new ErrorResponse("DB_ERROR", "Database error occurred");
        }
    }

    /**
     * Parses status string to IssueStatus for done marking.
     * Returns null if the status is not a valid done status (fixed or skip).
     */
    private IssueStatus parseStatus(String status) {
        if (status == null) {
            return null;
        }
        IssueStatus parsed = IssueStatus.fromString(status);
        // Only FIXED and SKIPPED are valid for the done action; PENDING means unrecognized input
        return parsed == IssueStatus.PENDING ? null : parsed;
    }
}

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
import java.util.Map;

import com.guidedbyte.sheriff.model.response.ErrorResponse;
import com.guidedbyte.sheriff.model.response.ProgressResponse;
import com.guidedbyte.sheriff.model.state.Scope;
import com.guidedbyte.sheriff.service.DatabaseService;
import com.guidedbyte.sheriff.service.IssueRepository;
import com.guidedbyte.sheriff.service.ProgressRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles the 'progress' action - reports current session progress.
 */
public class ProgressAction {

    private static final Logger log = LoggerFactory.getLogger(ProgressAction.class);

    private final DatabaseService db;
    private final IssueRepository issueRepo;
    private final ProgressRepository progressRepo;

    public ProgressAction(DatabaseService db, IssueRepository issueRepo, ProgressRepository progressRepo) {
        this.db = db;
        this.issueRepo = issueRepo;
        this.progressRepo = progressRepo;
    }

    /**
     * Executes the progress action.
     *
     * @param scope optional filter scope
     * @return ProgressResponse or ErrorResponse
     */
    public Object execute(Scope scope) {
        log.info("Getting progress with scope: {}", scope);

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

            // Get total issues in scope
            int total;
            if (scope == null || scope.isEmpty()) {
                total = issueRepo.getTotal();
            } else {
                total = issueRepo.getTotalInScope(scope);
            }

            // Get progress counts
            Map<String, Integer> progress = progressRepo.getProgressCountsInScope(scope);
            int fixed = progress.getOrDefault("fixed", 0);
            int skip = progress.getOrDefault("skip", 0);
            int remaining = total - fixed - skip;

            // Get file counts
            Map<String, Integer> pending = issueRepo.getPendingCounts(scope);
            int filesRemaining = pending.getOrDefault("files", 0);
            int filesComplete = progressRepo.getCompletedFileCount(scope);

            return new ProgressResponse(
                    scope != null && !scope.isEmpty() ? scope : null,
                    total,
                    fixed,
                    skip,
                    remaining,
                    filesComplete,
                    filesRemaining);

        } catch (SQLException e) {
            log.error("Database error: {}", e.getMessage(), e);
            return new ErrorResponse("DB_ERROR", "Database error occurred");
        }
    }
}

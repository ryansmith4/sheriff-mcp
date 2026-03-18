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

import com.guidedbyte.sheriff.model.response.ErrorResponse;
import com.guidedbyte.sheriff.model.response.NextResponse;
import com.guidedbyte.sheriff.model.state.Scope;
import com.guidedbyte.sheriff.service.BatchService;
import com.guidedbyte.sheriff.service.DatabaseService;
import com.guidedbyte.sheriff.service.IssueRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles the 'next' action - gets the next batch of issues to work on.
 */
public class NextAction {

    private static final Logger log = LoggerFactory.getLogger(NextAction.class);

    /** Default maximum number of issues to return per batch. */
    public static final int DEFAULT_LIMIT = 25;

    private final DatabaseService db;
    private final IssueRepository issueRepo;
    private final BatchService batchService;

    public NextAction(DatabaseService db, IssueRepository issueRepo, BatchService batchService) {
        this.db = db;
        this.issueRepo = issueRepo;
        this.batchService = batchService;
    }

    /**
     * Executes the next action.
     *
     * @param scope optional filter scope
     * @param format output format: "default" or "checklist"
     * @return NextResponse or ErrorResponse
     */
    public Object execute(Scope scope, String format) {
        return execute(scope, format, null);
    }

    /**
     * Executes the next action with optional limit.
     *
     * @param scope optional filter scope
     * @param format output format: "default" or "checklist"
     * @param limit maximum number of issues to return (optional, defaults to {@link #DEFAULT_LIMIT})
     * @return NextResponse or ErrorResponse
     */
    public Object execute(Scope scope, String format, Integer limit) {
        // Validate limit before computing effective value
        if (limit != null && limit <= 0) {
            return new ErrorResponse("INVALID_LIMIT", "Limit must be a positive integer");
        }

        int effectiveLimit = (limit != null) ? limit : DEFAULT_LIMIT;
        log.info("Getting next batch with scope: {}, format: {}, limit: {}", scope, format, effectiveLimit);

        try {
            // Check if SARIF is loaded
            String sarifPath = db.getMeta("sarif_path");
            if (sarifPath == null) {
                return new ErrorResponse("SARIF_NOT_LOADED", "No SARIF file loaded. Call 'load' first.");
            }

            // Validate scope
            if (scope != null && !validateScope(scope)) {
                return new ErrorResponse("INVALID_SCOPE", "Invalid scope filter");
            }

            boolean checklistFormat = "checklist".equalsIgnoreCase(format);
            NextResponse response = batchService.getNextBatch(scope, checklistFormat, effectiveLimit);

            log.info(
                    "Returning {} issues for file: {}",
                    response.issues().size(),
                    response.file() != null ? response.file() : "(none remaining)");

            return response;

        } catch (SQLException e) {
            log.error("Database error: {}", e.getMessage(), e);
            return new ErrorResponse("DB_ERROR", "Database error occurred");
        }
    }

    /**
     * Validates a scope filter.
     */
    private boolean validateScope(Scope scope) {
        // Severity must be valid if provided
        if (scope.severity() != null && scope.getSeverityCode() == null) {
            return false;
        }
        return true;
    }
}

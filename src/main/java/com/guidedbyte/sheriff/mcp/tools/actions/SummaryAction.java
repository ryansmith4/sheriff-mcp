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
import java.util.Objects;

import com.guidedbyte.sheriff.model.response.ErrorResponse;
import com.guidedbyte.sheriff.model.response.SummaryResponse;
import com.guidedbyte.sheriff.model.response.SummaryResponse.FileBreakdown;
import com.guidedbyte.sheriff.model.response.SummaryResponse.RuleBreakdown;
import com.guidedbyte.sheriff.model.response.SummaryResponse.SeverityBreakdown;
import com.guidedbyte.sheriff.service.DatabaseService;
import com.guidedbyte.sheriff.service.IssueRepository;
import com.guidedbyte.sheriff.service.ProgressRepository;
import com.guidedbyte.sheriff.util.SeverityUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles the 'summary' action - provides breakdown by rule, severity, and file.
 */
public class SummaryAction {

    private static final Logger log = LoggerFactory.getLogger(SummaryAction.class);

    private final DatabaseService db;
    private final IssueRepository issueRepo;
    private final ProgressRepository progressRepo;

    public SummaryAction(DatabaseService db, IssueRepository issueRepo, ProgressRepository progressRepo) {
        this.db = db;
        this.issueRepo = issueRepo;
        this.progressRepo = progressRepo;
    }

    /**
     * Executes the summary action.
     *
     * @return SummaryResponse or ErrorResponse
     */
    public Object execute() {
        log.info("Getting summary breakdown");

        try {
            // Check if SARIF is loaded
            String sarifPath = db.getMeta("sarif_path");
            if (sarifPath == null) {
                return new ErrorResponse("SARIF_NOT_LOADED", "No SARIF file loaded. Call 'load' first.");
            }

            // Get overall counts
            int total = issueRepo.getTotal();
            Map<String, Integer> progressCounts = progressRepo.getProgressCounts();
            int fixed = progressCounts.getOrDefault("fixed", 0);
            int skip = progressCounts.getOrDefault("skip", 0);
            int remaining = total - fixed - skip;

            // Get breakdown by rule
            List<RuleBreakdown> byRule = new ArrayList<>();
            for (Map<String, Object> row : issueRepo.getBreakdownByRule()) {
                int ruleTotal = intVal(row, "total");
                int ruleFixed = intVal(row, "fixed");
                int ruleSkip = intVal(row, "skip");
                byRule.add(new RuleBreakdown(
                        Objects.toString(row.get("rule"), ""),
                        ruleTotal,
                        ruleFixed,
                        ruleSkip,
                        ruleTotal - ruleFixed - ruleSkip));
            }

            // Get breakdown by severity
            List<SeverityBreakdown> bySeverity = new ArrayList<>();
            for (Map<String, Object> row : issueRepo.getBreakdownBySeverity()) {
                int sevTotal = intVal(row, "total");
                int sevFixed = intVal(row, "fixed");
                int sevSkip = intVal(row, "skip");
                String sev = Objects.toString(row.get("sev"), "");
                // Convert severity code to full name for readability
                String sevName = mapSeverityCode(sev);
                bySeverity.add(
                        new SeverityBreakdown(sevName, sevTotal, sevFixed, sevSkip, sevTotal - sevFixed - sevSkip));
            }

            // Get breakdown by file (top 20 by remaining)
            List<FileBreakdown> byFile = new ArrayList<>();
            for (Map<String, Object> row : issueRepo.getBreakdownByFile()) {
                int fileTotal = intVal(row, "total");
                int fileFixed = intVal(row, "fixed");
                int fileSkip = intVal(row, "skip");
                byFile.add(new FileBreakdown(
                        Objects.toString(row.get("file"), ""),
                        fileTotal,
                        fileFixed,
                        fileSkip,
                        fileTotal - fileFixed - fileSkip));
            }

            return new SummaryResponse(total, fixed, skip, remaining, byRule, bySeverity, byFile);

        } catch (SQLException e) {
            log.error("Database error: {}", e.getMessage(), e);
            return new ErrorResponse("DB_ERROR", "Database error occurred");
        }
    }

    private static int intVal(Map<String, Object> row, String key) {
        Object val = row.get(key);
        if (val instanceof Number n) {
            return n.intValue();
        }
        return 0;
    }

    private String mapSeverityCode(String code) {
        return SeverityUtil.mapSeverityCode(code);
    }
}

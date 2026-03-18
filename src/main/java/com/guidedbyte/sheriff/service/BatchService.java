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

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.guidedbyte.sheriff.model.response.NextResponse;
import com.guidedbyte.sheriff.model.response.NextResponse.ChecklistItem;
import com.guidedbyte.sheriff.model.response.NextResponse.IssueInfo;
import com.guidedbyte.sheriff.model.response.NextResponse.Progress;
import com.guidedbyte.sheriff.model.sarif.Location;
import com.guidedbyte.sheriff.model.sarif.OriginalUriBaseIds;
import com.guidedbyte.sheriff.model.sarif.PhysicalLocation;
import com.guidedbyte.sheriff.model.sarif.Region;
import com.guidedbyte.sheriff.model.sarif.Result;
import com.guidedbyte.sheriff.model.sarif.Run;
import com.guidedbyte.sheriff.model.sarif.SarifReport;
import com.guidedbyte.sheriff.model.state.Scope;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service for intelligent batching of issues.
 */
public class BatchService {

    private static final Logger log = LoggerFactory.getLogger(BatchService.class);

    private final IssueRepository issueRepo;
    private final ProgressRepository progressRepo;

    public BatchService(IssueRepository issueRepo, ProgressRepository progressRepo) {
        this.issueRepo = issueRepo;
        this.progressRepo = progressRepo;
    }

    /**
     * Gets the next batch of issues to work on.
     *
     * @param scope filter scope (optional)
     * @param checklistFormat if true, include checklist format for agent task tools
     * @return response with issues and progress
     */
    public NextResponse getNextBatch(Scope scope, boolean checklistFormat) throws SQLException {
        return getNextBatch(scope, checklistFormat, null);
    }

    /**
     * Gets the next batch of issues to work on with optional limit.
     *
     * @param scope filter scope (optional)
     * @param checklistFormat if true, include checklist format for agent task tools
     * @param limit maximum number of issues to return (optional, null for all in file)
     * @return response with issues and progress
     */
    public NextResponse getNextBatch(Scope scope, boolean checklistFormat, Integer limit) throws SQLException {
        List<IssueRepository.Issue> issues = issueRepo.getNextBatch(scope, limit);

        if (issues.isEmpty()) {
            return createEmptyResponse(scope);
        }

        String file = issues.get(0).file();
        List<IssueInfo> issueInfos = new ArrayList<>();
        List<ChecklistItem> checklist = checklistFormat ? new ArrayList<>() : null;

        for (IssueRepository.Issue issue : issues) {
            IssueInfo info = new IssueInfo(
                    issue.fp(),
                    issue.rule(),
                    formatLocation(issue.line(), issue.col()),
                    issue.msg(),
                    issue.sev(),
                    issue.snip(),
                    parseContext(issue.ctx()));

            issueInfos.add(info);

            if (checklistFormat) {
                String task = String.format(
                        "Fix %s at line %d: %s", issue.rule(), issue.line() != null ? issue.line() : 0, issue.msg());
                checklist.add(new ChecklistItem(task, issue.fp()));
            }
        }

        Progress prog = calculateProgress(scope);

        return new NextResponse(file, issueInfos, prog, checklist);
    }

    /**
     * Loads issues from a SARIF report into the database.
     */
    public int loadSarifReport(SarifReport report) throws SQLException {
        List<IssueRepository.Issue> issues = new ArrayList<>();

        for (Run run : report.runs()) {
            OriginalUriBaseIds baseIds = run.originalUriBaseIds();

            for (Result result : run.results()) {
                IssueRepository.Issue issue = convertResult(result, run, baseIds);
                if (issue != null) {
                    issues.add(issue);
                }
            }
        }

        if (!issues.isEmpty()) {
            issueRepo.insertBatch(issues);
        }

        return issues.size();
    }

    /**
     * Converts a SARIF result to an issue.
     */
    private IssueRepository.Issue convertResult(Result result, Run run, OriginalUriBaseIds baseIds) {
        if (result.locations() == null || result.locations().isEmpty()) {
            return null;
        }

        Location loc = result.locations().get(0);
        PhysicalLocation physLoc = loc.physicalLocation();
        if (physLoc == null || physLoc.artifactLocation() == null) {
            return null;
        }

        String uri = physLoc.artifactLocation().uri();
        String uriBaseId = physLoc.artifactLocation().uriBaseId();

        // Resolve the full path
        String file = uri;
        if (baseIds != null) {
            file = baseIds.resolveUri(uri, uriBaseId);
        }

        Integer line = null;
        Integer col = null;
        String snip = null;
        String ctx = null;

        Region region = physLoc.region();
        if (region != null) {
            line = region.startLine();
            col = region.startColumn();

            if (region.snippet() != null) {
                snip = region.snippet().text();
            }
        }

        Region ctxRegion = physLoc.contextRegion();
        if (ctxRegion != null && ctxRegion.snippet() != null) {
            ctx = ctxRegion.snippet().text();
        }

        String sev = mapSeverity(result.level());
        String msg = result.message() != null ? result.message().getText() : "";
        String fp = result.getFingerprint();
        String ruleId = result.ruleId() != null ? result.ruleId() : "unknown";

        return new IssueRepository.Issue(fp, ruleId, file, line, col, msg, sev, snip, ctx);
    }

    /**
     * Maps SARIF level to severity code.
     */
    private String mapSeverity(String level) {
        if (level == null) {
            return "M";
        }
        return switch (level.toLowerCase()) {
            case "error" -> "H";
            case "warning" -> "M";
            case "note", "none" -> "L";
            default -> "M";
        };
    }

    /**
     * Formats line:col location string.
     */
    private String formatLocation(Integer line, Integer col) {
        if (line == null) {
            return null;
        }
        if (col == null) {
            return String.valueOf(line);
        }
        return line + ":" + col;
    }

    /**
     * Wraps the context string in a list, or returns null if empty.
     */
    private List<String> parseContext(String ctx) {
        if (ctx == null || ctx.isEmpty()) {
            return null;
        }
        // Context is stored as-is; return as single element
        return List.of(ctx);
    }

    /**
     * Calculates current progress.
     */
    private Progress calculateProgress(Scope scope) throws SQLException {
        Map<String, Integer> pending = issueRepo.getPendingCounts(scope);
        Map<String, Integer> progress = progressRepo.getProgressCountsInScope(scope);

        int remaining = pending.getOrDefault("total", 0);
        int remainingFiles = pending.getOrDefault("files", 0);
        int fixed = progress.getOrDefault("fixed", 0);
        int skip = progress.getOrDefault("skip", 0);

        return new Progress(remaining, remainingFiles, fixed, skip);
    }

    /**
     * Creates an empty response when no more issues.
     */
    private NextResponse createEmptyResponse(Scope scope) throws SQLException {
        Progress prog = calculateProgress(scope);
        return new NextResponse(null, List.of(), prog, null);
    }
}

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
package com.guidedbyte.sheriff.mcp.tools;

import java.util.ArrayList;
import java.util.List;

import com.guidedbyte.sheriff.mcp.tools.actions.DoneAction;
import com.guidedbyte.sheriff.mcp.tools.actions.ExportAction;
import com.guidedbyte.sheriff.mcp.tools.actions.LoadAction;
import com.guidedbyte.sheriff.mcp.tools.actions.NextAction;
import com.guidedbyte.sheriff.mcp.tools.actions.ProgressAction;
import com.guidedbyte.sheriff.mcp.tools.actions.ReopenAction;
import com.guidedbyte.sheriff.mcp.tools.actions.SummaryAction;
import com.guidedbyte.sheriff.model.response.ErrorResponse;
import com.guidedbyte.sheriff.model.state.Scope;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Unified MCP tool that dispatches to action handlers based on the 'action' parameter.
 */
public class SheriffTool {

    private static final Logger log = LoggerFactory.getLogger(SheriffTool.class);

    public static final String TOOL_NAME = "sheriff";
    public static final String TOOL_DESCRIPTION =
            """
            AI work queue manager for static analysis issues.

            Actions:
            - load: Load SARIF file(s). Args: target (path)
            - next: Get next batch of issues (default limit: 25). Args: scope (optional), fmt (default/checklist), limit (optional, default 25)
            - done: Mark issues as fixed/skipped. Args: fps (fingerprints), status (fixed/skip)
            - progress: Get session progress. Args: scope (optional)
            - reopen: Reopen issues marked as fixed/skipped. Args: fps (fingerprints)
            - summary: Get breakdown by rule/severity/file. No args.
            - export: Export remaining issues to file. Args: scope (optional), path (optional), format (json/list)

            Scope filter: {rule: "RuleId", severity: "High/Moderate/Low", file: "glob pattern"}
            """;

    private final LoadAction loadAction;
    private final NextAction nextAction;
    private final DoneAction doneAction;
    private final ProgressAction progressAction;
    private final ReopenAction reopenAction;
    private final SummaryAction summaryAction;
    private final ExportAction exportAction;
    private final ObjectMapper mapper;

    public SheriffTool(
            LoadAction loadAction,
            NextAction nextAction,
            DoneAction doneAction,
            ProgressAction progressAction,
            ReopenAction reopenAction,
            SummaryAction summaryAction,
            ExportAction exportAction) {
        this.loadAction = loadAction;
        this.nextAction = nextAction;
        this.doneAction = doneAction;
        this.progressAction = progressAction;
        this.reopenAction = reopenAction;
        this.summaryAction = summaryAction;
        this.exportAction = exportAction;
        this.mapper = new ObjectMapper();
    }

    /**
     * Executes the tool with the given arguments.
     *
     * @param arguments JSON arguments from MCP
     * @return JSON response string
     */
    public String execute(String arguments) {
        log.debug(
                "Executing sheriff tool with arguments: {}",
                arguments != null ? arguments.replace("\n", "").replace("\r", "") : "null");

        try {
            JsonNode args = mapper.readTree(arguments);
            String action = args.has("action") ? args.get("action").asText() : "";

            Object result =
                    switch (action.toLowerCase()) {
                        case "load" -> executeLoad(args);
                        case "next" -> executeNext(args);
                        case "done" -> executeDone(args);
                        case "progress" -> executeProgress(args);
                        case "reopen" -> executeReopen(args);
                        case "summary" -> executeSummary();
                        case "export" -> executeExport(args);
                        default -> new ErrorResponse(
                                "INVALID_ACTION",
                                "Unknown action: "
                                        + action.substring(0, Math.min(action.length(), 50))
                                                .replaceAll("[\\r\\n\\t]", "")
                                        + ". Valid: load, next, done, progress, reopen, summary, export");
                    };

            return mapper.writeValueAsString(result);

        } catch (JsonProcessingException e) {
            log.error("JSON processing error: {}", e.getMessage(), e);
            return toJson(new ErrorResponse("JSON_ERROR", "Failed to process JSON arguments"));
        }
    }

    private Object executeLoad(JsonNode args) {
        String target = args.has("target") ? args.get("target").asText() : null;
        return loadAction.execute(target);
    }

    private Object executeNext(JsonNode args) {
        Scope scope = parseScope(args);
        String format = args.has("fmt") ? args.get("fmt").asText() : "default";
        Integer limit = null;
        if (args.has("limit")) {
            JsonNode limitNode = args.get("limit");
            if (!limitNode.isNumber()) {
                return new ErrorResponse("INVALID_LIMIT", "Limit must be a number");
            }
            limit = limitNode.asInt();
        }
        return nextAction.execute(scope, format, limit);
    }

    private Object executeDone(JsonNode args) {
        List<String> fingerprints = parseFingerprints(args);
        String status = args.has("status") ? args.get("status").asText() : null;
        return doneAction.execute(fingerprints, status);
    }

    private Object executeProgress(JsonNode args) {
        Scope scope = parseScope(args);
        return progressAction.execute(scope);
    }

    private Object executeReopen(JsonNode args) {
        List<String> fingerprints = parseFingerprints(args);
        return reopenAction.execute(fingerprints);
    }

    private Object executeSummary() {
        return summaryAction.execute();
    }

    private Object executeExport(JsonNode args) {
        Scope scope = parseScope(args);
        String path = args.has("path") ? args.get("path").asText() : null;
        String format = args.has("format") ? args.get("format").asText() : "json";
        return exportAction.execute(scope, path, format);
    }

    /**
     * Parses scope from arguments.
     */
    private Scope parseScope(JsonNode args) {
        if (!args.has("scope")) {
            return null;
        }

        JsonNode scopeNode = args.get("scope");
        String rule = scopeNode.has("rule") ? scopeNode.get("rule").asText() : null;
        String severity = scopeNode.has("severity") ? scopeNode.get("severity").asText() : null;
        String file = scopeNode.has("file") ? scopeNode.get("file").asText() : null;

        if (rule == null && severity == null && file == null) {
            return null;
        }

        return new Scope(rule, severity, file);
    }

    /**
     * Parses fingerprints array from arguments.
     */
    private List<String> parseFingerprints(JsonNode args) {
        List<String> fps = new ArrayList<>();

        if (args.has("fps") && args.get("fps").isArray()) {
            for (JsonNode fp : args.get("fps")) {
                fps.add(fp.asText());
            }
        }

        return fps;
    }

    /**
     * Converts object to JSON string, handling errors.
     */
    private String toJson(Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{\"error\":{\"code\":\"JSON_ERROR\",\"msg\":\"Failed to serialize response\"}}";
        }
    }

    /**
     * Returns the JSON schema for the tool's input.
     */
    public static String getInputSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "action": {
                      "type": "string",
                      "enum": ["load", "next", "done", "progress", "reopen", "summary", "export"],
                      "description": "The action to perform"
                    },
                    "target": {
                      "type": "string",
                      "description": "Path to SARIF file or directory (for 'load' action)"
                    },
                    "scope": {
                      "type": "object",
                      "description": "Filter scope (for 'next', 'progress', and 'export' actions)",
                      "properties": {
                        "rule": {"type": "string", "description": "Rule ID filter (wildcards OK)"},
                        "severity": {"type": "string", "enum": ["High", "Moderate", "Low"]},
                        "file": {"type": "string", "description": "File glob pattern"}
                      }
                    },
                    "fmt": {
                      "type": "string",
                      "enum": ["default", "checklist"],
                      "description": "Output format (for 'next' action)"
                    },
                    "limit": {
                      "type": "integer",
                      "description": "Maximum number of issues to return (for 'next' action, default: 25)"
                    },
                    "fps": {
                      "type": "array",
                      "items": {"type": "string"},
                      "description": "Issue fingerprints (for 'done' and 'reopen' actions)"
                    },
                    "status": {
                      "type": "string",
                      "enum": ["fixed", "skip"],
                      "description": "Status to set (for 'done' action)"
                    },
                    "path": {
                      "type": "string",
                      "description": "Output file path (for 'export' action)"
                    },
                    "format": {
                      "type": "string",
                      "enum": ["json", "list"],
                      "description": "Export format (for 'export' action)"
                    }
                  },
                  "required": ["action"]
                }
                """;
    }
}

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
package com.guidedbyte.sheriff.model.response;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response for the 'summary' action.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SummaryResponse(
        @JsonProperty("total") int total,
        @JsonProperty("fixed") int fixed,
        @JsonProperty("skip") int skip,
        @JsonProperty("rem") int remaining,
        @JsonProperty("byRule") List<RuleBreakdown> byRule,
        @JsonProperty("bySev") List<SeverityBreakdown> bySeverity,
        @JsonProperty("byFile") List<FileBreakdown> byFile) {

    /**
     * Breakdown by rule.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RuleBreakdown(
            @JsonProperty("rule") String rule,
            @JsonProperty("total") int total,
            @JsonProperty("fixed") int fixed,
            @JsonProperty("skip") int skip,
            @JsonProperty("rem") int remaining) {}

    /**
     * Breakdown by severity.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SeverityBreakdown(
            @JsonProperty("sev") String severity,
            @JsonProperty("total") int total,
            @JsonProperty("fixed") int fixed,
            @JsonProperty("skip") int skip,
            @JsonProperty("rem") int remaining) {}

    /**
     * Breakdown by file.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FileBreakdown(
            @JsonProperty("file") String file,
            @JsonProperty("total") int total,
            @JsonProperty("fixed") int fixed,
            @JsonProperty("skip") int skip,
            @JsonProperty("rem") int remaining) {}
}

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
 * Response for the 'next' action.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NextResponse(
        @JsonProperty("file") String file,
        @JsonProperty("issues") List<IssueInfo> issues,
        @JsonProperty("prog") Progress prog,
        @JsonProperty("checklist") List<ChecklistItem> checklist) {

    /**
     * Issue information in compact format.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record IssueInfo(
            @JsonProperty("fp") String fp,
            @JsonProperty("rule") String rule,
            @JsonProperty("loc") String loc,
            @JsonProperty("msg") String msg,
            @JsonProperty("sev") String sev,
            @JsonProperty("snip") String snip,
            @JsonProperty("ctx") List<String> ctx) {}

    /**
     * Checklist item for agents with TodoList capability.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ChecklistItem(@JsonProperty("task") String task, @JsonProperty("fp") String fp) {}

    /**
     * Progress summary.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Progress(
            @JsonProperty("rem") int rem,
            @JsonProperty("remF") int remF,
            @JsonProperty("fixed") int fixed,
            @JsonProperty("skip") int skip) {}
}

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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response for the 'export' action.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExportResponse(
        @JsonProperty("path") String path,
        @JsonProperty("format") String format,
        @JsonProperty("count") int count,
        @JsonProperty("scope") ScopeInfo scope) {

    /**
     * Scope filter info (if applied).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ScopeInfo(
            @JsonProperty("rule") String rule,
            @JsonProperty("severity") String severity,
            @JsonProperty("file") String file) {}
}

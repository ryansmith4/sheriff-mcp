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

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response for the 'load' action.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LoadResponse(
        @JsonProperty("file") String file,
        @JsonProperty("total") int total,
        @JsonProperty("sev") Map<String, Integer> sev,
        @JsonProperty("rules") Map<String, Integer> rules,
        @JsonProperty("files") int files,
        @JsonProperty("prior") PriorProgress prior) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PriorProgress(@JsonProperty("fixed") int fixed, @JsonProperty("skip") int skip) {}
}

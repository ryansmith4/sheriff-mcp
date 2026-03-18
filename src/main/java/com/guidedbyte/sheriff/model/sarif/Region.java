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
package com.guidedbyte.sheriff.model.sarif;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A region within a source file.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Region(
        @JsonProperty("startLine") Integer startLine,
        @JsonProperty("startColumn") Integer startColumn,
        @JsonProperty("endLine") Integer endLine,
        @JsonProperty("endColumn") Integer endColumn,
        @JsonProperty("snippet") ArtifactContent snippet) {}

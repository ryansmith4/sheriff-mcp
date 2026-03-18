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
package com.guidedbyte.sheriff.model.state;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Scope filter for querying issues.
 * All fields are optional; null means "match all".
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Scope(
        @JsonProperty("rule") String rule,
        @JsonProperty("severity") String severity,
        @JsonProperty("file") String file) {

    private static final Logger log = LoggerFactory.getLogger(Scope.class);

    public static final Scope ALL = new Scope(null, null, null);

    public boolean isEmpty() {
        return rule == null && severity == null && file == null;
    }

    /**
     * Returns the severity as a single-character code (H/M/L).
     */
    public String getSeverityCode() {
        if (severity == null) {
            return null;
        }
        return switch (severity.toLowerCase()) {
            case "high", "error", "h" -> "H";
            case "moderate", "medium", "warning", "m" -> "M";
            case "low", "note", "info", "l" -> "L";
            default -> {
                log.warn("Unrecognized severity filter '{}'; ignoring", severity);
                yield null;
            }
        };
    }
}

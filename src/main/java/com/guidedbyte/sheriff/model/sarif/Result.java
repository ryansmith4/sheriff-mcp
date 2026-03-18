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

import java.util.List;
import java.util.Map;

import com.guidedbyte.sheriff.util.FingerprintMatcher;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A single analysis result (finding/issue).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Result(
        @JsonProperty("ruleId") String ruleId,
        @JsonProperty("ruleIndex") Integer ruleIndex,
        @JsonProperty("level") String level,
        @JsonProperty("message") Message message,
        @JsonProperty("locations") List<Location> locations,
        @JsonProperty("fingerprints") Map<String, String> fingerprints,
        @JsonProperty("partialFingerprints") Map<String, String> partialFingerprints,
        @JsonProperty("codeFlows") List<CodeFlow> codeFlows,
        @JsonProperty("relatedLocations") List<Location> relatedLocations) {

    public Result {
        if (locations == null) {
            locations = List.of();
        }
        if (codeFlows == null) {
            codeFlows = List.of();
        }
        if (relatedLocations == null) {
            relatedLocations = List.of();
        }
    }

    /**
     * Returns the primary fingerprint for this result.
     * Tries fingerprints first, then partialFingerprints.
     */
    public String getFingerprint() {
        if (fingerprints != null && !fingerprints.isEmpty()) {
            return fingerprints.entrySet().stream()
                    .min(Map.Entry.comparingByKey())
                    .get()
                    .getValue();
        }
        if (partialFingerprints != null && !partialFingerprints.isEmpty()) {
            return partialFingerprints.entrySet().stream()
                    .min(Map.Entry.comparingByKey())
                    .get()
                    .getValue();
        }
        // Generate a fallback fingerprint from rule + location
        String loc = "";
        if (!locations.isEmpty() && locations.get(0).physicalLocation() != null) {
            PhysicalLocation pl = locations.get(0).physicalLocation();
            if (pl.artifactLocation() != null && pl.artifactLocation().uri() != null) {
                loc = pl.artifactLocation().uri();
            }
            if (pl.region() != null && pl.region().startLine() != null) {
                loc += ":" + pl.region().startLine();
                if (pl.region().startColumn() != null) {
                    loc += ":" + pl.region().startColumn();
                }
            }
        }
        return FingerprintMatcher.hash((ruleId != null ? ruleId : "") + loc);
    }
}

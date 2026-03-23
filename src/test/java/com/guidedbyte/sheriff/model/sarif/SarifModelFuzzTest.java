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

import java.io.IOException;

import com.code_intelligence.jazzer.junit.FuzzTest;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Fuzz tests for SARIF model deserialization and derived operations.
 *
 * <p>Exercises the Jackson deserialization of individual SARIF model records
 * and the null-traversal chains in methods like {@link Result#getFingerprint()}.
 */
class SarifModelFuzzTest {

    private static final ObjectMapper MAPPER =
            new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /**
     * Deserializes arbitrary JSON into a {@link Result} and calls
     * {@link Result#getFingerprint()}. The fingerprint computation traverses
     * nullable chains (locations → physicalLocation → artifactLocation → uri)
     * and must never throw for any valid deserialized Result.
     */
    @FuzzTest(maxDuration = "5m")
    void fuzzResultFingerprint(byte[] data) {
        try {
            Result result = MAPPER.readValue(data, Result.class);
            result.getFingerprint();
        } catch (IOException e) {
            // Expected for malformed JSON
        }
    }

    /**
     * Deserializes arbitrary JSON into a full {@link SarifReport} and exercises
     * fingerprint generation for every result in every run.
     */
    @FuzzTest(maxDuration = "5m")
    void fuzzSarifReportDeserialization(byte[] data) {
        try {
            SarifReport report = MAPPER.readValue(data, SarifReport.class);
            for (Run run : report.runs()) {
                if (run.results() != null) {
                    for (Result result : run.results()) {
                        result.getFingerprint();
                    }
                }
            }
        } catch (IOException e) {
            // Expected for malformed JSON
        }
    }

    /**
     * Fuzzes {@link OriginalUriBaseIds#resolveUri(String, String)} with arbitrary
     * URI strings. The method handles file:// URIs, null bases, and malformed URIs.
     */
    @FuzzTest(maxDuration = "5m")
    void fuzzUriResolution(String uri, String baseUri) {
        OriginalUriBaseIds ids = new OriginalUriBaseIds();
        ids.setBase("SRCROOT", new OriginalUriBaseIds.UriBase(baseUri));

        // Should never throw
        ids.resolveUri(uri, "SRCROOT");
        ids.resolveUri(uri, null);
        ids.resolveUri(uri, "NONEXISTENT");
        ids.resolveUri(null, "SRCROOT");
    }
}

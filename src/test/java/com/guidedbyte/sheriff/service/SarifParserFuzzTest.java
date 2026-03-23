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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.code_intelligence.jazzer.junit.FuzzTest;
import org.junit.jupiter.api.io.TempDir;

/**
 * Fuzz tests for SARIF parsing to find edge cases and potential vulnerabilities
 * when processing untrusted SARIF input.
 *
 * <p>Run as regression tests normally ({@code ./gradlew test}).
 * Run as actual fuzzer with {@code JAZZER_FUZZ=1 ./gradlew test}.
 */
class SarifParserFuzzTest {

    @TempDir
    Path tempDir;

    @FuzzTest(maxDuration = "5m")
    void fuzzSarifParsing(byte[] data) throws IOException {
        Path sarifFile = tempDir.resolve("fuzz.sarif.json");
        Files.write(sarifFile, data);

        SarifParser parser = new SarifParser();
        try {
            parser.parse(sarifFile.toFile());
        } catch (SarifParser.SarifParseException e) {
            // Expected for malformed input
        }
    }

    @FuzzTest(maxDuration = "5m")
    void fuzzFindSarifFiles(byte[] data) {
        String input = new String(data);

        SarifParser parser = new SarifParser();
        try {
            parser.findSarifFiles(input);
        } catch (SarifParser.SarifParseException e) {
            // Expected for invalid paths
        }
    }
}

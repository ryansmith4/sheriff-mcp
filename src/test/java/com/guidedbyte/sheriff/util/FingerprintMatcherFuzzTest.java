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
package com.guidedbyte.sheriff.util;

import com.code_intelligence.jazzer.junit.FuzzTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fuzz tests for {@link FingerprintMatcher} hashing and validation.
 */
class FingerprintMatcherFuzzTest {

    /**
     * {@link FingerprintMatcher#hash(String)} must always return a 16-character
     * lowercase hex string regardless of input.
     */
    @FuzzTest(maxDuration = "5m")
    void fuzzHash(String input) {
        String hash = FingerprintMatcher.hash(input);
        assertThat(hash).hasSize(16).matches("[0-9a-f]{16}");
    }

    /**
     * {@link FingerprintMatcher#generateFingerprint(String, String, int, int)} must
     * produce valid fingerprints for arbitrary rule IDs and file paths.
     */
    @FuzzTest(maxDuration = "5m")
    void fuzzGenerateFingerprint(String ruleId, String file, int line, int col) {
        String fp = FingerprintMatcher.generateFingerprint(ruleId, file, line, col);
        assertThat(fp).hasSize(16).matches("[0-9a-f]{16}");
    }

    /**
     * {@link FingerprintMatcher#isValidFingerprint(String)} must never throw
     * for any input, and the result should be consistent.
     */
    @FuzzTest(maxDuration = "5m")
    void fuzzIsValidFingerprint(String input) {
        boolean valid = FingerprintMatcher.isValidFingerprint(input);
        if (valid) {
            assertThat(input).matches("[0-9a-fA-F]{8,64}");
        }
    }
}

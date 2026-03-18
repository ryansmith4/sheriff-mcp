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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FingerprintMatcherTest {

    @Test
    void generateFingerprint_shouldProduceConsistentHash() {
        String fp1 = FingerprintMatcher.generateFingerprint("Rule1", "file.java", 10, 5);
        String fp2 = FingerprintMatcher.generateFingerprint("Rule1", "file.java", 10, 5);

        assertThat(fp1).isEqualTo(fp2);
        assertThat(fp1).hasSize(16); // 8 bytes = 16 hex chars
    }

    @Test
    void generateFingerprint_shouldProduceDifferentHashesForDifferentInputs() {
        String fp1 = FingerprintMatcher.generateFingerprint("Rule1", "file.java", 10, 5);
        String fp2 = FingerprintMatcher.generateFingerprint("Rule2", "file.java", 10, 5);
        String fp3 = FingerprintMatcher.generateFingerprint("Rule1", "other.java", 10, 5);
        String fp4 = FingerprintMatcher.generateFingerprint("Rule1", "file.java", 20, 5);

        assertThat(fp1).isNotEqualTo(fp2);
        assertThat(fp1).isNotEqualTo(fp3);
        assertThat(fp1).isNotEqualTo(fp4);
    }

    @Test
    void hash_shouldProduceConsistentResult() {
        String hash1 = FingerprintMatcher.hash("test input");
        String hash2 = FingerprintMatcher.hash("test input");

        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    void hash_shouldProduceHexString() {
        String hash = FingerprintMatcher.hash("test");

        assertThat(hash).matches("[0-9a-f]+");
        assertThat(hash).hasSize(16);
    }

    @Test
    void isValidFingerprint_shouldAcceptValidHexStrings() {
        assertThat(FingerprintMatcher.isValidFingerprint("abcd1234")).isTrue();
        assertThat(FingerprintMatcher.isValidFingerprint("ABCD1234")).isTrue();
        assertThat(FingerprintMatcher.isValidFingerprint("0123456789abcdef")).isTrue();
        assertThat(FingerprintMatcher.isValidFingerprint("a".repeat(64))).isTrue();
    }

    @Test
    void isValidFingerprint_shouldRejectInvalidStrings() {
        assertThat(FingerprintMatcher.isValidFingerprint(null)).isFalse();
        assertThat(FingerprintMatcher.isValidFingerprint("")).isFalse();
        assertThat(FingerprintMatcher.isValidFingerprint("abc")).isFalse(); // Too short
        assertThat(FingerprintMatcher.isValidFingerprint("ghijklmn")).isFalse(); // Invalid chars
        assertThat(FingerprintMatcher.isValidFingerprint("a".repeat(65))).isFalse(); // Too long
    }

    @Test
    void isValidFingerprint_shouldAcceptMinimumLength() {
        assertThat(FingerprintMatcher.isValidFingerprint("12345678")).isTrue(); // 8 chars minimum
        assertThat(FingerprintMatcher.isValidFingerprint("1234567")).isFalse(); // 7 chars - too short
    }
}

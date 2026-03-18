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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GlobMatcherTest {

    @Nested
    class FilePathMatching {

        @Test
        void matches_shouldReturnTrueForNullPattern() {
            assertThat(GlobMatcher.matches(null, "src/main/java/File.java")).isTrue();
        }

        @Test
        void matches_shouldReturnTrueForEmptyPattern() {
            assertThat(GlobMatcher.matches("", "src/main/java/File.java")).isTrue();
        }

        @Test
        void matches_shouldReturnFalseForNullPath() {
            assertThat(GlobMatcher.matches("*.java", null)).isFalse();
        }

        @Test
        void matches_shouldReturnFalseForEmptyPath() {
            assertThat(GlobMatcher.matches("*.java", "")).isFalse();
        }

        @Test
        void matches_shouldMatchExactPath() {
            assertThat(GlobMatcher.matches("src/main/File.java", "src/main/File.java"))
                    .isTrue();
        }

        @Test
        void matches_shouldMatchContainsPattern() {
            assertThat(GlobMatcher.matches("main", "src/main/java/File.java")).isTrue();
        }

        @Test
        void matches_shouldMatchSingleWildcard() {
            assertThat(GlobMatcher.matches("*.java", "File.java")).isTrue();
            assertThat(GlobMatcher.matches("*.java", "File.txt")).isFalse();
        }

        @Test
        void matches_shouldMatchDoubleWildcard() {
            assertThat(GlobMatcher.matches("src/**/*.java", "src/main/java/File.java"))
                    .isTrue();
            assertThat(GlobMatcher.matches("src/**/*.java", "test/main/java/File.java"))
                    .isFalse();
        }

        @Test
        void matches_shouldNormalizePathSeparators() {
            assertThat(GlobMatcher.matches("src/main", "src\\main\\File.java")).isTrue();
            assertThat(GlobMatcher.matches("src\\main", "src/main/File.java")).isTrue();
        }
    }

    @Nested
    class RuleMatching {

        @Test
        void matchesRule_shouldReturnTrueForNullPattern() {
            assertThat(GlobMatcher.matchesRule(null, "ConstantValue")).isTrue();
        }

        @Test
        void matchesRule_shouldReturnTrueForEmptyPattern() {
            assertThat(GlobMatcher.matchesRule("", "ConstantValue")).isTrue();
        }

        @Test
        void matchesRule_shouldReturnFalseForNullRuleId() {
            assertThat(GlobMatcher.matchesRule("Constant*", null)).isFalse();
        }

        @Test
        void matchesRule_shouldMatchExactRule() {
            assertThat(GlobMatcher.matchesRule("ConstantValue", "ConstantValue"))
                    .isTrue();
            assertThat(GlobMatcher.matchesRule("ConstantValue", "OtherRule")).isFalse();
        }

        @Test
        void matchesRule_shouldMatchCaseInsensitive() {
            assertThat(GlobMatcher.matchesRule("constantvalue", "ConstantValue"))
                    .isTrue();
            assertThat(GlobMatcher.matchesRule("CONSTANTVALUE", "ConstantValue"))
                    .isTrue();
        }

        @Test
        void matchesRule_shouldMatchWildcardPrefix() {
            assertThat(GlobMatcher.matchesRule("Constant*", "ConstantValue")).isTrue();
            assertThat(GlobMatcher.matchesRule("Constant*", "ConstantCondition"))
                    .isTrue();
            assertThat(GlobMatcher.matchesRule("Constant*", "SomeOtherRule")).isFalse();
        }

        @Test
        void matchesRule_shouldMatchWildcardSuffix() {
            assertThat(GlobMatcher.matchesRule("*Value", "ConstantValue")).isTrue();
            assertThat(GlobMatcher.matchesRule("*Value", "NullValue")).isTrue();
            assertThat(GlobMatcher.matchesRule("*Value", "SomeCondition")).isFalse();
        }

        @Test
        void matchesRule_shouldMatchWildcardMiddle() {
            assertThat(GlobMatcher.matchesRule("Const*Value", "ConstantValue")).isTrue();
            assertThat(GlobMatcher.matchesRule("Const*Value", "ConstValue")).isTrue();
        }
    }
}

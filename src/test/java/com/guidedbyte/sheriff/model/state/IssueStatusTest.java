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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IssueStatusTest {

    @Test
    void getCode_shouldReturnCorrectCodes() {
        assertThat(IssueStatus.PENDING.getCode()).isEqualTo('P');
        assertThat(IssueStatus.FIXED.getCode()).isEqualTo('F');
        assertThat(IssueStatus.SKIPPED.getCode()).isEqualTo('S');
    }

    @Test
    void fromCode_shouldReturnCorrectStatus() {
        assertThat(IssueStatus.fromCode('P')).isEqualTo(IssueStatus.PENDING);
        assertThat(IssueStatus.fromCode('F')).isEqualTo(IssueStatus.FIXED);
        assertThat(IssueStatus.fromCode('S')).isEqualTo(IssueStatus.SKIPPED);
    }

    @Test
    void fromCode_shouldThrowForUnknownCode() {
        assertThatThrownBy(() -> IssueStatus.fromCode('X'))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown status code: X");
    }

    @ParameterizedTest
    @CsvSource({
        "fixed, FIXED",
        "Fixed, FIXED",
        "FIXED, FIXED",
        "f, FIXED",
        "skip, SKIPPED",
        "skipped, SKIPPED",
        "s, SKIPPED"
    })
    void fromString_shouldParseValidStrings(String input, IssueStatus expected) {
        assertThat(IssueStatus.fromString(input)).isEqualTo(expected);
    }

    @Test
    void fromString_shouldReturnPendingForNull() {
        assertThat(IssueStatus.fromString(null)).isEqualTo(IssueStatus.PENDING);
    }

    @Test
    void fromString_shouldReturnPendingForEmpty() {
        assertThat(IssueStatus.fromString("")).isEqualTo(IssueStatus.PENDING);
    }

    @Test
    void fromString_shouldReturnPendingForUnknown() {
        assertThat(IssueStatus.fromString("unknown")).isEqualTo(IssueStatus.PENDING);
        assertThat(IssueStatus.fromString("xyz")).isEqualTo(IssueStatus.PENDING);
    }

    @Test
    void values_shouldContainAllStatuses() {
        assertThat(IssueStatus.values()).containsExactly(IssueStatus.PENDING, IssueStatus.FIXED, IssueStatus.SKIPPED);
    }
}

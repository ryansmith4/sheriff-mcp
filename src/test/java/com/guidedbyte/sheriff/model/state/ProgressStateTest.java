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

import java.time.Instant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProgressStateTest {

    @Test
    void twoArgConstructor_shouldSetTimestampToNow() {
        Instant before = Instant.now();
        ProgressState state = new ProgressState("fp123", IssueStatus.FIXED);
        Instant after = Instant.now();

        assertThat(state.fingerprint()).isEqualTo("fp123");
        assertThat(state.status()).isEqualTo(IssueStatus.FIXED);
        assertThat(state.timestamp()).isBetween(before, after);
        assertThat(state.note()).isNull();
    }

    @Test
    void threeArgConstructor_shouldSetNote() {
        Instant before = Instant.now();
        ProgressState state = new ProgressState("fp456", IssueStatus.SKIPPED, "False positive");
        Instant after = Instant.now();

        assertThat(state.fingerprint()).isEqualTo("fp456");
        assertThat(state.status()).isEqualTo(IssueStatus.SKIPPED);
        assertThat(state.timestamp()).isBetween(before, after);
        assertThat(state.note()).isEqualTo("False positive");
    }

    @Test
    void fullConstructor_shouldSetAllFields() {
        Instant timestamp = Instant.parse("2026-01-15T10:30:00Z");
        ProgressState state = new ProgressState("fp789", IssueStatus.SKIPPED, timestamp, "Needs review");

        assertThat(state.fingerprint()).isEqualTo("fp789");
        assertThat(state.status()).isEqualTo(IssueStatus.SKIPPED);
        assertThat(state.timestamp()).isEqualTo(timestamp);
        assertThat(state.note()).isEqualTo("Needs review");
    }

    @Test
    void recordEquality_shouldWorkCorrectly() {
        Instant timestamp = Instant.parse("2026-01-15T10:30:00Z");
        ProgressState state1 = new ProgressState("fp123", IssueStatus.FIXED, timestamp, "Done");
        ProgressState state2 = new ProgressState("fp123", IssueStatus.FIXED, timestamp, "Done");

        assertThat(state1).isEqualTo(state2);
        assertThat(state1.hashCode()).isEqualTo(state2.hashCode());
    }
}

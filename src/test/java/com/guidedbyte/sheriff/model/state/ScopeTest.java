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

class ScopeTest {

    @Test
    void isEmpty_shouldReturnTrueForAllNullFields() {
        Scope scope = new Scope(null, null, null);
        assertThat(scope.isEmpty()).isTrue();
    }

    @Test
    void isEmpty_shouldReturnTrueForALLConstant() {
        assertThat(Scope.ALL.isEmpty()).isTrue();
    }

    @Test
    void isEmpty_shouldReturnFalseWhenRuleIsSet() {
        Scope scope = new Scope("ConstantValue", null, null);
        assertThat(scope.isEmpty()).isFalse();
    }

    @Test
    void isEmpty_shouldReturnFalseWhenSeverityIsSet() {
        Scope scope = new Scope(null, "High", null);
        assertThat(scope.isEmpty()).isFalse();
    }

    @Test
    void isEmpty_shouldReturnFalseWhenFileIsSet() {
        Scope scope = new Scope(null, null, "src/**/*.java");
        assertThat(scope.isEmpty()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
        "high, H",
        "High, H",
        "HIGH, H",
        "error, H",
        "h, H",
        "moderate, M",
        "medium, M",
        "warning, M",
        "m, M",
        "low, L",
        "note, L",
        "info, L",
        "l, L"
    })
    void getSeverityCode_shouldMapSeverityCorrectly(String severity, String expectedCode) {
        Scope scope = new Scope(null, severity, null);
        assertThat(scope.getSeverityCode()).isEqualTo(expectedCode);
    }

    @Test
    void getSeverityCode_shouldReturnNullForNullSeverity() {
        Scope scope = new Scope(null, null, null);
        assertThat(scope.getSeverityCode()).isNull();
    }

    @Test
    void getSeverityCode_shouldReturnNullForUnknownSeverity() {
        Scope scope = new Scope(null, "unknown", null);
        assertThat(scope.getSeverityCode()).isNull();
    }

    @Test
    void recordComponents_shouldBeAccessible() {
        Scope scope = new Scope("rule1", "High", "src/*.java");

        assertThat(scope.rule()).isEqualTo("rule1");
        assertThat(scope.severity()).isEqualTo("High");
        assertThat(scope.file()).isEqualTo("src/*.java");
    }
}

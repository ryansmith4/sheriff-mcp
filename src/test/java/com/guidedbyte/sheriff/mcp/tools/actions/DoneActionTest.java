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
package com.guidedbyte.sheriff.mcp.tools.actions;

import java.io.File;
import java.util.Collections;
import java.util.List;

import com.guidedbyte.sheriff.model.response.DoneResponse;
import com.guidedbyte.sheriff.model.response.ErrorResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class DoneActionTest {

    @TempDir
    File tempDir;

    private ActionTestHelper helper;
    private DoneAction action;

    @BeforeEach
    void setUp() throws Exception {
        helper = new ActionTestHelper(tempDir);
        action = new DoneAction(helper.getDb(), helper.getIssueRepo(), helper.getProgressRepo());
    }

    @AfterEach
    void tearDown() {
        if (helper != null) {
            helper.close();
        }
    }

    @Test
    void shouldReturnErrorWhenSarifNotLoaded() {
        Object result = action.execute(List.of("fp1"), "fixed");

        assertThat(result).isInstanceOf(ErrorResponse.class);
        assertThat(((ErrorResponse) result).error().code()).isEqualTo("SARIF_NOT_LOADED");
    }

    @Test
    void shouldReturnErrorWhenStatusNull() throws Exception {
        helper.loadSampleSarif();

        Object result = action.execute(List.of("fp1"), null);

        assertThat(result).isInstanceOf(ErrorResponse.class);
        assertThat(((ErrorResponse) result).error().code()).isEqualTo("INVALID_STATUS");
    }

    @Test
    void shouldReturnErrorWhenStatusInvalid() throws Exception {
        helper.loadSampleSarif();

        Object result = action.execute(List.of("fp1"), "invalid");

        assertThat(result).isInstanceOf(ErrorResponse.class);
        assertThat(((ErrorResponse) result).error().code()).isEqualTo("INVALID_STATUS");
    }

    @Test
    void shouldReturnErrorWhenFingerprintsNull() throws Exception {
        helper.loadSampleSarif();

        Object result = action.execute(null, "fixed");

        assertThat(result).isInstanceOf(ErrorResponse.class);
        assertThat(((ErrorResponse) result).error().code()).isEqualTo("INVALID_FINGERPRINT");
    }

    @Test
    void shouldReturnErrorWhenFingerprintsEmpty() throws Exception {
        helper.loadSampleSarif();

        Object result = action.execute(Collections.emptyList(), "fixed");

        assertThat(result).isInstanceOf(ErrorResponse.class);
        assertThat(((ErrorResponse) result).error().code()).isEqualTo("INVALID_FINGERPRINT");
    }

    @Test
    void shouldReturnErrorWhenAllFingerprintsInvalid() throws Exception {
        helper.loadSampleSarif();

        Object result = action.execute(List.of("nonexistent1", "nonexistent2"), "fixed");

        assertThat(result).isInstanceOf(ErrorResponse.class);
        assertThat(((ErrorResponse) result).error().code()).isEqualTo("INVALID_FINGERPRINT");
    }

    @Test
    void shouldMarkIssueAsFixed() throws Exception {
        helper.loadSampleSarif();

        var issues = helper.getIssueRepo().getRemainingIssues(null);
        assertThat(issues).isNotEmpty();
        String fp = issues.get(0).fp();

        Object result = action.execute(List.of(fp), "fixed");

        assertThat(result).isInstanceOf(DoneResponse.class);
        DoneResponse response = (DoneResponse) result;
        assertThat(response.marked()).isEqualTo(1);
        assertThat(response.prog().fixed()).isEqualTo(1);
    }

    @Test
    void shouldMarkIssueAsSkipped() throws Exception {
        helper.loadSampleSarif();

        var issues = helper.getIssueRepo().getRemainingIssues(null);
        assertThat(issues).isNotEmpty();
        String fp = issues.get(0).fp();

        Object result = action.execute(List.of(fp), "skip");

        assertThat(result).isInstanceOf(DoneResponse.class);
        DoneResponse response = (DoneResponse) result;
        assertThat(response.marked()).isEqualTo(1);
        assertThat(response.prog().skip()).isEqualTo(1);
    }

    @Test
    void shouldMarkMultipleIssues() throws Exception {
        helper.loadSampleSarif();

        var issues = helper.getIssueRepo().getRemainingIssues(null);
        assertThat(issues.size()).isGreaterThanOrEqualTo(2);
        String fp1 = issues.get(0).fp();
        String fp2 = issues.get(1).fp();

        Object result = action.execute(List.of(fp1, fp2), "fixed");

        assertThat(result).isInstanceOf(DoneResponse.class);
        DoneResponse response = (DoneResponse) result;
        assertThat(response.marked()).isEqualTo(2);
        assertThat(response.prog().fixed()).isEqualTo(2);
    }

    @Test
    void shouldSkipInvalidFingerprintsWhenSomeValid() throws Exception {
        helper.loadSampleSarif();

        var issues = helper.getIssueRepo().getRemainingIssues(null);
        assertThat(issues).isNotEmpty();
        String validFp = issues.get(0).fp();

        Object result = action.execute(List.of(validFp, "nonexistent"), "fixed");

        assertThat(result).isInstanceOf(DoneResponse.class);
        DoneResponse response = (DoneResponse) result;
        assertThat(response.marked()).isEqualTo(1);
    }

    @Test
    void shouldAcceptStatusAliases() throws Exception {
        helper.loadSampleSarif();

        var issues = helper.getIssueRepo().getRemainingIssues(null);
        assertThat(issues.size()).isGreaterThanOrEqualTo(4);

        // Test "fix" alias
        Object result1 = action.execute(List.of(issues.get(0).fp()), "fix");
        assertThat(result1).isInstanceOf(DoneResponse.class);
        assertThat(((DoneResponse) result1).prog().fixed()).isEqualTo(1);

        // Test "f" alias
        Object result2 = action.execute(List.of(issues.get(1).fp()), "f");
        assertThat(result2).isInstanceOf(DoneResponse.class);
        assertThat(((DoneResponse) result2).prog().fixed()).isEqualTo(2);

        // Test "skipped" alias
        Object result3 = action.execute(List.of(issues.get(2).fp()), "skipped");
        assertThat(result3).isInstanceOf(DoneResponse.class);
        assertThat(((DoneResponse) result3).prog().skip()).isEqualTo(1);

        // Test "s" alias
        Object result4 = action.execute(List.of(issues.get(3).fp()), "s");
        assertThat(result4).isInstanceOf(DoneResponse.class);
        assertThat(((DoneResponse) result4).prog().skip()).isEqualTo(2);
    }
}

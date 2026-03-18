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

import com.guidedbyte.sheriff.model.response.ErrorResponse;
import com.guidedbyte.sheriff.model.response.ReopenResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class ReopenActionTest {

    @TempDir
    File tempDir;

    private ActionTestHelper helper;
    private ReopenAction action;
    private DoneAction doneAction;

    @BeforeEach
    void setUp() throws Exception {
        helper = new ActionTestHelper(tempDir);
        action = new ReopenAction(helper.getDb(), helper.getIssueRepo(), helper.getProgressRepo());
        doneAction = new DoneAction(helper.getDb(), helper.getIssueRepo(), helper.getProgressRepo());
    }

    @AfterEach
    void tearDown() {
        if (helper != null) {
            helper.close();
        }
    }

    @Test
    void shouldReturnErrorWhenSarifNotLoaded() {
        Object result = action.execute(List.of("fp1"));

        assertThat(result).isInstanceOf(ErrorResponse.class);
        assertThat(((ErrorResponse) result).error().code()).isEqualTo("SARIF_NOT_LOADED");
    }

    @Test
    void shouldReturnErrorWhenFingerprintsEmpty() throws Exception {
        helper.loadSampleSarif();

        Object result = action.execute(Collections.emptyList());

        assertThat(result).isInstanceOf(ErrorResponse.class);
        assertThat(((ErrorResponse) result).error().code()).isEqualTo("INVALID_FINGERPRINT");
    }

    @Test
    void shouldReturnErrorWhenFingerprintsNull() throws Exception {
        helper.loadSampleSarif();

        Object result = action.execute(null);

        assertThat(result).isInstanceOf(ErrorResponse.class);
        assertThat(((ErrorResponse) result).error().code()).isEqualTo("INVALID_FINGERPRINT");
    }

    @Test
    void shouldReturnErrorWhenFingerprintNotFound() throws Exception {
        helper.loadSampleSarif();

        Object result = action.execute(List.of("nonexistent-fp"));

        assertThat(result).isInstanceOf(ErrorResponse.class);
        assertThat(((ErrorResponse) result).error().code()).isEqualTo("INVALID_FINGERPRINT");
    }

    @Test
    void shouldReturnErrorWhenIssueNotMarked() throws Exception {
        helper.loadSampleSarif();

        // Get a valid fingerprint from the database
        var issues = helper.getIssueRepo().getRemainingIssues(null);
        assertThat(issues).isNotEmpty();
        String fp = issues.get(0).fp();

        // Try to reopen without marking first
        Object result = action.execute(List.of(fp));

        assertThat(result).isInstanceOf(ErrorResponse.class);
        assertThat(((ErrorResponse) result).error().code()).isEqualTo("ALREADY_OPEN");
    }

    @Test
    void shouldReopenMarkedIssue() throws Exception {
        helper.loadSampleSarif();

        // Get a valid fingerprint and mark it as fixed
        var issues = helper.getIssueRepo().getRemainingIssues(null);
        assertThat(issues).isNotEmpty();
        String fp = issues.get(0).fp();

        doneAction.execute(List.of(fp), "fixed");

        // Verify it's marked
        assertThat(helper.getProgressRepo().hasProgress(fp)).isTrue();

        // Reopen it
        Object result = action.execute(List.of(fp));

        assertThat(result).isInstanceOf(ReopenResponse.class);
        ReopenResponse response = (ReopenResponse) result;
        assertThat(response.reopened()).isEqualTo(1);
        assertThat(response.prog().fixed()).isEqualTo(0);
    }

    @Test
    void shouldReopenMultipleIssues() throws Exception {
        helper.loadSampleSarif();

        // Get multiple fingerprints and mark them
        var issues = helper.getIssueRepo().getRemainingIssues(null);
        assertThat(issues.size()).isGreaterThanOrEqualTo(2);
        String fp1 = issues.get(0).fp();
        String fp2 = issues.get(1).fp();

        doneAction.execute(List.of(fp1, fp2), "fixed");

        // Reopen both
        Object result = action.execute(List.of(fp1, fp2));

        assertThat(result).isInstanceOf(ReopenResponse.class);
        ReopenResponse response = (ReopenResponse) result;
        assertThat(response.reopened()).isEqualTo(2);
    }
}

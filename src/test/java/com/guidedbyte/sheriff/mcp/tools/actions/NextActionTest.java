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

import com.guidedbyte.sheriff.model.response.ErrorResponse;
import com.guidedbyte.sheriff.model.response.NextResponse;
import com.guidedbyte.sheriff.model.state.Scope;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class NextActionTest {

    @TempDir
    File tempDir;

    private ActionTestHelper helper;
    private NextAction action;

    @BeforeEach
    void setUp() throws Exception {
        helper = new ActionTestHelper(tempDir);
        action = new NextAction(helper.getDb(), helper.getIssueRepo(), helper.getBatchService());
    }

    @AfterEach
    void tearDown() {
        if (helper != null) {
            helper.close();
        }
    }

    @Test
    void defaultLimitIsSet() {
        assertThat(NextAction.DEFAULT_LIMIT).isEqualTo(25);
    }

    @Test
    void shouldReturnErrorWhenSarifNotLoaded() {
        Object result = action.execute(null, null);

        assertThat(result).isInstanceOf(ErrorResponse.class);
        assertThat(((ErrorResponse) result).error().code()).isEqualTo("SARIF_NOT_LOADED");
    }

    @Test
    void shouldReturnNextBatch() throws Exception {
        helper.loadSampleSarif();

        Object result = action.execute(null, null);

        assertThat(result).isInstanceOf(NextResponse.class);
        NextResponse response = (NextResponse) result;
        assertThat(response.file()).isNotNull();
        assertThat(response.issues()).isNotEmpty();
        assertThat(response.prog()).isNotNull();
        assertThat(response.prog().rem()).isGreaterThan(0);
    }

    @Test
    void shouldReturnChecklistFormat() throws Exception {
        helper.loadSampleSarif();

        Object result = action.execute(null, "checklist");

        assertThat(result).isInstanceOf(NextResponse.class);
        NextResponse response = (NextResponse) result;
        assertThat(response.checklist()).isNotNull();
        assertThat(response.checklist()).isNotEmpty();
        assertThat(response.checklist().get(0).fp()).isNotNull();
        assertThat(response.checklist().get(0).task()).contains("Fix");
    }

    @Test
    void shouldRespectLimit() throws Exception {
        helper.loadSampleSarif();

        Object result = action.execute(null, null, 1);

        assertThat(result).isInstanceOf(NextResponse.class);
        NextResponse response = (NextResponse) result;
        assertThat(response.issues()).hasSize(1);
    }

    @Test
    void shouldReturnErrorForInvalidLimit() throws Exception {
        helper.loadSampleSarif();

        Object result = action.execute(null, null, -1);

        assertThat(result).isInstanceOf(ErrorResponse.class);
        assertThat(((ErrorResponse) result).error().code()).isEqualTo("INVALID_LIMIT");
    }

    @Test
    void shouldReturnErrorForZeroLimit() throws Exception {
        helper.loadSampleSarif();

        Object result = action.execute(null, null, 0);

        assertThat(result).isInstanceOf(ErrorResponse.class);
        assertThat(((ErrorResponse) result).error().code()).isEqualTo("INVALID_LIMIT");
    }

    @Test
    void shouldFilterByScope() throws Exception {
        helper.loadSampleSarif();

        // Filter by severity=high (only Utils.java has error level)
        Scope scope = new Scope(null, "high", null);
        Object result = action.execute(scope, null);

        assertThat(result).isInstanceOf(NextResponse.class);
        NextResponse response = (NextResponse) result;
        assertThat(response.issues()).isNotEmpty();
        assertThat(response.issues()).allMatch(i -> "H".equals(i.sev()));
    }

    @Test
    void shouldReturnErrorForInvalidScope() throws Exception {
        helper.loadSampleSarif();

        // Invalid severity value
        Scope scope = new Scope(null, "invalid_severity", null);
        Object result = action.execute(scope, null);

        assertThat(result).isInstanceOf(ErrorResponse.class);
        assertThat(((ErrorResponse) result).error().code()).isEqualTo("INVALID_SCOPE");
    }

    @Test
    void shouldReturnEmptyWhenAllDone() throws Exception {
        helper.loadSampleSarif();

        // Mark all issues as done
        var issues = helper.getIssueRepo().getRemainingIssues(null);
        DoneAction doneAction = new DoneAction(helper.getDb(), helper.getIssueRepo(), helper.getProgressRepo());
        for (var issue : issues) {
            doneAction.execute(java.util.List.of(issue.fp()), "fixed");
        }

        Object result = action.execute(null, null);

        assertThat(result).isInstanceOf(NextResponse.class);
        NextResponse response = (NextResponse) result;
        assertThat(response.file()).isNull();
        assertThat(response.issues()).isEmpty();
        assertThat(response.prog().rem()).isEqualTo(0);
    }
}

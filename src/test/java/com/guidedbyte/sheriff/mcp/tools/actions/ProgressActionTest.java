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
import java.util.List;

import com.guidedbyte.sheriff.model.response.ErrorResponse;
import com.guidedbyte.sheriff.model.response.ProgressResponse;
import com.guidedbyte.sheriff.model.state.Scope;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class ProgressActionTest {

    @TempDir
    File tempDir;

    private ActionTestHelper helper;
    private ProgressAction action;
    private DoneAction doneAction;

    @BeforeEach
    void setUp() throws Exception {
        helper = new ActionTestHelper(tempDir);
        action = new ProgressAction(helper.getDb(), helper.getIssueRepo(), helper.getProgressRepo());
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
        Object result = action.execute(null);

        assertThat(result).isInstanceOf(ErrorResponse.class);
        assertThat(((ErrorResponse) result).error().code()).isEqualTo("SARIF_NOT_LOADED");
    }

    @Test
    void shouldReturnProgressAfterLoad() throws Exception {
        helper.loadSampleSarif();

        Object result = action.execute(null);

        assertThat(result).isInstanceOf(ProgressResponse.class);
        ProgressResponse response = (ProgressResponse) result;
        assertThat(response.total()).isEqualTo(4);
        assertThat(response.fixed()).isEqualTo(0);
        assertThat(response.skip()).isEqualTo(0);
        assertThat(response.rem()).isEqualTo(4);
    }

    @Test
    void shouldTrackFixedIssues() throws Exception {
        helper.loadSampleSarif();

        // Mark an issue as fixed
        var issues = helper.getIssueRepo().getRemainingIssues(null);
        String fp = issues.get(0).fp();
        doneAction.execute(List.of(fp), "fixed");

        Object result = action.execute(null);

        assertThat(result).isInstanceOf(ProgressResponse.class);
        ProgressResponse response = (ProgressResponse) result;
        assertThat(response.fixed()).isEqualTo(1);
        assertThat(response.rem()).isEqualTo(3);
    }

    @Test
    void shouldTrackSkippedIssues() throws Exception {
        helper.loadSampleSarif();

        // Skip an issue
        var issues = helper.getIssueRepo().getRemainingIssues(null);
        String fp = issues.get(0).fp();
        doneAction.execute(List.of(fp), "skip");

        Object result = action.execute(null);

        assertThat(result).isInstanceOf(ProgressResponse.class);
        ProgressResponse response = (ProgressResponse) result;
        assertThat(response.skip()).isEqualTo(1);
        assertThat(response.rem()).isEqualTo(3);
    }

    @Test
    void shouldFilterByRuleScope() throws Exception {
        helper.loadSampleSarif();

        Scope scope = new Scope("ConstantValue", null, null);
        Object result = action.execute(scope);

        assertThat(result).isInstanceOf(ProgressResponse.class);
        ProgressResponse response = (ProgressResponse) result;
        // Total should be filtered to only ConstantValue issues
        assertThat(response.total()).isLessThan(4);
        assertThat(response.scope()).isNotNull();
        assertThat(response.scope().rule()).isEqualTo("ConstantValue");
    }

    @Test
    void shouldReturnFileCounts() throws Exception {
        helper.loadSampleSarif();

        Object result = action.execute(null);

        assertThat(result).isInstanceOf(ProgressResponse.class);
        ProgressResponse response = (ProgressResponse) result;
        // Sample SARIF has 3 files
        assertThat(response.filesRem()).isEqualTo(3);
        assertThat(response.filesOk()).isEqualTo(0);
    }
}

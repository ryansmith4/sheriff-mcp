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
import com.guidedbyte.sheriff.model.response.SummaryResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class SummaryActionTest {

    @TempDir
    File tempDir;

    private ActionTestHelper helper;
    private SummaryAction action;
    private DoneAction doneAction;

    @BeforeEach
    void setUp() throws Exception {
        helper = new ActionTestHelper(tempDir);
        action = new SummaryAction(helper.getDb(), helper.getIssueRepo(), helper.getProgressRepo());
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
        Object result = action.execute();

        assertThat(result).isInstanceOf(ErrorResponse.class);
        assertThat(((ErrorResponse) result).error().code()).isEqualTo("SARIF_NOT_LOADED");
    }

    @Test
    void shouldReturnSummaryAfterLoad() throws Exception {
        helper.loadSampleSarif();

        Object result = action.execute();

        assertThat(result).isInstanceOf(SummaryResponse.class);
        SummaryResponse response = (SummaryResponse) result;
        assertThat(response.total()).isEqualTo(4);
        assertThat(response.fixed()).isEqualTo(0);
        assertThat(response.skip()).isEqualTo(0);
        assertThat(response.remaining()).isEqualTo(4);
    }

    @Test
    void shouldIncludeRuleBreakdown() throws Exception {
        helper.loadSampleSarif();

        Object result = action.execute();

        assertThat(result).isInstanceOf(SummaryResponse.class);
        SummaryResponse response = (SummaryResponse) result;
        assertThat(response.byRule()).isNotEmpty();

        // All issues should have a rule breakdown entry
        int totalFromRules = response.byRule().stream()
                .mapToInt(SummaryResponse.RuleBreakdown::total)
                .sum();
        assertThat(totalFromRules).isEqualTo(4);
    }

    @Test
    void shouldIncludeFileBreakdown() throws Exception {
        helper.loadSampleSarif();

        Object result = action.execute();

        assertThat(result).isInstanceOf(SummaryResponse.class);
        SummaryResponse response = (SummaryResponse) result;
        assertThat(response.byFile()).isNotEmpty();
    }

    @Test
    void shouldUpdateCountsAfterMarkingFixed() throws Exception {
        helper.loadSampleSarif();

        // Get a fingerprint and mark it fixed
        var issues = helper.getIssueRepo().getRemainingIssues(null);
        String fp = issues.get(0).fp();
        doneAction.execute(List.of(fp), "fixed");

        Object result = action.execute();

        assertThat(result).isInstanceOf(SummaryResponse.class);
        SummaryResponse response = (SummaryResponse) result;
        assertThat(response.total()).isEqualTo(4);
        assertThat(response.fixed()).isEqualTo(1);
        assertThat(response.remaining()).isEqualTo(3);
    }

    @Test
    void shouldUpdateCountsAfterSkipping() throws Exception {
        helper.loadSampleSarif();

        // Get a fingerprint and skip it
        var issues = helper.getIssueRepo().getRemainingIssues(null);
        String fp = issues.get(0).fp();
        doneAction.execute(List.of(fp), "skip");

        Object result = action.execute();

        assertThat(result).isInstanceOf(SummaryResponse.class);
        SummaryResponse response = (SummaryResponse) result;
        assertThat(response.total()).isEqualTo(4);
        assertThat(response.skip()).isEqualTo(1);
        assertThat(response.remaining()).isEqualTo(3);
    }
}

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
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import com.guidedbyte.sheriff.model.response.ErrorResponse;
import com.guidedbyte.sheriff.model.response.LoadResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class LoadActionTest {

    @TempDir
    File tempDir;

    private ActionTestHelper helper;
    private LoadAction action;

    @BeforeEach
    void setUp() throws Exception {
        helper = new ActionTestHelper(tempDir);
        action = new LoadAction(
                helper.getSarifParser(),
                helper.getDb(),
                helper.getIssueRepo(),
                helper.getProgressRepo(),
                helper.getBatchService());
    }

    @AfterEach
    void tearDown() {
        if (helper != null) {
            helper.close();
        }
    }

    @Test
    void shouldReturnErrorWhenTargetNull() {
        Object result = action.execute(null);

        assertThat(result).isInstanceOf(ErrorResponse.class);
        assertThat(((ErrorResponse) result).error().code()).isEqualTo("INVALID_TARGET");
    }

    @Test
    void shouldReturnErrorWhenTargetEmpty() {
        Object result = action.execute("");

        assertThat(result).isInstanceOf(ErrorResponse.class);
        assertThat(((ErrorResponse) result).error().code()).isEqualTo("INVALID_TARGET");
    }

    @Test
    void shouldReturnErrorWhenFileNotFound() {
        Object result = action.execute("/nonexistent/path/to/file.sarif");

        assertThat(result).isInstanceOf(ErrorResponse.class);
    }

    @Test
    void shouldLoadSarifSuccessfully() {
        String sarifPath = getSampleSarifPath();
        Object result = action.execute(sarifPath);

        assertThat(result).isInstanceOf(LoadResponse.class);
        LoadResponse response = (LoadResponse) result;
        assertThat(response.total()).isEqualTo(4);
        assertThat(response.files()).isEqualTo(3);
        assertThat(response.sev()).isNotEmpty();
    }

    @Test
    void shouldPreserveProgressWhenSarifUnchanged() {
        String sarifPath = getSampleSarifPath();

        // Load once
        Object result1 = action.execute(sarifPath);
        assertThat(result1).isInstanceOf(LoadResponse.class);

        // Load same file again
        Object result2 = action.execute(sarifPath);
        assertThat(result2).isInstanceOf(LoadResponse.class);

        LoadResponse response2 = (LoadResponse) result2;
        assertThat(response2.total()).isEqualTo(4);
    }

    @Test
    void shouldResetProgressWhenSarifChanged() throws Exception {
        String sarifPath = getSampleSarifPath();

        // Load original
        Object result1 = action.execute(sarifPath);
        assertThat(result1).isInstanceOf(LoadResponse.class);

        // Create a different SARIF file
        Path newSarif = Path.of(tempDir.getAbsolutePath(), "different.sarif.json");
        Files.writeString(
                newSarif,
                """
                {
                  "$schema": "https://raw.githubusercontent.com/oasis-tcs/sarif-spec/master/Schemata/sarif-schema-2.1.0.json",
                  "version": "2.1.0",
                  "runs": [{
                    "tool": {"driver": {"name": "Test", "version": "1.0.0", "rules": []}},
                    "results": [{
                      "ruleId": "NewRule",
                      "level": "error",
                      "message": {"text": "New issue"},
                      "locations": [{"physicalLocation": {"artifactLocation": {"uri": "New.java"}, "region": {"startLine": 1}}}],
                      "fingerprints": {"0": "newfingerprint123"}
                    }]
                  }]
                }
                """);

        Object result2 = action.execute(newSarif.toString());
        assertThat(result2).isInstanceOf(LoadResponse.class);
        LoadResponse response2 = (LoadResponse) result2;
        assertThat(response2.total()).isEqualTo(1);
    }

    @Test
    void shouldReturnPriorProgressOnReload() throws Exception {
        String sarifPath = getSampleSarifPath();

        // Load SARIF
        action.execute(sarifPath);

        // Mark some issues as done
        var issues = helper.getIssueRepo().getRemainingIssues(null);
        assertThat(issues).isNotEmpty();
        DoneAction doneAction = new DoneAction(helper.getDb(), helper.getIssueRepo(), helper.getProgressRepo());
        doneAction.execute(java.util.List.of(issues.get(0).fp()), "fixed");

        // Create a different SARIF to trigger reload
        Path newSarif = Path.of(tempDir.getAbsolutePath(), "changed.sarif.json");
        Files.writeString(
                newSarif,
                """
                {
                  "$schema": "https://raw.githubusercontent.com/oasis-tcs/sarif-spec/master/Schemata/sarif-schema-2.1.0.json",
                  "version": "2.1.0",
                  "runs": [{
                    "tool": {"driver": {"name": "Test", "version": "1.0.0", "rules": []}},
                    "results": [{
                      "ruleId": "Rule1",
                      "level": "warning",
                      "message": {"text": "Issue"},
                      "locations": [{"physicalLocation": {"artifactLocation": {"uri": "File.java"}, "region": {"startLine": 1}}}],
                      "fingerprints": {"0": "differentfp123"}
                    }]
                  }]
                }
                """);

        Object result = action.execute(newSarif.toString());
        assertThat(result).isInstanceOf(LoadResponse.class);
        LoadResponse response = (LoadResponse) result;
        assertThat(response.prior().fixed()).isEqualTo(1);
    }

    private String getSampleSarifPath() {
        URL resource = getClass().getClassLoader().getResource("sarif/sample.sarif.json");
        if (resource == null) {
            throw new IllegalStateException("Sample SARIF file not found");
        }
        return new File(resource.getFile()).getAbsolutePath();
    }
}

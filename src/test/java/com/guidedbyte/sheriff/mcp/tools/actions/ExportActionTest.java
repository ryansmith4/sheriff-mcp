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
import com.guidedbyte.sheriff.model.response.ExportResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class ExportActionTest {

    @TempDir
    File tempDir;

    private ActionTestHelper helper;
    private ExportAction action;
    private String originalDir;

    @BeforeEach
    void setUp() throws Exception {
        helper = new ActionTestHelper(tempDir);
        action = new ExportAction(helper.getDb(), helper.getIssueRepo());

        // Change working directory to temp dir for export tests
        originalDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.getAbsolutePath());
    }

    @AfterEach
    void tearDown() {
        if (helper != null) {
            helper.close();
        }
        if (originalDir != null) {
            System.setProperty("user.dir", originalDir);
        }
    }

    @Test
    void shouldReturnErrorWhenSarifNotLoaded() {
        Object result = action.execute(null, null, null);

        assertThat(result).isInstanceOf(ErrorResponse.class);
        assertThat(((ErrorResponse) result).error().code()).isEqualTo("SARIF_NOT_LOADED");
    }

    @Test
    void shouldExportToJsonByDefault() throws Exception {
        helper.loadSampleSarif();

        Object result = action.execute(null, "export.json", null);

        assertThat(result).isInstanceOf(ExportResponse.class);
        ExportResponse response = (ExportResponse) result;
        assertThat(response.format()).isEqualTo("json");
        assertThat(response.count()).isEqualTo(4);
        assertThat(response.path()).endsWith("export.json");
    }

    @Test
    void shouldExportToListFormat() throws Exception {
        helper.loadSampleSarif();

        Object result = action.execute(null, "export.txt", "list");

        assertThat(result).isInstanceOf(ExportResponse.class);
        ExportResponse response = (ExportResponse) result;
        assertThat(response.format()).isEqualTo("list");
        assertThat(response.count()).isEqualTo(4);
    }

    @Test
    void shouldRejectInvalidFormat() throws Exception {
        helper.loadSampleSarif();

        Object result = action.execute(null, "export.txt", "xml");

        assertThat(result).isInstanceOf(ErrorResponse.class);
        assertThat(((ErrorResponse) result).error().code()).isEqualTo("INVALID_FORMAT");
    }

    @Test
    void shouldRejectPathTraversalWithDotDot() throws Exception {
        helper.loadSampleSarif();

        Object result = action.execute(null, "../outside.json", null);

        assertThat(result).isInstanceOf(ErrorResponse.class);
        assertThat(((ErrorResponse) result).error().code()).isEqualTo("INVALID_PATH");
    }

    @Test
    void shouldRejectAbsolutePath() throws Exception {
        helper.loadSampleSarif();

        Object result = action.execute(null, "/etc/passwd", null);

        assertThat(result).isInstanceOf(ErrorResponse.class);
        assertThat(((ErrorResponse) result).error().code()).isEqualTo("INVALID_PATH");
    }

    @Test
    void shouldRejectWindowsAbsolutePath() throws Exception {
        helper.loadSampleSarif();

        Object result = action.execute(null, "C:\\Windows\\test.json", null);

        assertThat(result).isInstanceOf(ErrorResponse.class);
        assertThat(((ErrorResponse) result).error().code()).isEqualTo("INVALID_PATH");
    }

    @Test
    void shouldGenerateDefaultFilename() throws Exception {
        helper.loadSampleSarif();

        Object result = action.execute(null, null, null);

        assertThat(result).isInstanceOf(ExportResponse.class);
        ExportResponse response = (ExportResponse) result;
        assertThat(response.path()).contains("sheriff-export-");
    }
}

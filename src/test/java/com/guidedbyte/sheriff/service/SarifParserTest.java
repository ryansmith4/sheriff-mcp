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
package com.guidedbyte.sheriff.service;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.guidedbyte.sheriff.model.sarif.Result;
import com.guidedbyte.sheriff.model.sarif.Run;
import com.guidedbyte.sheriff.model.sarif.SarifReport;
import com.guidedbyte.sheriff.service.SarifParser.SarifParseException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SarifParserTest {

    private SarifParser parser;

    @BeforeEach
    void setUp() {
        parser = new SarifParser();
    }

    @Test
    void parseSampleSarif() throws SarifParseException {
        URL resource = getClass().getClassLoader().getResource("sarif/sample.sarif.json");
        assertThat(resource).isNotNull();

        File file = new File(resource.getFile());
        SarifReport report = parser.parse(file);

        assertThat(report).isNotNull();
        assertThat(report.version()).isEqualTo("2.1.0");
        assertThat(report.runs()).hasSize(1);

        Run run = report.runs().get(0);
        assertThat(run.tool().driver().name()).isEqualTo("TestAnalyzer");
        assertThat(run.results()).hasSize(4);

        // Check first result
        Result first = run.results().get(0);
        assertThat(first.ruleId()).isEqualTo("ConstantValue");
        assertThat(first.level()).isEqualTo("warning");
        assertThat(first.message().getText()).contains("value != null");
        assertThat(first.getFingerprint()).isEqualTo("abc123def456");
    }

    @Test
    void parseNonExistentFile() {
        assertThatThrownBy(() -> parser.parse(new File("non-existent.sarif")))
                .isInstanceOf(SarifParseException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void computeFileHash() throws SarifParseException {
        URL resource = getClass().getClassLoader().getResource("sarif/sample.sarif.json");
        assertThat(resource).isNotNull();

        String hash = parser.computeFileHash(new File(resource.getFile()));
        assertThat(hash).isNotNull();
        assertThat(hash).hasSize(64); // SHA-256 hex
    }

    @Test
    void findSarifFilesSingleFile() throws Exception {
        URL resource = getClass().getClassLoader().getResource("sarif/sample.sarif.json");
        assertThat(resource).isNotNull();

        String path = new File(resource.getFile()).getAbsolutePath();
        List<Path> files = parser.findSarifFiles(path);

        assertThat(files).hasSize(1);
        assertThat(files.get(0).toString()).endsWith("sample.sarif.json");
    }

    @Test
    void findSarifFilesDirectory(@TempDir File tempDir) throws Exception {
        // Create some SARIF files in the temp directory
        Files.writeString(tempDir.toPath().resolve("report.sarif"), "{}");
        Files.writeString(tempDir.toPath().resolve("report2.sarif.json"), "{}");
        Files.writeString(tempDir.toPath().resolve("notasarif.txt"), "{}");

        List<Path> files = parser.findSarifFiles(tempDir.getAbsolutePath());

        assertThat(files).hasSize(2);
        assertThat(files)
                .allMatch(p -> p.getFileName().toString().endsWith(".sarif")
                        || p.getFileName().toString().endsWith(".sarif.json"));
    }

    @Test
    void findSarifFilesNonexistent(@TempDir File tempDir) {
        String nonexistent = new File(tempDir, "does_not_exist").getAbsolutePath();
        assertThatThrownBy(() -> parser.findSarifFiles(nonexistent))
                .isInstanceOf(SarifParseException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void parseFromString() throws SarifParseException {
        URL resource = getClass().getClassLoader().getResource("sarif/sample.sarif.json");
        assertThat(resource).isNotNull();

        SarifReport report = parser.parse(new File(resource.getFile()).getAbsolutePath());
        assertThat(report).isNotNull();
        assertThat(report.runs()).hasSize(1);
    }
}

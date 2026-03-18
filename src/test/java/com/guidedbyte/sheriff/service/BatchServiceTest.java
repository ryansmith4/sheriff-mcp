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
import java.sql.SQLException;
import java.util.List;

import com.guidedbyte.sheriff.model.response.NextResponse;
import com.guidedbyte.sheriff.model.sarif.SarifReport;
import com.guidedbyte.sheriff.model.state.IssueStatus;
import com.guidedbyte.sheriff.model.state.Scope;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class BatchServiceTest {

    @TempDir
    File tempDir;

    private DatabaseService db;
    private IssueRepository issueRepo;
    private ProgressRepository progressRepo;
    private BatchService batchService;
    private SarifParser parser;

    @BeforeEach
    void setUp() throws SQLException {
        db = new DatabaseService(tempDir.getAbsolutePath());
        issueRepo = new IssueRepository(db);
        progressRepo = new ProgressRepository(db);
        batchService = new BatchService(issueRepo, progressRepo);
        parser = new SarifParser();
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (db != null) {
            db.close();
        }
    }

    @Test
    void loadSarifReport() throws Exception {
        URL resource = getClass().getClassLoader().getResource("sarif/sample.sarif.json");
        assertThat(resource).isNotNull();

        SarifReport report = parser.parse(new File(resource.getFile()));
        int loaded = batchService.loadSarifReport(report);

        assertThat(loaded).isEqualTo(4);
        assertThat(issueRepo.getTotal()).isEqualTo(4);
    }

    @Test
    void getNextBatchDefault() throws Exception {
        loadSampleSarif();

        NextResponse response = batchService.getNextBatch(null, false);

        assertThat(response.file()).isNotNull();
        assertThat(response.issues()).isNotEmpty();
        assertThat(response.checklist()).isNull();
        assertThat(response.prog()).isNotNull();
        assertThat(response.prog().rem()).isEqualTo(4);
    }

    @Test
    void getNextBatchChecklist() throws Exception {
        loadSampleSarif();

        NextResponse response = batchService.getNextBatch(null, true);

        assertThat(response.checklist()).isNotNull();
        assertThat(response.checklist()).isNotEmpty();
        assertThat(response.checklist().get(0).task()).startsWith("Fix ");
        assertThat(response.checklist().get(0).fp()).isNotBlank();
    }

    @Test
    void getNextBatchWithLimit() throws Exception {
        loadSampleSarif();

        NextResponse response = batchService.getNextBatch(null, false, 1);

        assertThat(response.issues()).hasSize(1);
    }

    @Test
    void getNextBatchWithScope() throws Exception {
        loadSampleSarif();

        // Filter by rule
        Scope scope = new Scope("ConstantValue", null, null);
        NextResponse response = batchService.getNextBatch(scope, false);

        assertThat(response.issues()).isNotEmpty();
        assertThat(response.issues()).allMatch(i -> "ConstantValue".equals(i.rule()));
    }

    @Test
    void getNextBatchEmptyWhenAllDone() throws Exception {
        loadSampleSarif();

        // Mark all issues as done
        var issues = issueRepo.getRemainingIssues(null);
        progressRepo.markStatus(issues.stream().map(IssueRepository.Issue::fp).toList(), IssueStatus.FIXED);

        NextResponse response = batchService.getNextBatch(null, false);

        assertThat(response.file()).isNull();
        assertThat(response.issues()).isEmpty();
        assertThat(response.prog().rem()).isEqualTo(0);
        assertThat(response.prog().fixed()).isEqualTo(4);
    }

    @Test
    void progressTracksCorrectly() throws Exception {
        loadSampleSarif();

        // Mark 2 as fixed, 1 as skipped
        progressRepo.markStatus(List.of("abc123def456"), IssueStatus.FIXED);
        progressRepo.markStatus(List.of("def789ghi012"), IssueStatus.FIXED);
        progressRepo.markStatus(List.of("jkl345mno678"), IssueStatus.SKIPPED);

        NextResponse response = batchService.getNextBatch(null, false);

        assertThat(response.prog().fixed()).isEqualTo(2);
        assertThat(response.prog().skip()).isEqualTo(1);
        assertThat(response.prog().rem()).isEqualTo(1);
    }

    @Test
    void severityMapping() throws Exception {
        loadSampleSarif();

        // Get all issues from the next batch responses
        var allIssues = issueRepo.getRemainingIssues(null);

        // Sample SARIF has: 2 warning (M), 1 note (L), 1 error (H)
        long high = allIssues.stream().filter(i -> "H".equals(i.sev())).count();
        long medium = allIssues.stream().filter(i -> "M".equals(i.sev())).count();
        long low = allIssues.stream().filter(i -> "L".equals(i.sev())).count();

        assertThat(high).isEqualTo(1);
        assertThat(medium).isEqualTo(2);
        assertThat(low).isEqualTo(1);
    }

    private void loadSampleSarif() throws Exception {
        URL resource = getClass().getClassLoader().getResource("sarif/sample.sarif.json");
        SarifReport report = parser.parse(new File(resource.getFile()));
        batchService.loadSarifReport(report);
    }
}

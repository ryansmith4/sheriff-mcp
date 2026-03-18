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
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import com.guidedbyte.sheriff.model.state.IssueStatus;
import com.guidedbyte.sheriff.model.state.ProgressState;
import com.guidedbyte.sheriff.model.state.Scope;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class ProgressRepositoryTest {

    @TempDir
    File tempDir;

    private DatabaseService db;
    private IssueRepository issueRepo;
    private ProgressRepository progressRepo;

    @BeforeEach
    void setUp() throws SQLException {
        db = new DatabaseService(tempDir.getAbsolutePath());
        issueRepo = new IssueRepository(db);
        progressRepo = new ProgressRepository(db);

        // Insert some test issues
        List<IssueRepository.Issue> issues = List.of(
                new IssueRepository.Issue("fp1", "Rule1", "File1.java", 10, 5, "msg1", "H", "snip1", null),
                new IssueRepository.Issue("fp2", "Rule1", "File1.java", 20, 5, "msg2", "H", "snip2", null),
                new IssueRepository.Issue("fp3", "Rule2", "File2.java", 30, 5, "msg3", "M", "snip3", null));
        issueRepo.insertBatch(issues);
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (db != null) {
            db.close();
        }
    }

    @Test
    void markStatusFixed() throws SQLException {
        int marked = progressRepo.markStatus(List.of("fp1"), IssueStatus.FIXED);
        assertThat(marked).isEqualTo(1);

        ProgressState state = progressRepo.getProgress("fp1");
        assertThat(state).isNotNull();
        assertThat(state.status()).isEqualTo(IssueStatus.FIXED);
    }

    @Test
    void markStatusSkipped() throws SQLException {
        int marked = progressRepo.markStatus(List.of("fp2", "fp3"), IssueStatus.SKIPPED);
        assertThat(marked).isEqualTo(2);

        ProgressState state2 = progressRepo.getProgress("fp2");
        ProgressState state3 = progressRepo.getProgress("fp3");

        assertThat(state2.status()).isEqualTo(IssueStatus.SKIPPED);
        assertThat(state3.status()).isEqualTo(IssueStatus.SKIPPED);
    }

    @Test
    void getProgressCounts() throws SQLException {
        progressRepo.markStatus(List.of("fp1"), IssueStatus.FIXED);
        progressRepo.markStatus(List.of("fp2"), IssueStatus.SKIPPED);

        Map<String, Integer> counts = progressRepo.getProgressCounts();

        assertThat(counts).containsEntry("fixed", 1);
        assertThat(counts).containsEntry("skip", 1);
    }

    @Test
    void markEmptyListReturnsZero() throws SQLException {
        int marked = progressRepo.markStatus(List.of(), IssueStatus.FIXED);
        assertThat(marked).isEqualTo(0);
    }

    @Test
    void clearProgress() throws SQLException {
        progressRepo.markStatus(List.of("fp1"), IssueStatus.FIXED);
        progressRepo.clear();

        ProgressState state = progressRepo.getProgress("fp1");
        assertThat(state).isNull();
    }

    @Test
    void hasProgress() throws SQLException {
        assertThat(progressRepo.hasProgress("fp1")).isFalse();

        progressRepo.markStatus(List.of("fp1"), IssueStatus.FIXED);

        assertThat(progressRepo.hasProgress("fp1")).isTrue();
        assertThat(progressRepo.hasProgress("fp2")).isFalse();
    }

    @Test
    void reopen() throws SQLException {
        progressRepo.markStatus(List.of("fp1", "fp2"), IssueStatus.FIXED);
        assertThat(progressRepo.hasProgress("fp1")).isTrue();
        assertThat(progressRepo.hasProgress("fp2")).isTrue();

        int reopened = progressRepo.reopen(List.of("fp1"));
        assertThat(reopened).isEqualTo(1);
        assertThat(progressRepo.hasProgress("fp1")).isFalse();
        assertThat(progressRepo.hasProgress("fp2")).isTrue();
    }

    @Test
    void reopenEmptyList() throws SQLException {
        assertThat(progressRepo.reopen(List.of())).isEqualTo(0);
        assertThat(progressRepo.reopen(null)).isEqualTo(0);
    }

    @Test
    void getProgressCountsInScope() throws SQLException {
        progressRepo.markStatus(List.of("fp1"), IssueStatus.FIXED);
        progressRepo.markStatus(List.of("fp3"), IssueStatus.SKIPPED);

        // No scope returns all
        Map<String, Integer> counts = progressRepo.getProgressCountsInScope(null);
        assertThat(counts).containsEntry("fixed", 1);
        assertThat(counts).containsEntry("skip", 1);

        // Scope by rule
        Scope ruleScope = new Scope("Rule1", null, null);
        Map<String, Integer> ruleCounts = progressRepo.getProgressCountsInScope(ruleScope);
        assertThat(ruleCounts).containsEntry("fixed", 1);
        assertThat(ruleCounts).containsEntry("skip", 0);

        // Scope by severity
        Scope sevScope = new Scope(null, "medium", null);
        Map<String, Integer> sevCounts = progressRepo.getProgressCountsInScope(sevScope);
        assertThat(sevCounts).containsEntry("fixed", 0);
        assertThat(sevCounts).containsEntry("skip", 1);
    }

    @Test
    void getCompletedFileCount() throws SQLException {
        // No progress, no completed files
        assertThat(progressRepo.getCompletedFileCount(null)).isEqualTo(0);

        // Mark one issue in File1.java — file still not complete (has 2 issues)
        progressRepo.markStatus(List.of("fp1"), IssueStatus.FIXED);
        assertThat(progressRepo.getCompletedFileCount(null)).isEqualTo(0);

        // Mark remaining issue in File1.java — now complete
        progressRepo.markStatus(List.of("fp2"), IssueStatus.FIXED);
        assertThat(progressRepo.getCompletedFileCount(null)).isEqualTo(1);

        // Mark File2.java issue
        progressRepo.markStatus(List.of("fp3"), IssueStatus.SKIPPED);
        assertThat(progressRepo.getCompletedFileCount(null)).isEqualTo(2);
    }

    @Test
    void getCompletedFileCountWithScope() throws SQLException {
        // Mark all Rule1 issues as fixed (both in File1.java)
        progressRepo.markStatus(List.of("fp1", "fp2"), IssueStatus.FIXED);

        // Without scope, File1.java is complete
        assertThat(progressRepo.getCompletedFileCount(null)).isEqualTo(1);

        // With Rule1 scope, File1.java is complete for Rule1
        Scope scope = new Scope("Rule1", null, null);
        assertThat(progressRepo.getCompletedFileCount(scope)).isEqualTo(1);
    }

    @Test
    void getProgressState() throws SQLException {
        progressRepo.markStatus(List.of("fp1"), IssueStatus.FIXED);

        ProgressState state = progressRepo.getProgress("fp1");
        assertThat(state).isNotNull();
        assertThat(state.fingerprint()).isEqualTo("fp1");
        assertThat(state.status()).isEqualTo(IssueStatus.FIXED);
        assertThat(state.timestamp()).isNotNull();
    }
}

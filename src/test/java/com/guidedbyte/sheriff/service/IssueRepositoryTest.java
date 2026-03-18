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
import com.guidedbyte.sheriff.model.state.Scope;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class IssueRepositoryTest {

    @TempDir
    File tempDir;

    private DatabaseService db;
    private IssueRepository repo;
    private ProgressRepository progressRepo;

    @BeforeEach
    void setUp() throws SQLException {
        db = new DatabaseService(tempDir.getAbsolutePath());
        repo = new IssueRepository(db);
        progressRepo = new ProgressRepository(db);
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (db != null) {
            db.close();
        }
    }

    @Test
    void insertAndGetTotal() throws SQLException {
        IssueRepository.Issue issue = new IssueRepository.Issue(
                "fp1", "TestRule", "test/File.java", 10, 5, "Test message", "H", "code snippet", null);

        repo.insert(issue);
        assertThat(repo.getTotal()).isEqualTo(1);
    }

    @Test
    void insertBatch() throws SQLException {
        List<IssueRepository.Issue> issues = List.of(
                new IssueRepository.Issue("fp1", "Rule1", "File1.java", 10, 5, "msg1", "H", "snip1", null),
                new IssueRepository.Issue("fp2", "Rule1", "File1.java", 20, 5, "msg2", "H", "snip2", null),
                new IssueRepository.Issue("fp3", "Rule2", "File2.java", 30, 5, "msg3", "M", "snip3", null));

        repo.insertBatch(issues);
        assertThat(repo.getTotal()).isEqualTo(3);
    }

    @Test
    void getSeverityCounts() throws SQLException {
        List<IssueRepository.Issue> issues = List.of(
                new IssueRepository.Issue("fp1", "Rule1", "File1.java", 10, 5, "msg1", "H", "snip1", null),
                new IssueRepository.Issue("fp2", "Rule1", "File1.java", 20, 5, "msg2", "H", "snip2", null),
                new IssueRepository.Issue("fp3", "Rule2", "File2.java", 30, 5, "msg3", "M", "snip3", null));

        repo.insertBatch(issues);

        Map<String, Integer> counts = repo.getSeverityCounts();
        assertThat(counts).containsEntry("H", 2);
        assertThat(counts).containsEntry("M", 1);
    }

    @Test
    void getRuleCounts() throws SQLException {
        List<IssueRepository.Issue> issues = List.of(
                new IssueRepository.Issue("fp1", "Rule1", "File1.java", 10, 5, "msg1", "H", "snip1", null),
                new IssueRepository.Issue("fp2", "Rule1", "File1.java", 20, 5, "msg2", "H", "snip2", null),
                new IssueRepository.Issue("fp3", "Rule2", "File2.java", 30, 5, "msg3", "M", "snip3", null));

        repo.insertBatch(issues);

        Map<String, Integer> counts = repo.getRuleCounts();
        assertThat(counts).containsEntry("Rule1", 2);
        assertThat(counts).containsEntry("Rule2", 1);
    }

    @Test
    void getNextBatchGroupsByFile() throws SQLException {
        List<IssueRepository.Issue> issues = List.of(
                new IssueRepository.Issue("fp1", "Rule1", "File1.java", 10, 5, "msg1", "H", "snip1", null),
                new IssueRepository.Issue("fp2", "Rule1", "File1.java", 20, 5, "msg2", "H", "snip2", null),
                new IssueRepository.Issue("fp3", "Rule2", "File2.java", 30, 5, "msg3", "M", "snip3", null));

        repo.insertBatch(issues);

        List<IssueRepository.Issue> batch = repo.getNextBatch(null);

        // Should return all issues for the first file
        assertThat(batch).hasSize(2);
        assertThat(batch).allMatch(i -> i.file().equals("File1.java"));
    }

    @Test
    void getNextBatchWithScope() throws SQLException {
        List<IssueRepository.Issue> issues = List.of(
                new IssueRepository.Issue("fp1", "Rule1", "File1.java", 10, 5, "msg1", "H", "snip1", null),
                new IssueRepository.Issue("fp2", "Rule2", "File1.java", 20, 5, "msg2", "M", "snip2", null),
                new IssueRepository.Issue("fp3", "Rule1", "File2.java", 30, 5, "msg3", "H", "snip3", null));

        repo.insertBatch(issues);

        Scope scope = new Scope("Rule1", null, null);
        List<IssueRepository.Issue> batch = repo.getNextBatch(scope);

        assertThat(batch).hasSize(1);
        assertThat(batch.get(0).rule()).isEqualTo("Rule1");
        assertThat(batch.get(0).file()).isEqualTo("File1.java");
    }

    @Test
    void exists_shouldFindInsertedIssue() throws SQLException {
        IssueRepository.Issue issue = new IssueRepository.Issue(
                "fp123", "TestRule", "test/File.java", 10, 5, "Test message", "H", "code snippet", null);

        repo.insert(issue);

        assertThat(repo.exists("fp123")).isTrue();
        assertThat(repo.exists("nonexistent")).isFalse();
    }

    @Test
    void getFileCount() throws SQLException {
        insertStandardIssues();

        assertThat(repo.getFileCount()).isEqualTo(2);
    }

    @Test
    void getTotalInScope() throws SQLException {
        insertStandardIssues();

        // All issues
        assertThat(repo.getTotalInScope(null)).isEqualTo(3);

        // By rule
        assertThat(repo.getTotalInScope(new Scope("Rule1", null, null))).isEqualTo(2);
        assertThat(repo.getTotalInScope(new Scope("Rule2", null, null))).isEqualTo(1);

        // By severity
        assertThat(repo.getTotalInScope(new Scope(null, "high", null))).isEqualTo(2);
        assertThat(repo.getTotalInScope(new Scope(null, "medium", null))).isEqualTo(1);
    }

    @Test
    void getRemainingIssues() throws SQLException {
        insertStandardIssues();

        // All remaining initially
        List<IssueRepository.Issue> remaining = repo.getRemainingIssues(null);
        assertThat(remaining).hasSize(3);

        // Mark one as fixed
        progressRepo.markStatus(List.of("fp1"), IssueStatus.FIXED);

        remaining = repo.getRemainingIssues(null);
        assertThat(remaining).hasSize(2);
        assertThat(remaining).noneMatch(i -> "fp1".equals(i.fp()));
    }

    @Test
    void getRemainingIssuesWithScope() throws SQLException {
        insertStandardIssues();

        Scope scope = new Scope("Rule1", null, null);
        List<IssueRepository.Issue> remaining = repo.getRemainingIssues(scope);
        assertThat(remaining).hasSize(2);
        assertThat(remaining).allMatch(i -> "Rule1".equals(i.rule()));
    }

    @Test
    void getBreakdownByRule() throws SQLException {
        insertStandardIssues();
        progressRepo.markStatus(List.of("fp1"), IssueStatus.FIXED);
        progressRepo.markStatus(List.of("fp3"), IssueStatus.SKIPPED);

        List<Map<String, Object>> breakdown = repo.getBreakdownByRule();
        assertThat(breakdown).hasSize(2);

        Map<String, Object> rule1 = breakdown.stream()
                .filter(r -> "Rule1".equals(r.get("rule")))
                .findFirst()
                .orElseThrow();
        assertThat(rule1).containsEntry("total", 2);
        assertThat(rule1).containsEntry("fixed", 1);

        Map<String, Object> rule2 = breakdown.stream()
                .filter(r -> "Rule2".equals(r.get("rule")))
                .findFirst()
                .orElseThrow();
        assertThat(rule2).containsEntry("total", 1);
        assertThat(rule2).containsEntry("skip", 1);
    }

    @Test
    void getBreakdownBySeverity() throws SQLException {
        insertStandardIssues();
        progressRepo.markStatus(List.of("fp1"), IssueStatus.FIXED);

        List<Map<String, Object>> breakdown = repo.getBreakdownBySeverity();
        assertThat(breakdown).isNotEmpty();

        Map<String, Object> high = breakdown.stream()
                .filter(r -> "H".equals(r.get("sev")))
                .findFirst()
                .orElseThrow();
        assertThat(high).containsEntry("total", 2);
        assertThat(high).containsEntry("fixed", 1);
    }

    @Test
    void getBreakdownByFile() throws SQLException {
        insertStandardIssues();

        List<Map<String, Object>> breakdown = repo.getBreakdownByFile();
        assertThat(breakdown).hasSize(2);
        // Ordered by remaining desc
        assertThat(breakdown.get(0).get("file")).isEqualTo("File1.java");
        assertThat(breakdown.get(0).get("total")).isEqualTo(2);
    }

    @Test
    void getPendingCounts() throws SQLException {
        insertStandardIssues();
        progressRepo.markStatus(List.of("fp1"), IssueStatus.FIXED);

        Map<String, Integer> counts = repo.getPendingCounts(null);
        assertThat(counts).containsEntry("total", 2);
        assertThat(counts).containsEntry("files", 2);
    }

    @Test
    void getPendingCountsWithScope() throws SQLException {
        insertStandardIssues();

        Map<String, Integer> counts = repo.getPendingCounts(new Scope("Rule1", null, null));
        assertThat(counts).containsEntry("total", 2);
    }

    @Test
    void getNextBatchWithLimit() throws SQLException {
        insertStandardIssues();

        List<IssueRepository.Issue> batch = repo.getNextBatch(null, 1);
        assertThat(batch).hasSize(1);
        assertThat(batch.get(0).file()).isEqualTo("File1.java");
    }

    @Test
    void scopeWithFileFilter() throws SQLException {
        insertStandardIssues();

        Scope scope = new Scope(null, null, "File2");
        List<IssueRepository.Issue> remaining = repo.getRemainingIssues(scope);
        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).file()).isEqualTo("File2.java");
    }

    @Test
    void scopeWithWildcardRule() throws SQLException {
        insertStandardIssues();

        Scope scope = new Scope("Rule*", null, null);
        assertThat(repo.getTotalInScope(scope)).isEqualTo(3);
    }

    private void insertStandardIssues() throws SQLException {
        List<IssueRepository.Issue> issues = List.of(
                new IssueRepository.Issue("fp1", "Rule1", "File1.java", 10, 5, "msg1", "H", "snip1", null),
                new IssueRepository.Issue("fp2", "Rule1", "File1.java", 20, 5, "msg2", "H", "snip2", null),
                new IssueRepository.Issue("fp3", "Rule2", "File2.java", 30, 5, "msg3", "M", "snip3", null));
        repo.insertBatch(issues);
    }
}

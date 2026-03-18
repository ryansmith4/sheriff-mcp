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
import java.sql.SQLException;

import com.guidedbyte.sheriff.service.BatchService;
import com.guidedbyte.sheriff.service.DatabaseService;
import com.guidedbyte.sheriff.service.IssueRepository;
import com.guidedbyte.sheriff.service.ProgressRepository;
import com.guidedbyte.sheriff.service.SarifParser;

/**
 * Helper class providing common setup for action tests.
 */
public class ActionTestHelper implements AutoCloseable {

    private final DatabaseService db;
    private final SarifParser sarifParser;
    private final IssueRepository issueRepo;
    private final ProgressRepository progressRepo;
    private final BatchService batchService;

    public ActionTestHelper(File tempDir) throws SQLException {
        this.db = new DatabaseService(tempDir.getAbsolutePath());
        this.sarifParser = new SarifParser();
        this.issueRepo = new IssueRepository(db);
        this.progressRepo = new ProgressRepository(db);
        this.batchService = new BatchService(issueRepo, progressRepo);
    }

    public DatabaseService getDb() {
        return db;
    }

    public SarifParser getSarifParser() {
        return sarifParser;
    }

    public IssueRepository getIssueRepo() {
        return issueRepo;
    }

    public ProgressRepository getProgressRepo() {
        return progressRepo;
    }

    public BatchService getBatchService() {
        return batchService;
    }

    /**
     * Loads the sample SARIF file into the database.
     */
    public void loadSampleSarif() throws Exception {
        LoadAction loadAction = new LoadAction(sarifParser, db, issueRepo, progressRepo, batchService);
        URL resource = getClass().getClassLoader().getResource("sarif/sample.sarif.json");
        if (resource == null) {
            throw new IllegalStateException("Sample SARIF file not found");
        }
        String target = new File(resource.getFile()).getAbsolutePath();
        loadAction.execute(target);
    }

    @Override
    public void close() {
        try {
            db.close();
        } catch (SQLException e) {
            // Ignore
        }
    }
}

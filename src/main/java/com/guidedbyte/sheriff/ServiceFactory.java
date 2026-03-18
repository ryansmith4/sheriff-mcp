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
package com.guidedbyte.sheriff;

import java.sql.SQLException;

import com.guidedbyte.sheriff.mcp.tools.SheriffTool;
import com.guidedbyte.sheriff.mcp.tools.actions.DoneAction;
import com.guidedbyte.sheriff.mcp.tools.actions.ExportAction;
import com.guidedbyte.sheriff.mcp.tools.actions.LoadAction;
import com.guidedbyte.sheriff.mcp.tools.actions.NextAction;
import com.guidedbyte.sheriff.mcp.tools.actions.ProgressAction;
import com.guidedbyte.sheriff.mcp.tools.actions.ReopenAction;
import com.guidedbyte.sheriff.mcp.tools.actions.SummaryAction;
import com.guidedbyte.sheriff.service.BatchService;
import com.guidedbyte.sheriff.service.DatabaseService;
import com.guidedbyte.sheriff.service.IssueRepository;
import com.guidedbyte.sheriff.service.ProgressRepository;
import com.guidedbyte.sheriff.service.SarifParser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple dependency injection container for Sheriff services.
 */
public class ServiceFactory implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ServiceFactory.class);

    private final DatabaseService db;
    private final SarifParser sarifParser;
    private final IssueRepository issueRepo;
    private final ProgressRepository progressRepo;
    private final BatchService batchService;
    private final LoadAction loadAction;
    private final NextAction nextAction;
    private final DoneAction doneAction;
    private final ProgressAction progressAction;
    private final ReopenAction reopenAction;
    private final SummaryAction summaryAction;
    private final ExportAction exportAction;
    private final SheriffTool sheriffTool;

    public ServiceFactory() throws SQLException {
        this(".sheriff");
    }

    public ServiceFactory(String baseDir) throws SQLException {
        log.info("Initializing ServiceFactory with base dir: {}", baseDir);

        // Create database first; wrap remaining init in try-catch to avoid leaking connection
        this.db = new DatabaseService(baseDir);
        try {
            // Create services
            this.sarifParser = new SarifParser();
            this.issueRepo = new IssueRepository(db);
            this.progressRepo = new ProgressRepository(db);
            this.batchService = new BatchService(issueRepo, progressRepo);

            // Create actions
            this.loadAction = new LoadAction(sarifParser, db, issueRepo, progressRepo, batchService);
            this.nextAction = new NextAction(db, issueRepo, batchService);
            this.doneAction = new DoneAction(db, issueRepo, progressRepo);
            this.progressAction = new ProgressAction(db, issueRepo, progressRepo);
            this.reopenAction = new ReopenAction(db, issueRepo, progressRepo);
            this.summaryAction = new SummaryAction(db, issueRepo, progressRepo);
            this.exportAction = new ExportAction(db, issueRepo);

            // Create tool
            this.sheriffTool = new SheriffTool(
                    loadAction, nextAction, doneAction, progressAction, reopenAction, summaryAction, exportAction);

            log.info("ServiceFactory initialized");
        } catch (Exception e) {
            try {
                db.close();
            } catch (SQLException se) {
                e.addSuppressed(se);
            }
            throw e;
        }
    }

    public DatabaseService getDatabase() {
        return db;
    }

    public SarifParser getSarifParser() {
        return sarifParser;
    }

    public IssueRepository getIssueRepository() {
        return issueRepo;
    }

    public ProgressRepository getProgressRepository() {
        return progressRepo;
    }

    public BatchService getBatchService() {
        return batchService;
    }

    public SheriffTool getSheriffTool() {
        return sheriffTool;
    }

    /** Closes the database connection. Best-effort — failures are logged but not propagated. */
    @Override
    public void close() {
        log.info("Closing ServiceFactory");
        try {
            db.close();
        } catch (SQLException e) {
            log.warn("Error closing database: {}", e.getMessage(), e);
        }
    }
}

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
package com.guidedbyte.sheriff.cli;

import java.io.InputStream;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import com.guidedbyte.sheriff.ServiceFactory;
import com.guidedbyte.sheriff.mcp.EofDetectingInputStream;
import com.guidedbyte.sheriff.mcp.SheriffMcpServer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * Command-line interface for Sheriff.
 */
@Command(
        name = "sheriff",
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider.class,
        description = "AI work queue manager for static analysis issues")
public class SheriffCli implements Callable<Integer> {

    private static final Logger log = LoggerFactory.getLogger(SheriffCli.class);

    @Command(name = "start", description = "Start the MCP server")
    public int start() {
        log.info("Starting Sheriff MCP server...");

        // Phase 1: Start MCP server immediately so Claude Code can connect.
        // The STDIO transport must be listening before the MCP client times out (~2s).
        SheriffMcpServer mcpServer = new SheriffMcpServer();

        // Wrap stdin to detect EOF (MCP client pipe close) for clean shutdown
        InputStream eofStdin = new EofDetectingInputStream(System.in, () -> {
            log.info("Stdin EOF detected (MCP client disconnected), triggering shutdown");
            mcpServer.shutdown();
        });

        // Phase 2: Initialize services (DB connection, etc.) in background
        AtomicReference<ServiceFactory> factoryRef = new AtomicReference<>();
        CompletableFuture.runAsync(() -> {
            try {
                ServiceFactory factory = new ServiceFactory();
                factoryRef.set(factory);
                mcpServer.setTool(factory.getSheriffTool());
            } catch (SQLException e) {
                log.error("Failed to initialize services: {}", e.getMessage(), e);
            }
        });

        // Start the server (blocks until shutdown)
        mcpServer.start(eofStdin);

        // Cleanup
        ServiceFactory factory = factoryRef.get();
        if (factory != null) {
            factory.close();
        }

        return 0;
    }

    @Command(name = "status", description = "Show Sheriff status")
    public int status() {
        try (ServiceFactory factory = new ServiceFactory()) {
            String sarifPath = factory.getDatabase().getMeta("sarif_path");
            String loadedAt = factory.getDatabase().getMeta("loaded_at");

            if (sarifPath == null) {
                System.out.println("No SARIF file loaded");
            } else {
                System.out.println("SARIF: " + sarifPath);
                if (loadedAt != null) {
                    String formattedTime = formatTimestamp(loadedAt);
                    System.out.println("Loaded: " + formattedTime);
                }
                int total = factory.getIssueRepository().getTotal();
                var progress = factory.getProgressRepository().getProgressCounts();
                int fixed = progress.getOrDefault("fixed", 0);
                int skip = progress.getOrDefault("skip", 0);
                int remaining = total - fixed - skip;

                System.out.printf(
                        "Issues: %d total, %d fixed, %d skipped, %d remaining%n", total, fixed, skip, remaining);
            }
            return 0;
        } catch (SQLException e) {
            log.error("Status check failed: {}", e.getMessage(), e);
            System.err.println("Error: Status check failed. Check logs for details.");
            return 1;
        }
    }

    /**
     * Formats a timestamp stored as epoch milliseconds into a human-readable string.
     */
    private String formatTimestamp(String epochMillis) {
        try {
            long millis = Long.parseLong(epochMillis);
            LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
            return dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (NumberFormatException e) {
            return epochMillis; // Fall back to raw value if not a valid number
        }
    }

    @Override
    public Integer call() {
        // Default action: show help
        CommandLine.usage(this, System.out);
        return 0;
    }
}

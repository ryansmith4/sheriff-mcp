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
package com.guidedbyte.sheriff.mcp;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.guidedbyte.sheriff.Version;
import com.guidedbyte.sheriff.mcp.tools.SheriffTool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MCP server for Sheriff static analysis work queue management.
 *
 * <p>Supports two-phase startup: the MCP transport starts listening on stdin immediately,
 * while heavy initialization (database, services) happens in the background. Tool calls
 * block until initialization completes.
 */
public class SheriffMcpServer {

    private static final Logger log = LoggerFactory.getLogger(SheriffMcpServer.class);

    private static final String SERVER_NAME = "sheriff";
    private static final String SERVER_VERSION = Version.get();
    private static final long INIT_TIMEOUT_SECONDS = 30;

    private final ObjectMapper mapper;
    private final JacksonMcpJsonMapper jsonMapper;
    private final CompletableFuture<SheriffTool> toolFuture;
    private volatile SheriffTool sheriffTool;
    private final CountDownLatch shutdownLatch = new CountDownLatch(1);

    public SheriffMcpServer() {
        this.mapper = new ObjectMapper();
        this.jsonMapper = new JacksonMcpJsonMapper(mapper);
        this.toolFuture = new CompletableFuture<>();
    }

    /**
     * Sets the tool implementation once services are initialized.
     * Called from the background initialization thread.
     */
    public void setTool(SheriffTool tool) {
        this.sheriffTool = tool;
        this.toolFuture.complete(tool);
        log.info("Sheriff tool wired to MCP server");
    }

    /**
     * Signals the server to shut down.
     */
    public void shutdown() {
        shutdownLatch.countDown();
    }

    /**
     * Starts the MCP server with STDIO transport.
     *
     * <p>This method starts listening on stdin immediately (Phase 1) and blocks the calling
     * thread until shutdown. Tool calls will wait for {@link #setTool} to be called before
     * executing.
     */
    public void start(InputStream stdin) {
        log.info("Starting Sheriff MCP server...");

        // Create STDIO transport provider with custom stdin for EOF detection
        StdioServerTransportProvider transportProvider =
                new StdioServerTransportProvider(jsonMapper, stdin, System.out);

        // Create tool specification with JSON schema
        Tool toolDef = Tool.builder()
                .name(SheriffTool.TOOL_NAME)
                .description(SheriffTool.TOOL_DESCRIPTION)
                .inputSchema(jsonMapper, SheriffTool.getInputSchema())
                .build();

        // Build the server with tool support
        McpSyncServer server = McpServer.sync(transportProvider)
                .serverInfo(SERVER_NAME, SERVER_VERSION)
                .capabilities(ServerCapabilities.builder().tools(true).build())
                .toolCall(toolDef, this::handleToolCall)
                .build();

        log.info("Sheriff MCP server ready");

        // Register shutdown hook for clean server close
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down Sheriff MCP server...");
            server.close();
            shutdownLatch.countDown();
        }));

        try {
            shutdownLatch.await(); // Block until shutdown signal
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.info("Server interrupted");
        }
    }

    /**
     * Handles tool calls. Waits for initialization if the tool hasn't been wired yet.
     */
    private CallToolResult handleToolCall(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> arguments = request.arguments();
        log.debug("Tool call with action: {}", arguments != null ? arguments.get("action") : "null");

        try {
            SheriffTool tool = this.sheriffTool;
            if (tool == null) {
                log.info("Tool call received before initialization complete, waiting...");
                tool = toolFuture.get(INIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            }

            // Convert arguments map to JSON string for our tool
            String argsJson = mapper.writeValueAsString(arguments);
            String result = tool.execute(argsJson);
            return CallToolResult.builder()
                    .content(List.of(new TextContent(null, result, null)))
                    .isError(false)
                    .build();
        } catch (JsonProcessingException e) {
            log.error("JSON processing error: {}", e.getMessage(), e);
            return createErrorResult("Invalid JSON in tool arguments");
        } catch (Exception e) {
            log.error("Tool execution error: {}", e.getMessage(), e);
            return createErrorResult("Internal tool execution error");
        }
    }

    /**
     * Creates an error result.
     */
    private CallToolResult createErrorResult(String message) {
        try {
            String json = mapper.writeValueAsString(
                    new com.guidedbyte.sheriff.model.response.ErrorResponse("TOOL_ERROR", message));
            return CallToolResult.builder()
                    .content(List.of(new TextContent(null, json, null)))
                    .isError(true)
                    .build();
        } catch (JsonProcessingException e) {
            // Last-resort fallback if serialization itself fails
            return CallToolResult.builder()
                    .content(List.of(new TextContent(
                            null, "{\"error\":{\"code\":\"TOOL_ERROR\",\"msg\":\"Internal error\"}}", null)))
                    .isError(true)
                    .build();
        }
    }
}

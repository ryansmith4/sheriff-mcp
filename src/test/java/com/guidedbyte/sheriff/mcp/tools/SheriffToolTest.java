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
package com.guidedbyte.sheriff.mcp.tools;

import java.io.File;
import java.net.URL;
import java.sql.SQLException;

import com.guidedbyte.sheriff.ServiceFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class SheriffToolTest {

    @TempDir
    File tempDir;

    private ServiceFactory factory;
    private SheriffTool tool;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() throws SQLException {
        factory = new ServiceFactory(tempDir.getAbsolutePath());
        tool = factory.getSheriffTool();
        mapper = new ObjectMapper();
    }

    @AfterEach
    void tearDown() {
        if (factory != null) {
            factory.close();
        }
    }

    @Test
    void invalidActionReturnsError() throws Exception {
        String result = tool.execute("{\"action\": \"invalid\"}");
        JsonNode json = mapper.readTree(result);

        assertThat(json.has("error")).isTrue();
        assertThat(json.get("error").get("code").asText()).isEqualTo("INVALID_ACTION");
    }

    @Test
    void nextWithoutLoadReturnsError() throws Exception {
        String result = tool.execute("{\"action\": \"next\"}");
        JsonNode json = mapper.readTree(result);

        assertThat(json.has("error")).isTrue();
        assertThat(json.get("error").get("code").asText()).isEqualTo("SARIF_NOT_LOADED");
    }

    @Test
    void loadSarifFile() throws Exception {
        URL resource = getClass().getClassLoader().getResource("sarif/sample.sarif.json");
        assertThat(resource).isNotNull();

        String target = new File(resource.getFile()).getAbsolutePath();
        String result = tool.execute("{\"action\": \"load\", \"target\": \"" + escapeJson(target) + "\"}");
        JsonNode json = mapper.readTree(result);

        assertThat(json.has("error")).isFalse();
        assertThat(json.get("total").asInt()).isEqualTo(4);
        assertThat(json.get("files").asInt()).isEqualTo(3);
    }

    @Test
    void fullWorkflow() throws Exception {
        URL resource = getClass().getClassLoader().getResource("sarif/sample.sarif.json");
        assertThat(resource).isNotNull();
        String target = new File(resource.getFile()).getAbsolutePath();

        // Load
        String loadResult = tool.execute("{\"action\": \"load\", \"target\": \"" + escapeJson(target) + "\"}");
        JsonNode loadJson = mapper.readTree(loadResult);
        assertThat(loadJson.get("total").asInt()).isEqualTo(4);

        // Next - should get issues for first file
        String nextResult = tool.execute("{\"action\": \"next\"}");
        JsonNode nextJson = mapper.readTree(nextResult);
        assertThat(nextJson.has("error")).isFalse();
        assertThat(nextJson.get("issues").isArray()).isTrue();
        assertThat(nextJson.get("issues").size()).isGreaterThan(0);

        // Get fingerprint from first issue
        String fp = nextJson.get("issues").get(0).get("fp").asText();

        // Done - mark as fixed
        String doneResult = tool.execute("{\"action\": \"done\", \"fps\": [\"" + fp + "\"], \"status\": \"fixed\"}");
        JsonNode doneJson = mapper.readTree(doneResult);
        assertThat(doneJson.has("error")).isFalse();
        assertThat(doneJson.get("marked").asInt()).isEqualTo(1);

        // Progress
        String progressResult = tool.execute("{\"action\": \"progress\"}");
        JsonNode progressJson = mapper.readTree(progressResult);
        assertThat(progressJson.has("error")).isFalse();
        assertThat(progressJson.get("fixed").asInt()).isEqualTo(1);
    }

    @Test
    void doneWithInvalidStatus() throws Exception {
        URL resource = getClass().getClassLoader().getResource("sarif/sample.sarif.json");
        String target = new File(resource.getFile()).getAbsolutePath();

        tool.execute("{\"action\": \"load\", \"target\": \"" + escapeJson(target) + "\"}");

        String result = tool.execute("{\"action\": \"done\", \"fps\": [\"abc123\"], \"status\": \"invalid\"}");
        JsonNode json = mapper.readTree(result);

        assertThat(json.has("error")).isTrue();
        assertThat(json.get("error").get("code").asText()).isEqualTo("INVALID_STATUS");
    }

    @Test
    void nextWithScope() throws Exception {
        URL resource = getClass().getClassLoader().getResource("sarif/sample.sarif.json");
        String target = new File(resource.getFile()).getAbsolutePath();

        tool.execute("{\"action\": \"load\", \"target\": \"" + escapeJson(target) + "\"}");

        String result = tool.execute("{\"action\": \"next\", \"scope\": {\"rule\": \"ConstantValue\"}}");
        JsonNode json = mapper.readTree(result);

        assertThat(json.has("error")).isFalse();
        // All returned issues should be ConstantValue
        for (JsonNode issue : json.get("issues")) {
            assertThat(issue.get("rule").asText()).isEqualTo("ConstantValue");
        }
    }

    @Test
    void nextWithExplicitLimit() throws Exception {
        URL resource = getClass().getClassLoader().getResource("sarif/sample.sarif.json");
        String target = new File(resource.getFile()).getAbsolutePath();

        tool.execute("{\"action\": \"load\", \"target\": \"" + escapeJson(target) + "\"}");

        // Service.java has 2 issues, but limit=1 should only return 1
        String result = tool.execute("{\"action\": \"next\", \"limit\": 1}");
        JsonNode json = mapper.readTree(result);

        assertThat(json.has("error")).isFalse();
        assertThat(json.get("issues").size()).isEqualTo(1);
    }

    @Test
    void nextWithInvalidLimitReturnsError() throws Exception {
        URL resource = getClass().getClassLoader().getResource("sarif/sample.sarif.json");
        String target = new File(resource.getFile()).getAbsolutePath();

        tool.execute("{\"action\": \"load\", \"target\": \"" + escapeJson(target) + "\"}");

        String result = tool.execute("{\"action\": \"next\", \"limit\": 0}");
        JsonNode json = mapper.readTree(result);

        assertThat(json.has("error")).isTrue();
        assertThat(json.get("error").get("code").asText()).isEqualTo("INVALID_LIMIT");
    }

    @Test
    void nextWithNegativeLimitReturnsError() throws Exception {
        URL resource = getClass().getClassLoader().getResource("sarif/sample.sarif.json");
        String target = new File(resource.getFile()).getAbsolutePath();

        tool.execute("{\"action\": \"load\", \"target\": \"" + escapeJson(target) + "\"}");

        String result = tool.execute("{\"action\": \"next\", \"limit\": -5}");
        JsonNode json = mapper.readTree(result);

        assertThat(json.has("error")).isTrue();
        assertThat(json.get("error").get("code").asText()).isEqualTo("INVALID_LIMIT");
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

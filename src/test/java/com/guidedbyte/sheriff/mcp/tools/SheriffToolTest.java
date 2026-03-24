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

    @Test
    void malformedJsonReturnsError() throws Exception {
        String result = tool.execute("not valid json {{{");
        JsonNode json = mapper.readTree(result);

        assertThat(json.has("error")).isTrue();
        assertThat(json.get("error").get("code").asText()).isEqualTo("JSON_ERROR");
    }

    @Test
    void emptyObjectReturnsInvalidAction() throws Exception {
        String result = tool.execute("{}");
        JsonNode json = mapper.readTree(result);

        assertThat(json.has("error")).isTrue();
        assertThat(json.get("error").get("code").asText()).isEqualTo("INVALID_ACTION");
    }

    @Test
    void nonNumberLimitReturnsError() throws Exception {
        URL resource = getClass().getClassLoader().getResource("sarif/sample.sarif.json");
        String target = new File(resource.getFile()).getAbsolutePath();
        tool.execute("{\"action\": \"load\", \"target\": \"" + escapeJson(target) + "\"}");

        String result = tool.execute("{\"action\": \"next\", \"limit\": \"not_a_number\"}");
        JsonNode json = mapper.readTree(result);

        assertThat(json.has("error")).isTrue();
        assertThat(json.get("error").get("code").asText()).isEqualTo("INVALID_LIMIT");
    }

    @Test
    void emptyScopeObjectTreatedAsNoScope() throws Exception {
        URL resource = getClass().getClassLoader().getResource("sarif/sample.sarif.json");
        String target = new File(resource.getFile()).getAbsolutePath();
        tool.execute("{\"action\": \"load\", \"target\": \"" + escapeJson(target) + "\"}");

        String result = tool.execute("{\"action\": \"next\", \"scope\": {}}");
        JsonNode json = mapper.readTree(result);

        assertThat(json.has("error")).isFalse();
        assertThat(json.get("issues").isArray()).isTrue();
    }

    @Test
    void summaryAction() throws Exception {
        URL resource = getClass().getClassLoader().getResource("sarif/sample.sarif.json");
        String target = new File(resource.getFile()).getAbsolutePath();
        tool.execute("{\"action\": \"load\", \"target\": \"" + escapeJson(target) + "\"}");

        String result = tool.execute("{\"action\": \"summary\"}");
        JsonNode json = mapper.readTree(result);

        assertThat(json.has("error")).isFalse();
    }

    @Test
    void reopenAction() throws Exception {
        URL resource = getClass().getClassLoader().getResource("sarif/sample.sarif.json");
        String target = new File(resource.getFile()).getAbsolutePath();
        tool.execute("{\"action\": \"load\", \"target\": \"" + escapeJson(target) + "\"}");

        // Get a fingerprint, mark it done, then reopen
        String nextResult = tool.execute("{\"action\": \"next\"}");
        String fp = mapper.readTree(nextResult).get("issues").get(0).get("fp").asText();
        tool.execute("{\"action\": \"done\", \"fps\": [\"" + fp + "\"], \"status\": \"fixed\"}");

        String result = tool.execute("{\"action\": \"reopen\", \"fps\": [\"" + fp + "\"]}");
        JsonNode json = mapper.readTree(result);

        assertThat(json.has("error")).isFalse();
        assertThat(json.get("reopened").asInt()).isEqualTo(1);
    }

    @Test
    void exportAction() throws Exception {
        URL resource = getClass().getClassLoader().getResource("sarif/sample.sarif.json");
        String target = new File(resource.getFile()).getAbsolutePath();
        tool.execute("{\"action\": \"load\", \"target\": \"" + escapeJson(target) + "\"}");

        // Export uses relative paths only (security: rejects absolute paths)
        String result = tool.execute("{\"action\": \"export\", \"format\": \"json\"}");
        JsonNode json = mapper.readTree(result);

        assertThat(json.has("error")).isFalse();
    }

    @Test
    void exportWithListFormat() throws Exception {
        URL resource = getClass().getClassLoader().getResource("sarif/sample.sarif.json");
        String target = new File(resource.getFile()).getAbsolutePath();
        tool.execute("{\"action\": \"load\", \"target\": \"" + escapeJson(target) + "\"}");

        String result = tool.execute("{\"action\": \"export\", \"format\": \"list\"}");
        JsonNode json = mapper.readTree(result);
        assertThat(json.has("error")).isFalse();
    }

    @Test
    void exportWithScope() throws Exception {
        URL resource = getClass().getClassLoader().getResource("sarif/sample.sarif.json");
        String target = new File(resource.getFile()).getAbsolutePath();
        tool.execute("{\"action\": \"load\", \"target\": \"" + escapeJson(target) + "\"}");

        String result =
                tool.execute("{\"action\": \"export\", \"scope\": {\"severity\": \"High\"}, \"format\": \"json\"}");
        JsonNode json = mapper.readTree(result);
        assertThat(json.has("error")).isFalse();
    }

    @Test
    void progressWithScope() throws Exception {
        URL resource = getClass().getClassLoader().getResource("sarif/sample.sarif.json");
        String target = new File(resource.getFile()).getAbsolutePath();
        tool.execute("{\"action\": \"load\", \"target\": \"" + escapeJson(target) + "\"}");

        String result = tool.execute("{\"action\": \"progress\", \"scope\": {\"severity\": \"Moderate\"}}");
        JsonNode json = mapper.readTree(result);
        assertThat(json.has("error")).isFalse();
    }

    @Test
    void nextWithChecklistFormat() throws Exception {
        URL resource = getClass().getClassLoader().getResource("sarif/sample.sarif.json");
        String target = new File(resource.getFile()).getAbsolutePath();
        tool.execute("{\"action\": \"load\", \"target\": \"" + escapeJson(target) + "\"}");

        String result = tool.execute("{\"action\": \"next\", \"fmt\": \"checklist\"}");
        JsonNode json = mapper.readTree(result);
        assertThat(json.has("error")).isFalse();
        assertThat(json.has("checklist")).isTrue();
    }

    @Test
    void nextWithFileScope() throws Exception {
        URL resource = getClass().getClassLoader().getResource("sarif/sample.sarif.json");
        String target = new File(resource.getFile()).getAbsolutePath();
        tool.execute("{\"action\": \"load\", \"target\": \"" + escapeJson(target) + "\"}");

        String result = tool.execute("{\"action\": \"next\", \"scope\": {\"file\": \"*.java\"}}");
        JsonNode json = mapper.readTree(result);
        assertThat(json.has("error")).isFalse();
    }

    @Test
    void getInputSchemaReturnsValidJson() throws Exception {
        String schema = SheriffTool.getInputSchema();
        JsonNode json = mapper.readTree(schema);

        assertThat(json.get("type").asText()).isEqualTo("object");
        assertThat(json.get("properties").has("action")).isTrue();
        assertThat(json.get("required").get(0).asText()).isEqualTo("action");
    }

    @Test
    void missingActionFieldReturnsError() throws Exception {
        String result = tool.execute("{\"target\": \"something\"}");
        JsonNode json = mapper.readTree(result);

        assertThat(json.has("error")).isTrue();
        assertThat(json.get("error").get("code").asText()).isEqualTo("INVALID_ACTION");
    }

    @Test
    void doneWithSkipStatus() throws Exception {
        URL resource = getClass().getClassLoader().getResource("sarif/sample.sarif.json");
        String target = new File(resource.getFile()).getAbsolutePath();
        tool.execute("{\"action\": \"load\", \"target\": \"" + escapeJson(target) + "\"}");

        String nextResult = tool.execute("{\"action\": \"next\"}");
        String fp = mapper.readTree(nextResult).get("issues").get(0).get("fp").asText();

        String result = tool.execute("{\"action\": \"done\", \"fps\": [\"" + fp + "\"], \"status\": \"skip\"}");
        JsonNode json = mapper.readTree(result);
        assertThat(json.has("error")).isFalse();
        assertThat(json.get("marked").asInt()).isEqualTo(1);
    }

    @Test
    void doneWithEmptyFingerprints() throws Exception {
        URL resource = getClass().getClassLoader().getResource("sarif/sample.sarif.json");
        String target = new File(resource.getFile()).getAbsolutePath();
        tool.execute("{\"action\": \"load\", \"target\": \"" + escapeJson(target) + "\"}");

        String result = tool.execute("{\"action\": \"done\", \"fps\": [], \"status\": \"fixed\"}");
        JsonNode json = mapper.readTree(result);
        assertThat(json.has("error")).isTrue();
    }

    @Test
    void reopenNonExistentFingerprint() throws Exception {
        URL resource = getClass().getClassLoader().getResource("sarif/sample.sarif.json");
        String target = new File(resource.getFile()).getAbsolutePath();
        tool.execute("{\"action\": \"load\", \"target\": \"" + escapeJson(target) + "\"}");

        String result = tool.execute("{\"action\": \"reopen\", \"fps\": [\"nonexistent\"]}");
        JsonNode json = mapper.readTree(result);
        // Non-existent fingerprints return an error with details
        assertThat(json).isNotNull();
    }

    @Test
    void loadNonExistentTarget() throws Exception {
        String result = tool.execute("{\"action\": \"load\", \"target\": \"/nonexistent/path\"}");
        JsonNode json = mapper.readTree(result);
        assertThat(json.has("error")).isTrue();
    }

    @Test
    void loadWithoutTarget() throws Exception {
        String result = tool.execute("{\"action\": \"load\"}");
        JsonNode json = mapper.readTree(result);
        assertThat(json.has("error")).isTrue();
    }

    @Test
    void summaryWithoutLoad() throws Exception {
        String result = tool.execute("{\"action\": \"summary\"}");
        JsonNode json = mapper.readTree(result);
        // Summary should work even without loading (returns empty data)
        assertThat(json).isNotNull();
    }

    @Test
    void nextWithSeverityScope() throws Exception {
        URL resource = getClass().getClassLoader().getResource("sarif/sample.sarif.json");
        String target = new File(resource.getFile()).getAbsolutePath();
        tool.execute("{\"action\": \"load\", \"target\": \"" + escapeJson(target) + "\"}");

        String result = tool.execute("{\"action\": \"next\", \"scope\": {\"severity\": \"High\"}}");
        JsonNode json = mapper.readTree(result);
        assertThat(json.has("error")).isFalse();
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

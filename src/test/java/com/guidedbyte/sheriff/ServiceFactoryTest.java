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

import java.io.File;
import java.sql.SQLException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceFactoryTest {

    @TempDir
    File tempDir;

    @Test
    void constructorCreatesAllServices() throws SQLException {
        try (ServiceFactory factory = new ServiceFactory(tempDir.getAbsolutePath())) {
            assertThat(factory.getDatabase()).isNotNull();
            assertThat(factory.getSarifParser()).isNotNull();
            assertThat(factory.getIssueRepository()).isNotNull();
            assertThat(factory.getProgressRepository()).isNotNull();
            assertThat(factory.getBatchService()).isNotNull();
            assertThat(factory.getSheriffTool()).isNotNull();
        }
    }

    @Test
    void closeDoesNotThrow() throws SQLException {
        ServiceFactory factory = new ServiceFactory(tempDir.getAbsolutePath());
        factory.close();
        // No exception = success
    }
}

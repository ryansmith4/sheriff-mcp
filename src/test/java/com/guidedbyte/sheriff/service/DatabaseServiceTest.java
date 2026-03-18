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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseServiceTest {

    @TempDir
    File tempDir;

    private DatabaseService db;

    @BeforeEach
    void setUp() throws SQLException {
        db = new DatabaseService(tempDir.getAbsolutePath());
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (db != null) {
            db.close();
        }
    }

    @Test
    void initializesSchema() throws SQLException {
        // Schema should be initialized automatically
        assertThat(db.getConnection()).isNotNull();
        assertThat(db.getConnection().isClosed()).isFalse();
    }

    @Test
    void setAndGetMeta() throws SQLException {
        db.setMeta("test_key", "test_value");
        String value = db.getMeta("test_key");
        assertThat(value).isEqualTo("test_value");
    }

    @Test
    void getNonExistentMeta() throws SQLException {
        String value = db.getMeta("non_existent");
        assertThat(value).isNull();
    }

    @Test
    void clearIssues() throws SQLException {
        // Should not throw
        db.clearIssues();
    }
}

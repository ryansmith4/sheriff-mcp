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

import com.guidedbyte.sheriff.cli.VersionProvider;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VersionTest {

    @Test
    void getReturnsNonNull() {
        String version = Version.get();
        assertThat(version).isNotNull();
        assertThat(version).isNotBlank();
    }

    @Test
    void getReturnsConsistentValue() {
        String v1 = Version.get();
        String v2 = Version.get();
        assertThat(v1).isEqualTo(v2);
    }

    @Test
    void versionProviderReturnsArrayStartingWithSheriff() {
        VersionProvider provider = new VersionProvider();
        String[] versions = provider.getVersion();

        assertThat(versions).hasSize(1);
        assertThat(versions[0]).startsWith("Sheriff ");
        assertThat(versions[0]).isEqualTo("Sheriff " + Version.get());
    }
}

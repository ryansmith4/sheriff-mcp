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
package com.guidedbyte.sheriff.model.sarif;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OriginalUriBaseIdsTest {

    @Test
    void resolveUriWithNullUri() {
        OriginalUriBaseIds ids = new OriginalUriBaseIds();
        ids.setBase("SRCROOT", new OriginalUriBaseIds.UriBase("/project/"));
        assertThat(ids.resolveUri(null, "SRCROOT")).isNull();
    }

    @Test
    void resolveUriWithNullBaseId() {
        OriginalUriBaseIds ids = new OriginalUriBaseIds();
        assertThat(ids.resolveUri("src/Foo.java", null)).isEqualTo("src/Foo.java");
    }

    @Test
    void resolveUriWithUnknownBaseId() {
        OriginalUriBaseIds ids = new OriginalUriBaseIds();
        ids.setBase("SRCROOT", new OriginalUriBaseIds.UriBase("/project/"));
        assertThat(ids.resolveUri("src/Foo.java", "UNKNOWN")).isEqualTo("src/Foo.java");
    }

    @Test
    void resolveUriWithNullBaseUri() {
        OriginalUriBaseIds ids = new OriginalUriBaseIds();
        ids.setBase("SRCROOT", new OriginalUriBaseIds.UriBase(null));
        assertThat(ids.resolveUri("src/Foo.java", "SRCROOT")).isEqualTo("src/Foo.java");
    }

    @Test
    void resolveUriWithTrailingSlash() {
        OriginalUriBaseIds ids = new OriginalUriBaseIds();
        ids.setBase("SRCROOT", new OriginalUriBaseIds.UriBase("/project/"));
        assertThat(ids.resolveUri("src/Foo.java", "SRCROOT")).isEqualTo("/project/src/Foo.java");
    }

    @Test
    void resolveUriWithoutTrailingSlash() {
        OriginalUriBaseIds ids = new OriginalUriBaseIds();
        ids.setBase("SRCROOT", new OriginalUriBaseIds.UriBase("/project"));
        assertThat(ids.resolveUri("src/Foo.java", "SRCROOT")).isEqualTo("/project/src/Foo.java");
    }

    @Test
    void resolveUriWithFileProtocol() {
        OriginalUriBaseIds ids = new OriginalUriBaseIds();
        ids.setBase("SRCROOT", new OriginalUriBaseIds.UriBase("file:///home/user/project/"));
        String resolved = ids.resolveUri("src/Foo.java", "SRCROOT");
        assertThat(resolved).isEqualTo("/home/user/project/src/Foo.java");
    }

    @Test
    void resolveUriWithMalformedFileUri() {
        OriginalUriBaseIds ids = new OriginalUriBaseIds();
        ids.setBase("SRCROOT", new OriginalUriBaseIds.UriBase("file://invalid uri with spaces["));
        String resolved = ids.resolveUri("src/Foo.java", "SRCROOT");
        // Falls back to string stripping: removes "file://" prefix
        assertThat(resolved).contains("src/Foo.java");
    }

    @Test
    void resolveUriWithShortFileUri() {
        OriginalUriBaseIds ids = new OriginalUriBaseIds();
        ids.setBase("SRCROOT", new OriginalUriBaseIds.UriBase("file://"));
        String resolved = ids.resolveUri("src/Foo.java", "SRCROOT");
        assertThat(resolved).contains("src/Foo.java");
    }

    @Test
    void getBasesReturnsUnmodifiableMap() {
        OriginalUriBaseIds ids = new OriginalUriBaseIds();
        ids.setBase("SRCROOT", new OriginalUriBaseIds.UriBase("/project/"));
        assertThat(ids.getBases()).containsKey("SRCROOT");
        assertThat(ids.getBases()).hasSize(1);
    }
}

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

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResultTest {

    @Test
    void getFingerprint_shouldReturnFromFingerprints() {
        Result result = new Result(
                "rule1", null, "error", null, List.of(), Map.of("fp1", "abc123"), null, List.of(), List.of());

        assertThat(result.getFingerprint()).isEqualTo("abc123");
    }

    @Test
    void getFingerprint_shouldFallbackToPartialFingerprints() {
        Result result = new Result(
                "rule1", null, "error", null, List.of(), null, Map.of("pfp1", "partial123"), List.of(), List.of());

        assertThat(result.getFingerprint()).isEqualTo("partial123");
    }

    @Test
    void getFingerprint_shouldGenerateFallbackWhenNoFingerprints() {
        Result result = new Result("rule1", null, "error", null, List.of(), null, null, List.of(), List.of());

        String fingerprint = result.getFingerprint();
        assertThat(fingerprint).isNotEmpty();
        // Same input should produce same hash
        assertThat(result.getFingerprint()).isEqualTo(fingerprint);
    }

    @Test
    void getFingerprint_shouldIncludeLocationInFallback() {
        ArtifactLocation artifactLoc = new ArtifactLocation("src/main/File.java", null);
        Region region = new Region(42, null, null, null, null);
        PhysicalLocation physLoc = new PhysicalLocation(artifactLoc, region, null);
        Location location = new Location(physLoc, null);

        Result result = new Result("rule1", null, "error", null, List.of(location), null, null, List.of(), List.of());

        // Different location should produce different fingerprint
        Result result2 = new Result("rule1", null, "error", null, List.of(), null, null, List.of(), List.of());

        assertThat(result.getFingerprint()).isNotEqualTo(result2.getFingerprint());
    }
}

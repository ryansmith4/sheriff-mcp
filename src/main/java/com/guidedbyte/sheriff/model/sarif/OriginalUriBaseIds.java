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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maps URI base IDs (like %SRCROOT%) to their resolved locations.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class OriginalUriBaseIds {

    private static final Logger log = LoggerFactory.getLogger(OriginalUriBaseIds.class);

    private final Map<String, UriBase> bases = new HashMap<>();

    @JsonAnySetter
    public void setBase(String key, UriBase value) {
        bases.put(key, value);
    }

    public Map<String, UriBase> getBases() {
        return Collections.unmodifiableMap(bases);
    }

    public String resolveUri(String uri, String uriBaseId) {
        if (uri == null) {
            return null;
        }
        if (uriBaseId == null || !bases.containsKey(uriBaseId)) {
            return uri;
        }
        UriBase base = bases.get(uriBaseId);
        if (base == null) {
            return uri;
        }
        String baseUri = base.uri();
        if (baseUri == null) {
            return uri;
        }
        // Handle file:// URIs by parsing properly
        if (baseUri.startsWith("file://")) {
            try {
                String path = new URI(baseUri).getPath();
                if (path != null) {
                    baseUri = path;
                }
            } catch (URISyntaxException e) {
                log.warn("Malformed file URI '{}', falling back to string stripping", baseUri);
                baseUri = baseUri.substring(Math.min(7, baseUri.length()));
            }
        }
        // Combine base and relative URI
        if (baseUri.endsWith("/")) {
            return baseUri + uri;
        }
        return baseUri + "/" + uri;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UriBase(String uri) {}
}

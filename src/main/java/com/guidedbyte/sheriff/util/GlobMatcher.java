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
package com.guidedbyte.sheriff.util;

import java.nio.file.FileSystems;
import java.nio.file.InvalidPathException;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility for matching file paths against glob patterns.
 */
public final class GlobMatcher {

    private static final Logger log = LoggerFactory.getLogger(GlobMatcher.class);

    private GlobMatcher() {}

    /**
     * Tests if a file path matches a glob pattern.
     *
     * @param pattern glob pattern (e.g., "src/**\/*.java")
     * @param path file path to test
     * @return true if the path matches the pattern
     */
    public static boolean matches(String pattern, String path) {
        if (pattern == null || pattern.isEmpty()) {
            return true;
        }
        if (path == null || path.isEmpty()) {
            return false;
        }

        // Normalize path separators
        String normalizedPath = path.replace("\\", "/");
        String normalizedPattern = pattern.replace("\\", "/");

        // Handle simple wildcard patterns
        if (normalizedPattern.contains("*")) {
            try {
                PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + normalizedPattern);
                return matcher.matches(Paths.get(normalizedPath));
            } catch (PatternSyntaxException | InvalidPathException e) {
                log.warn(
                        "Invalid glob pattern '{}', falling back to simple match: {}",
                        normalizedPattern,
                        e.getMessage());
                return simpleMatch(normalizedPattern, normalizedPath);
            }
        }

        // Exact match or contains
        return normalizedPath.contains(normalizedPattern);
    }

    /**
     * Simple glob matching for basic patterns.
     */
    private static boolean simpleMatch(String pattern, String path) {
        // Reject overly complex patterns to prevent ReDoS
        if (pattern.length() > 200) {
            log.warn("Glob pattern too long ({}), rejecting", pattern.length());
            return false;
        }

        // Convert glob to regex with possessive quantifiers to prevent backtracking
        String regex = pattern.replace(".", "\\.")
                .replace("**", "\0")
                .replace("*", "[^/]*+")
                .replace("\0", ".*+")
                .replace("?", ".");

        return Pattern.compile(regex).matcher(path).matches();
    }

    /**
     * Tests if a rule ID matches a pattern (supports wildcards).
     *
     * @param pattern rule pattern (e.g., "Constant*")
     * @param ruleId rule ID to test
     * @return true if the rule matches the pattern
     */
    public static boolean matchesRule(String pattern, String ruleId) {
        if (pattern == null || pattern.isEmpty()) {
            return true;
        }
        if (ruleId == null) {
            return false;
        }

        // Handle wildcard patterns
        if (pattern.contains("*")) {
            String regex =
                    Arrays.stream(pattern.split("\\*", -1)).map(Pattern::quote).collect(Collectors.joining(".*"));
            return Pattern.compile(regex, Pattern.CASE_INSENSITIVE)
                    .matcher(ruleId)
                    .matches();
        }

        // Exact match (case-insensitive)
        return pattern.equalsIgnoreCase(ruleId);
    }
}

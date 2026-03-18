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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utility for working with issue fingerprints.
 */
public final class FingerprintMatcher {

    private FingerprintMatcher() {}

    /**
     * Generates a fingerprint from rule ID and location.
     * Used as fallback when SARIF doesn't provide fingerprints.
     */
    public static String generateFingerprint(String ruleId, String file, int line, int col) {
        String input = String.format("%s:%s:%d:%d", ruleId, file, line, col);
        return hash(input);
    }

    /**
     * Generates a short fingerprint (16 chars) from a string.
     */
    public static String hash(String input) {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed by the JVM spec; this should never happen
            throw new AssertionError("SHA-256 algorithm not available", e);
        }
        byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            hex.append(String.format("%02x", digest[i]));
        }
        return hex.toString();
    }

    /**
     * Validates a fingerprint format.
     */
    public static boolean isValidFingerprint(String fp) {
        if (fp == null || fp.isEmpty()) {
            return false;
        }
        // Accept hex strings of reasonable length
        return fp.matches("[0-9a-fA-F]{8,64}");
    }
}

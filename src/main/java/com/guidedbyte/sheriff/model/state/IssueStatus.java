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
package com.guidedbyte.sheriff.model.state;

/**
 * Status of an issue in the work queue.
 */
public enum IssueStatus {
    /** Issue has not been processed yet */
    PENDING('P'),

    /** Issue has been fixed */
    FIXED('F'),

    /** Issue was skipped (false positive, won't fix, etc.) */
    SKIPPED('S');

    private final char code;

    IssueStatus(char code) {
        this.code = code;
    }

    public char getCode() {
        return code;
    }

    public static IssueStatus fromCode(char code) {
        for (IssueStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown status code: " + code);
    }

    public static IssueStatus fromString(String str) {
        if (str == null || str.isEmpty()) {
            return PENDING;
        }
        return switch (str.toLowerCase()) {
            case "fixed", "fix", "f" -> FIXED;
            case "skip", "skipped", "s" -> SKIPPED;
            default -> PENDING;
        };
    }
}

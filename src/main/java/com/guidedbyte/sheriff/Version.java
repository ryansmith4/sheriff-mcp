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

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Provides version information for Sheriff.
 * Version is read from version.properties generated at build time.
 */
public final class Version {

    private static final String VERSION;

    static {
        String version = "unknown";
        try (InputStream is = Version.class.getResourceAsStream("/version.properties")) {
            if (is != null) {
                Properties props = new Properties();
                props.load(is);
                version = props.getProperty("version", "unknown");
            }
        } catch (IOException e) {
            // Use System.err because SLF4J may not be initialized during static init
            System.err.println("Warning: could not read version.properties: " + e.getMessage());
        }
        VERSION = version;
    }

    private Version() {}

    /**
     * Returns the current version of Sheriff.
     */
    public static String get() {
        return VERSION;
    }
}

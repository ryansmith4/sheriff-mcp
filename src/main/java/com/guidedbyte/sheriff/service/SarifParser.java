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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import com.guidedbyte.sheriff.model.sarif.SarifReport;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parses SARIF files using Jackson.
 */
public class SarifParser {

    private static final Logger log = LoggerFactory.getLogger(SarifParser.class);

    private final ObjectMapper mapper;

    public SarifParser() {
        this.mapper = new ObjectMapper();
        this.mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Parses a single SARIF file.
     *
     * @param file the SARIF file to parse
     * @return the parsed SARIF report
     * @throws SarifParseException if parsing fails
     */
    public SarifReport parse(File file) throws SarifParseException {
        log.info("Parsing SARIF file: {}", file.getAbsolutePath());

        if (!file.exists()) {
            throw new SarifParseException("SARIF file not found: " + file.getAbsolutePath());
        }

        try {
            SarifReport report = mapper.readValue(file, SarifReport.class);
            if (report == null || report.runs() == null) {
                throw new SarifParseException("Invalid SARIF file: missing required 'runs' array");
            }
            log.info(
                    "Parsed SARIF: {} runs, {} total results",
                    report.runs().size(),
                    report.runs().stream()
                            .mapToInt(r -> r.results() != null ? r.results().size() : 0)
                            .sum());
            return report;
        } catch (IOException e) {
            throw new SarifParseException("Failed to parse SARIF file: " + e.getMessage(), e);
        }
    }

    /**
     * Parses a SARIF file from a path string.
     */
    public SarifReport parse(String path) throws SarifParseException {
        return parse(new File(path));
    }

    /**
     * Computes a hash of the SARIF file for change detection.
     */
    public String computeFileHash(File file) throws SarifParseException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] content = Files.readAllBytes(file.toPath());
            byte[] digest = md.digest(content);
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new SarifParseException("Failed to compute file hash: " + e.getMessage(), e);
        }
    }

    /**
     * Computes a hash of the SARIF file from a path string.
     */
    public String computeFileHash(String path) throws SarifParseException {
        return computeFileHash(new File(path));
    }

    /**
     * Finds SARIF files matching a target path (file or directory).
     */
    public List<Path> findSarifFiles(String target) throws SarifParseException {
        Path path = Path.of(target);
        List<Path> files = new ArrayList<>();

        if (Files.isRegularFile(path)) {
            files.add(path);
        } else if (Files.isDirectory(path)) {
            try (var stream = Files.walk(path, 2)) {
                stream.filter(p -> {
                            String name = p.getFileName().toString();
                            return name.endsWith(".sarif") || name.endsWith(".sarif.json");
                        })
                        .forEach(files::add);
            } catch (IOException e) {
                throw new SarifParseException("Failed to scan directory: " + e.getMessage(), e);
            }
        } else {
            throw new SarifParseException("Target not found: " + target);
        }

        return files;
    }

    /**
     * Exception thrown when SARIF parsing fails.
     */
    public static class SarifParseException extends Exception {
        public SarifParseException(String message) {
            super(message);
        }

        public SarifParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

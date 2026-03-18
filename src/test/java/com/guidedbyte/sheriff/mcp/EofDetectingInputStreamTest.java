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
package com.guidedbyte.sheriff.mcp;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EofDetectingInputStreamTest {

    @Test
    void singleByteReadPassesThroughDataCorrectly() throws IOException {
        byte[] data = {10, 20, 30};
        AtomicInteger callbackCount = new AtomicInteger();
        try (EofDetectingInputStream stream =
                new EofDetectingInputStream(new ByteArrayInputStream(data), callbackCount::incrementAndGet)) {
            assertThat(stream.read()).isEqualTo(10);
            assertThat(stream.read()).isEqualTo(20);
            assertThat(stream.read()).isEqualTo(30);
        }
    }

    @Test
    void bulkReadPassesThroughDataCorrectly() throws IOException {
        byte[] data = {1, 2, 3, 4, 5};
        AtomicInteger callbackCount = new AtomicInteger();
        try (EofDetectingInputStream stream =
                new EofDetectingInputStream(new ByteArrayInputStream(data), callbackCount::incrementAndGet)) {
            byte[] buf = new byte[5];
            int bytesRead = stream.read(buf, 0, buf.length);
            assertThat(bytesRead).isEqualTo(5);
            assertThat(buf).containsExactly(1, 2, 3, 4, 5);
        }
    }

    @Test
    void eofOnSingleByteReadFiresCallback() throws IOException {
        AtomicInteger callbackCount = new AtomicInteger();
        try (EofDetectingInputStream stream = new EofDetectingInputStream(
                new ByteArrayInputStream(new byte[] {42}), callbackCount::incrementAndGet)) {
            assertThat(stream.read()).isEqualTo(42);
            assertThat(callbackCount.get()).isZero();

            int eof = stream.read();
            assertThat(eof).isEqualTo(-1);
            assertThat(callbackCount.get()).isEqualTo(1);
        }
    }

    @Test
    void eofOnBulkReadFiresCallback() throws IOException {
        AtomicInteger callbackCount = new AtomicInteger();
        try (EofDetectingInputStream stream =
                new EofDetectingInputStream(new ByteArrayInputStream(new byte[] {7}), callbackCount::incrementAndGet)) {
            byte[] buf = new byte[8];
            int bytesRead = stream.read(buf, 0, buf.length);
            assertThat(bytesRead).isEqualTo(1);
            assertThat(callbackCount.get()).isZero();

            int eofRead = stream.read(buf, 0, buf.length);
            assertThat(eofRead).isEqualTo(-1);
            assertThat(callbackCount.get()).isEqualTo(1);
        }
    }

    @Test
    void callbackFiresExactlyOnceWithMultipleEofReads() throws IOException {
        AtomicInteger callbackCount = new AtomicInteger();
        try (EofDetectingInputStream stream =
                new EofDetectingInputStream(InputStream.nullInputStream(), callbackCount::incrementAndGet)) {
            // First EOF via single-byte read
            assertThat(stream.read()).isEqualTo(-1);
            assertThat(callbackCount.get()).isEqualTo(1);

            // Second EOF via single-byte read
            assertThat(stream.read()).isEqualTo(-1);
            assertThat(callbackCount.get()).isEqualTo(1);

            // Third EOF via bulk read
            byte[] buf = new byte[4];
            assertThat(stream.read(buf, 0, buf.length)).isEqualTo(-1);
            assertThat(callbackCount.get()).isEqualTo(1);
        }
    }

    @Test
    void emptyStreamFiresCallbackOnFirstRead() throws IOException {
        AtomicInteger callbackCount = new AtomicInteger();
        try (EofDetectingInputStream stream =
                new EofDetectingInputStream(new ByteArrayInputStream(new byte[0]), callbackCount::incrementAndGet)) {
            int result = stream.read();
            assertThat(result).isEqualTo(-1);
            assertThat(callbackCount.get()).isEqualTo(1);
        }
    }

    @Test
    void dataFlowsThroughWithoutCorruption() throws IOException {
        byte[] data = new byte[256];
        for (int i = 0; i < 256; i++) {
            data[i] = (byte) i;
        }
        AtomicInteger callbackCount = new AtomicInteger();
        try (EofDetectingInputStream stream =
                new EofDetectingInputStream(new ByteArrayInputStream(data), callbackCount::incrementAndGet)) {
            byte[] buf = new byte[256];
            int totalRead = 0;
            int n;
            while ((n = stream.read(buf, totalRead, buf.length - totalRead)) != -1) {
                totalRead += n;
            }
            assertThat(totalRead).isEqualTo(256);
            assertThat(buf).isEqualTo(data);
            assertThat(callbackCount.get()).isEqualTo(1);
        }
    }
}

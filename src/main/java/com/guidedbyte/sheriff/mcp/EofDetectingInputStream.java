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

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * InputStream wrapper that detects EOF (stdin pipe closed) and fires a callback exactly once.
 * Used to detect when the MCP client disconnects.
 */
public class EofDetectingInputStream extends FilterInputStream {

    private final Runnable onEof;
    private final AtomicBoolean eofFired = new AtomicBoolean(false);

    public EofDetectingInputStream(InputStream in, Runnable onEof) {
        super(in);
        this.onEof = onEof;
    }

    @Override
    public int read() throws IOException {
        int b = super.read();
        if (b == -1) {
            fireEof();
        }
        return b;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int n = super.read(b, off, len);
        if (n == -1) {
            fireEof();
        }
        return n;
    }

    private void fireEof() {
        if (eofFired.compareAndSet(false, true)) {
            onEof.run();
        }
    }
}

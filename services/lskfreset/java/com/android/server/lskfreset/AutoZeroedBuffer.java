/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.lskfreset;

import java.util.Arrays;

/**
 * A helper class that manages a byte buffer which is automatically zeroed out when closed.
 *
 * <p>This is useful for handling sensitive data such as cryptographic keys, ensuring that they do
 * not remain in memory longer than necessary.
 *
 * <p>It is important to not hold onto references to the buffer outside of this class, as the buffer
 * will be zeroed out when the closable is called. Ideally this class should be used in a
 * try-with-resources block to ensure proper and timely cleanup.
 */
class AutoZeroedBuffer implements AutoCloseable {
    private final byte[] mBuffer;

    /**
     * Creates a new AutoZeroedBuffer of the specified size.
     *
     * @param size The size of the buffer to allocate, in bytes.
     */
    AutoZeroedBuffer(int size) {
        mBuffer = new byte[size];
    }

    /**
     * Creates a new AutoZeroedBuffer that will manage the provided byte buffer.
     *
     * @param buffer The byte buffer to manage. Note that this captures a reference, not a copy.
     */
    AutoZeroedBuffer(byte[] buffer) {
        mBuffer = buffer;
    }

    /**
     * Returns the underlying byte buffer.
     *
     * <p>In an ideal world the raw buffer would not be exposed at all, but in practice almost all
     * raw byte-oriented APIs in Java require or return byte[] objects and so it is impractial to
     * try and abstract this away.
     *
     * @return The byte buffer being managed by this resource.
     */
    public byte[] getBuffer() {
        return mBuffer;
    }

    @Override
    public void close() {
        // Zero out the buffer to clear sensitive data from memory. Unfortunately, we do not
        // currently have a way to guarantee that the runtime actually does this, and for example
        // does not elide the writes or make copies under the covers, but this is the best we can
        // do for now. If a more robust mechanism for blanking memory becomes available we can
        // switch to that in a (hopefully) transparent way.
        Arrays.fill(mBuffer, (byte) 0);
    }
}

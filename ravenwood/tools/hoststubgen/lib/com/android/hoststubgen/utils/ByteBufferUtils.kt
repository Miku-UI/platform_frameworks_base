/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.hoststubgen.utils

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import kotlin.math.min

class ByteBufferChannel(
    private val buffer: ByteBuffer
) : SeekableByteChannel {

    override fun close() {}

    override fun isOpen() = true

    override fun read(dst: ByteBuffer): Int {
        val size = min(buffer.remaining(), dst.remaining())
        buffer.limit(buffer.position() + size)
        dst.put(buffer)
        // Restore limit back to capacity
        buffer.limit(buffer.capacity())
        return size
    }

    override fun position() = buffer.position().toLong()

    override fun position(newPosition: Long): SeekableByteChannel {
        buffer.position(newPosition.toInt())
        return this
    }

    override fun size(): Long = buffer.capacity().toLong()

    override fun write(src: ByteBuffer): Int {
        throw UnsupportedOperationException()
    }

    override fun truncate(size: Long): SeekableByteChannel {
        throw UnsupportedOperationException()
    }
}

class ByteBufferInputStream(
    private val buffer: ByteBuffer
) : InputStream() {
    override fun read(): Int {
        return if (buffer.hasRemaining()) {
            buffer.get().toInt() and 0xFF
        } else {
            -1
        }
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (!buffer.hasRemaining()) return -1
        val size = min(buffer.remaining(), len)
        buffer.get(b, off, size)
        return size
    }

    override fun available() = buffer.remaining()
}

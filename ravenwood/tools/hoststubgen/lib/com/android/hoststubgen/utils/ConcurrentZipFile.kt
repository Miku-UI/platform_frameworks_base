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

import com.android.hoststubgen.log
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream
import java.util.zip.ZipEntry
import kotlin.io.path.Path
import kotlin.math.min
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipFile

// Enable to debug concurrency issues
const val DISABLE_PARALLELISM = false
const val DEFAULT_SHARD_COUNT = 20
const val MINIMUM_BATCH_SIZE = 100

/**
 * Whether to skip compression when adding processed entries back to a zip file.
 */
private const val SKIP_COMPRESSION = false

class DecompressionStream(ins: InputStream) : InflaterInputStream(ins, Inflater(true)) {
    override fun close() {
        super.close()
        inf.end()
    }
}

sealed class ZipEntryData {
    abstract val entry: ZipArchiveEntry
    abstract val data: ByteArray
    abstract fun writeTo(out: ZipArchiveOutputStream)

    val name: String get() = entry.name

    abstract class RawData : ZipEntryData() {
        protected abstract fun rawStream(): InputStream
        protected fun decompressStream(): InputStream = DecompressionStream(rawStream())
        final override fun writeTo(out: ZipArchiveOutputStream) {
            out.addRawArchiveEntry(entry, rawStream())
        }
    }

    class Entry(
        override val entry: ZipArchiveEntry,
        private val zipBytes: ByteBuffer
    ) : RawData() {
        override val data: ByteArray get() =
            if (entry.method == ZipEntry.STORED) {
                rawStream()
            } else {
                decompressStream()
            }.readAllBytes()

        override fun rawStream(): InputStream {
            val offset = entry.dataOffset.toInt()
            val size = entry.compressedSize.toInt()
            return ByteBufferInputStream(zipBytes.slice(offset, size))
        }
    }

    class Bytes(name: String, override val data: ByteArray) : ZipEntryData() {
        override val entry = ZipArchiveEntry(name)

        init {
            entry.method = 0
            entry.size = data.size.toLong()
            entry.crc = CRC32().apply { update(data) }.value
        }

        override fun writeTo(out: ZipArchiveOutputStream) {
            out.putArchiveEntry(entry)
            out.write(data)
            out.closeArchiveEntry()
        }
    }

    class Compressed(name: String, data: ByteArray) : RawData() {
        override val entry = ZipArchiveEntry(name)
        override val data: ByteArray get() = decompressStream().readAllBytes()

        private val compressed: ByteArray

        init {
            val compressor = Deflater(Deflater.DEFAULT_COMPRESSION, true)
            val buffer = ByteArray(1024 * 1024)
            val bos = ByteArrayOutputStream()

            compressor.setInput(data)
            compressor.finish()
            while (!compressor.finished()) {
                val size = compressor.deflate(buffer)
                bos.write(buffer, 0, size)
            }
            compressor.end()
            compressed = bos.toByteArray()

            entry.method = ZipEntry.DEFLATED
            entry.size = data.size.toLong()
            entry.compressedSize = compressed.size.toLong()
            entry.crc = CRC32().apply { update(data) }.value
        }

        override fun rawStream() = ByteArrayInputStream(compressed)
    }

    companion object {
        fun fromBytes(name: String, data: ByteArray): ZipEntryData {
            return if (SKIP_COMPRESSION) {
                Bytes(name, data)
            } else {
                Compressed(name, data)
            }
        }
    }
}

class ConcurrentListMapper<T>(val list: MutableList<T?>) {
    val currentIndex = AtomicInteger()

    inline fun process(mapper: (T) -> T?) {
        while (true) {
            val idx = currentIndex.getAndIncrement()
            if (idx < list.size) {
                list[idx]?.let { list[idx] = mapper(it) }
                continue
            }
            break
        }
    }
}

class ConcurrentZipFile(
    val fileName: String,
    parallelism: Int,
) {
    val entries: MutableList<ZipEntryData?>
    val executor: Executor
    val shardCount: Int

    init {
        val mappedBytes = FileChannel.open(Path(fileName)).use { ch ->
            ch.map(FileChannel.MapMode.READ_ONLY, 0, ch.size())
        }
        entries = ZipFile(ByteBufferChannel(mappedBytes.slice())).use { zf ->
            zf.entries.asSequence()
                .map { ZipEntryData.Entry(it, mappedBytes) }
                .toMutableList()
        }
        if (DISABLE_PARALLELISM) {
            shardCount = 1
            // Directly run on the same thread as the caller
            executor = Executor { r -> r.run() }
        } else {
            val count = min(parallelism, Runtime.getRuntime().availableProcessors())
            shardCount = min(count, entries.size / MINIMUM_BATCH_SIZE + 1)
            executor = Executors.newFixedThreadPool(shardCount)
        }
    }

    inline fun forEach(action: (ZipEntryData) -> Unit) {
        entries.asSequence().filterNotNull().forEach(action)
    }

    inline fun parallelForEach(crossinline action: (ZipEntryData) -> Unit) {
        forEachThread { entries ->
            entries.process { entry ->
                action(entry)
                entry
            }
        }
    }

    inline fun forEachThread(
        crossinline action: (ConcurrentListMapper<ZipEntryData>) -> Unit
    ) {
        val latch = CountDownLatch(shardCount)
        val mapper = ConcurrentListMapper(entries)
        val exception = AtomicReference<Throwable>(null)
        repeat(shardCount) {
            executor.execute {
                try {
                    action(mapper)
                } catch (e: Throwable) {
                    exception.compareAndSet(null, e)
                } finally {
                    log.flush()
                    latch.countDown()
                }
            }
        }
        latch.await(5, TimeUnit.MINUTES)
        exception.get()?.let { throw it }
    }

    fun write(out: String, timeCollector: ((Double) -> Unit)? = null) {
        var writeTime = .0

        ZipArchiveOutputStream(FileOutputStream(out).buffered(1024 * 1024)).use { zos ->
            forEach { entry ->
                writeTime += log.nTime { entry.writeTo(zos) }
            }
        }

        timeCollector?.invoke(writeTime)
    }
}

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
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.collections.mutableListOf
import kotlin.io.path.isDirectory

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

sealed class ZipEntryData(
    // Where the entry originally came from. Either a JAR/ZIP file name,
    // or a directory name from the --in-dir option.
    val container: String,
) {
    abstract val entry: ZipArchiveEntry
    abstract val data: ByteArray
    abstract fun writeTo(out: ZipArchiveOutputStream)

    val name: String get() = entry.name

    abstract class RawData(container: String) : ZipEntryData(container) {
        protected abstract fun rawStream(): InputStream
        protected fun decompressStream(): InputStream = DecompressionStream(rawStream())
        final override fun writeTo(out: ZipArchiveOutputStream) {
            out.addRawArchiveEntry(entry, rawStream())
        }
    }

    class Entry(
        container: String,
        override val entry: ZipArchiveEntry,
        private val zipBytes: ByteBuffer
    ) : RawData(container) {
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

    class Bytes(
        container: String,
        name: String,
        override val data: ByteArray,
    ) : ZipEntryData(container) {
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

    class Compressed(
        container: String,
        name: String,
        data: ByteArray,
    ) : RawData(container) {
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
        fun fromBytes(sourceFilename: String, name: String, data: ByteArray): ZipEntryData {
            return if (SKIP_COMPRESSION) {
                Bytes(sourceFilename, name, data)
            } else {
                Compressed(sourceFilename, name, data)
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

/**
 * Reads ZIP files and/or directories and collect their contents as [ZipEntryData].
 *
 * If multiple inputs have the same entry names, we keep only the first one seen.
 */
class ZipEntryCollector {
    private val result: MutableList<ZipEntryData> = mutableListOf()
    private val knownNames: MutableSet<String> = mutableSetOf()

    /**
     * Add ZIP entries from a zip file, or a directory. Can be called multiple times.
     */
    fun addZipOrDirectory(path: String) {
        if (Paths.get(path).isDirectory()) {
            addFromDirectory(path)
        } else {
            addFromZip(path)
        }
    }

    /**
     * Return all the accumulated entries so far.
     */
    fun getEntries(): List<ZipEntryData> {
        return result
    }

    /** Read a single ZIP file and add its entries to [result]. */
    private fun addFromZip(filename: String) {
        log.v("Reading zip file: %s...", filename)
        val mappedBytes = FileChannel.open(Path(filename)).use { ch ->
            ch.map(FileChannel.MapMode.READ_ONLY, 0, ch.size())
        }
        ZipFile(ByteBufferChannel(mappedBytes.slice())).use { zf ->
            zf.entries.asSequence().forEach { entry ->
                if (knownNames.contains(entry.name)) {
                    log.w("  Skip duplicate entry \"%s\" in file %s", entry.name, filename)
                    return@forEach
                }
                log.d("  Found entry \"%s\" in file %s", entry.name, filename)
                result.add(ZipEntryData.Entry(filename, entry, mappedBytes))
                knownNames.add(entry.name)
            }
        }
    }

    /** Find files in a given directory and add them to [result]. */
    private fun addFromDirectory(dirName: String) {
        log.v("Reading directory: %s...", dirName)
        val topDir = File(dirName)
        topDir.walk().forEach { file ->
            if (file.isDirectory) {
                return@forEach
            }
            val relative = file.relativeTo(topDir).path
            if (knownNames.contains(relative)) {
                log.w("  Skip duplicate entry \"%s\" in directory %s", relative, dirName)
                return@forEach
            }
            log.d("  Found file \"%s\" in directory %s", relative, dirName)
            val mappedBytes = FileChannel.open(Path(file.absolutePath)).use { ch ->
                ch.map(FileChannel.MapMode.READ_ONLY, 0, ch.size())
            }
            val bytes = ByteArray(mappedBytes.remaining())
            mappedBytes.get(bytes)
            result.add(ZipEntryData.Bytes(dirName, relative, bytes))
            knownNames.add(relative)
        }
    }
}

/**
 * Provides a logic to process multip "zip entries" concurrently and write them
 * into a single zip file.
 */
class ConcurrentZipProcessor(
    private val jarFilesOrDirectories: List<String>,
    parallelism: Int,
) {
    private val entries = mutableListOf<ZipEntryData?>()
    private val executor: Executor
    private val shardCount: Int

    val sourceFiles: List<String> get() = jarFilesOrDirectories

    init {
        val reader = ZipEntryCollector()
        jarFilesOrDirectories.forEach { reader.addZipOrDirectory(it) }
        entries.addAll(reader.getEntries())

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

    fun forEach(action: (ZipEntryData) -> Unit) {
        entries.asSequence().filterNotNull().forEach(action)
    }

    fun parallelForEach(action: (ZipEntryData) -> Unit) {
        forEachThread { entries ->
            entries.process { entry ->
                action(entry)
                entry
            }
        }
    }

    fun forEachThread(
        action: (ConcurrentListMapper<ZipEntryData>) -> Unit
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

    fun write(out: String, metadaata: String? = null, timeCollector: ((Double) -> Unit)? = null) {
        var writeTime = .0

        val metadataFilename = "hoststubgen.txt"

        // Make sure the output directory exists.
        Files.createDirectories(Paths.get(out).parent)

        ZipArchiveOutputStream(FileOutputStream(out).buffered(1024 * 1024)).use { zos ->
            if (metadaata != null) {
                zos.putArchiveEntry(ZipArchiveEntry(metadataFilename))
                zos.write(metadaata.toByteArray())
                zos.closeArchiveEntry()
            }
            forEach { entry ->
                if (entry.name == metadataFilename) {
                    return@forEach
                }
                writeTime += log.nTime { entry.writeTo(zos) }
            }
        }

        timeCollector?.invoke(writeTime)
    }
}

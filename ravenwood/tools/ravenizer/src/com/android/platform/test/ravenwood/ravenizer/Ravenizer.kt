/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.platform.test.ravenwood.ravenizer

import com.android.hoststubgen.GeneralUserErrorException
import com.android.hoststubgen.HostStubGenClassProcessor
import com.android.hoststubgen.HostStubGenStats
import com.android.hoststubgen.asm.ClassNodes
import com.android.hoststubgen.asm.zipEntryNameToClassName
import com.android.hoststubgen.executableName
import com.android.hoststubgen.getJarMetadata
import com.android.hoststubgen.log
import com.android.hoststubgen.utils.ConcurrentZipProcessor
import com.android.hoststubgen.utils.ZipEntryData
import com.android.platform.test.ravenwood.ravenizer.adapter.RunnerRewritingAdapter
import java.util.concurrent.atomic.AtomicInteger
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.util.CheckClassAdapter

/**
 * Various stats on Ravenizer.
 */
data class RavenizerStats(
    /** Time took to validate the classes */
    var validationTime: Double = .0,

    /** # of *.class files that have been processed. */
    var processedClasses: AtomicInteger = AtomicInteger(),
) : HostStubGenStats() {
    override fun toString(): String {
        return """
            RavenizerStats {
              totalTime=$totalTime,
              loadStructureTime=$loadStructureTime,
              validationTime=$validationTime,
              totalProcessTime=$totalProcessTime,
              totalWriteTime=$totalWriteTime,
              totalEntries=$totalEntries,
              totalClasses=$totalClasses,
              processedClasses=$processedClasses,
            }
            """.trimIndent()
    }
}

/**
 * Main class.
 */
class Ravenizer {
    fun run(options: RavenizerOptions) {
        val stats = RavenizerStats()

        stats.totalTime = log.nTime {
            val inJar = ConcurrentZipProcessor(options.inJars, options.numShards.get)
            val allClasses = ClassNodes.loadClassStructures(inJar, String::shouldProcess) {
                stats.loadStructureTime = it
            }
            process(inJar, options, allClasses, stats)
        }
        log.v(stats.toString())
    }

    private fun process(
        inJars: ConcurrentZipProcessor,
        options: RavenizerOptions,
        allClasses: ClassNodes,
        stats: RavenizerStats,
    ) {
        if (includeUnsupportedMockito(allClasses)) {
            log.w("Unsupported Mockito detected in ${inJars.sourceFiles}!")
        }

        stats.totalProcessTime = log.vTime("$executableName processing ${inJars.sourceFiles}") {
            inJars.forEachThread { entries ->
                val processor = HostStubGenClassProcessor(options, allClasses)
                entries.process { entry ->
                    stats.totalEntries.incrementAndGet()
                    if (entry.name.endsWith(".dex")) {
                        // Seems like it's an ART jar file. We can't process it.
                        // It's a fatal error.
                        throw GeneralUserErrorException(
                            "${entry.container} is not a desktop jar file. It contains a *.dex file."
                        )
                    }
                    if (entry.name.startsWith("META-INF/")) {
                        // Do not touch any files in it.
                        return@process entry
                    }

                    if (options.stripMockito.get && entry.name.isMockitoFile()) {
                        // Remove this entry
                        return@process null
                    }

                    val className = zipEntryNameToClassName(entry.name)
                    if (className != null) {
                        stats.totalClasses.incrementAndGet()
                    }

                    if (className?.shouldBypass() == false) {
                        stats.processedClasses.incrementAndGet()

                        val entryInfo = processor.applyFilterOnClass(entry)
                            ?: return@process null

                        log.v("Creating class: %s Policy: %s",
                            entryInfo.classInternalName, entryInfo.policy)

                        var classBytes = entry.data
                        if (RunnerRewritingAdapter.shouldProcess(processor.allClasses, className)) {
                            classBytes = ravenizeSingleClass(classBytes, processor.allClasses)
                        }
                        classBytes = processor.processClassBytecode(classBytes)
                        // Create a new entry
                        ZipEntryData.fromBytes(
                            entry.container,
                            entryInfo.renamedEntryName,
                            classBytes,
                        )
                    } else {
                        // Do not process and return the original entry
                        entry
                    }
                }
            }
        }
        val out = options.outJar.get
        val meta = getJarMetadata("ravenizer", inJars.sourceFiles, out)
        inJars.write(out, meta) { writeTime ->
            stats.totalWriteTime += writeTime
        }
    }

    private fun ravenizeSingleClass(
        input: ByteArray,
        allClasses: ClassNodes,
    ): ByteArray {
        val flags = ClassWriter.COMPUTE_MAXS
        val cw = ClassWriter(flags)
        var outVisitor: ClassVisitor = cw

        val enableChecker = false
        if (enableChecker) {
            outVisitor = CheckClassAdapter(outVisitor)
        }

        outVisitor = RunnerRewritingAdapter(allClasses, outVisitor)

        val cr = ClassReader(input)
        cr.accept(outVisitor, ClassReader.EXPAND_FRAMES)

        return cw.toByteArray()
    }
}

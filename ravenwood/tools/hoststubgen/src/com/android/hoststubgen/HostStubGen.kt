/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.hoststubgen

import com.android.hoststubgen.asm.ClassNodes
import com.android.hoststubgen.filters.printAsTextPolicy
import com.android.hoststubgen.utils.ConcurrentZipFile
import com.android.hoststubgen.utils.ZipEntryData
import java.io.PrintWriter

/**
 * Actual main class.
 */
class HostStubGen(val options: HostStubGenOptions) {
    fun run() {
        val errors = HostStubGenErrors()
        val inJar = ConcurrentZipFile(options.inJar.get, options.numShards.get)
        val stats = HostStubGenStats()

        lateinit var allClasses: ClassNodes

        stats.totalTime = log.nTime {
            // Load all classes.
            allClasses = ClassNodes.loadClassStructures(inJar) {
                stats.loadStructureTime = it
            }

            convert(inJar, allClasses, options, errors, stats)
        }
        log.i(stats.toString())

        // Dump the classes, if specified.
        options.inputJarDumpFile.ifSet {
            log.iTime("Dump file created at $it") {
                PrintWriter(it).use { pw -> allClasses.dump(pw) }
            }
        }

        options.inputJarAsKeepAllFile.ifSet {
            log.iTime("Dump file created at $it") {
                PrintWriter(it).use { pw ->
                    allClasses.forEach { classNode ->
                        printAsTextPolicy(pw, classNode)
                    }
                }
            }
        }
    }

    /**
     * Convert a JAR file.
     */
    private fun convert(
        inJar: ConcurrentZipFile,
        allClasses: ClassNodes,
        options: HostStubGenOptions,
        errors: HostStubGenErrors,
        stats: HostStubGenStats,
    ) {
        log.v("Converting %s into %s ...", inJar.fileName, options.outJar.get)
        log.v("ASM CheckClassAdapter is %s",
            if (options.enableClassChecker.get) "enabled" else "disabled")

        stats.totalProcessTime = log.nTime {
            log.withIndent {
                inJar.forEachThread { entries ->
                    // Create a new processor for each thread
                    val processor = HostStubGenClassProcessor(options, allClasses, errors)
                    entries.process { entry ->
                        stats.totalEntries.incrementAndGet()
                        convertSingleEntry(entry, processor, stats)
                    }
                }
            }
        }

        options.outJar.get?.let {
            inJar.write(it) { time -> stats.totalWriteTime = time }
            log.d("Created: $it")
        }
    }

    /**
     * Convert a single ZIP entry, which may or may not be a class file.
     */
    private fun convertSingleEntry(
        entry: ZipEntryData,
        processor: HostStubGenClassProcessor,
        stats: HostStubGenStats,
    ): ZipEntryData? {
        log.d("Entry: %s", entry.name)
        log.withIndent {
            val name = entry.name

            // Just ignore all the directories. (TODO: make sure it's okay)
            if (name.endsWith("/")) {
                return null
            }

            // If it's a class, convert it.
            if (name.endsWith(".class")) {
                stats.totalClasses.incrementAndGet()
                return processSingleClass(entry, processor)
            }

            // Handle other file types...

            // - *.uau seems to contain hidden API information.
            // -  *_compat_config.xml is also about compat-framework.
            if (name.endsWith(".uau") || name.endsWith("_compat_config.xml")) {
                log.d("Not needed: %s", entry.name)
                return null
            }

            // Unknown type, we just copy it to both output zip files.
            log.v("Copy: %s", entry.name)
            return entry
        }
    }

    /**
     * Convert a single class.
     */
    private fun processSingleClass(
        entry: ZipEntryData,
        processor: HostStubGenClassProcessor
    ): ZipEntryData? {
        val entryInfo = processor.applyFilterOnClass(entry) ?: return null

        log.v("Creating class: %s Policy: %s", entryInfo.classInternalName, entryInfo.policy)
        log.withIndent {
            val data = processor.processClassBytecode(entry.data)
            return ZipEntryData.fromBytes(entryInfo.renamedEntryName, data)
        }
    }
}

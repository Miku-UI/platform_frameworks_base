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
package com.android.platform.test.ravenwood.ravenhelper.stats

import com.android.hoststubgen.HostStubGenClassProcessor
import com.android.hoststubgen.HostStubGenErrors
import com.android.hoststubgen.asm.ClassNodes
import com.android.hoststubgen.filters.printAsTextPolicy
import com.android.hoststubgen.log
import com.android.hoststubgen.utils.ConcurrentZipFile
import com.android.hoststubgen.utils.DEFAULT_SHARD_COUNT
import com.android.platform.test.ravenwood.ravenhelper.SubcommandHandler
import java.io.PrintWriter

class StatsHandler : SubcommandHandler {
    override fun handle(args: List<String>) {
        val options = StatsOptions().apply { parseArgs(args) }
        log.i("Options: $options")

        val errors = HostStubGenErrors()

        val inJar = ConcurrentZipFile(options.inJar.get, DEFAULT_SHARD_COUNT)

        // Load all classes.
        val allClasses = ClassNodes.loadClassStructures(inJar)

        val filter = HostStubGenClassProcessor.buildFilter(options, allClasses)

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

        options.apiListFile.ifSet {
            log.iTime("API list file created at $it") {
                PrintWriter(it).use { pw ->
                    // TODO, when dumping a jar that's not framework-minus-apex.jar, we need to feed
                    // framework-minus-apex.jar so that we can dump inherited methods from it.
                    ApiDumper(pw, allClasses, null, filter, errors).dump()
                }
            }
        }
    }
}

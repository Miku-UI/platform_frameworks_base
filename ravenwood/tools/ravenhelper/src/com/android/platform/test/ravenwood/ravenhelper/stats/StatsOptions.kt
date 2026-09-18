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

import com.android.hoststubgen.HostStubGenClassProcessorOptions
import com.android.hoststubgen.utils.ArgIterator
import com.android.hoststubgen.utils.SetOnce

class StatsOptions(
    val inputJarDumpFile: SetOnce<String?> = SetOnce(null),

    val inputJarAsKeepAllFile: SetOnce<String?> = SetOnce(null),

    val apiListFile: SetOnce<String?> = SetOnce(null),
) : HostStubGenClassProcessorOptions() {

    override fun parseOption(option: String, args: ArgIterator): Boolean {
        // Define some shorthands...
        fun nextArg(): String = args.nextArgRequired(option)

        when (option) {
            // TODO: Write help
            "-h", "--help" -> TODO("Help is not implemented yet")

            "--gen-input-dump-file" -> inputJarDumpFile.set(nextArg())
            "--gen-keep-all-file" -> inputJarAsKeepAllFile.set(nextArg())

            "--supported-api-list-file" -> apiListFile.set(nextArg())

            else -> return super.parseOption(option, args)
        }

        return true
    }

    override fun dumpFields(): String {
        return """
            inputJarDumpFile=$inputJarDumpFile,
            inputJarAsKeepAllFile=$inputJarAsKeepAllFile,
            apiListFile=$apiListFile,
        """.trimIndent() + '\n' + super.dumpFields()
    }
}

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

import com.android.hoststubgen.ArgumentsException
import com.android.hoststubgen.HostStubGenClassProcessorOptions
import com.android.hoststubgen.utils.ArgIterator
import com.android.hoststubgen.utils.DEFAULT_SHARD_COUNT
import com.android.hoststubgen.utils.IntSetOnce
import com.android.hoststubgen.utils.SetOnce

class RavenizerOptions(
    /** Output jar file */
    val outJar: SetOnce<String> = SetOnce(""),

    /** Whether to remove mockito and dexmaker classes. */
    val stripMockito: SetOnce<Boolean> = SetOnce(false),

    val numShards: IntSetOnce = IntSetOnce(DEFAULT_SHARD_COUNT),
) : HostStubGenClassProcessorOptions() {

    override fun parseOption(option: String, args: ArgIterator): Boolean {
        fun nextArg(): String = args.nextArgRequired(option)

        when (option) {
            // TODO: Write help
            "-h", "--help" -> TODO("Help is not implemented yet")

            "--out-jar" -> outJar.set(nextArg())

            "--strip-mockito" -> stripMockito.set(true)
            "--no-strip-mockito" -> stripMockito.set(false)

            "--num-shards" -> numShards.set(nextArg()).also {
                if (it < 1) {
                    throw ArgumentsException("$option must be positive integer")
                }
            }

            else -> return super.parseOption(option, args)
        }

        return true
    }

    override fun checkArgs() {
        super.checkArgs()
        if (!outJar.isSet) {
            throw ArgumentsException("Required option missing: --out-jar")
        }
    }

    override fun dumpFields(): String {
        return """
            outJar=$outJar,
            stripMockito=$stripMockito,
            numShards=$numShards,
        """.trimIndent() + '\n' + super.dumpFields()
    }
}

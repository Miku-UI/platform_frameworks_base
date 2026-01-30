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
package com.android.hoststubgen

import com.android.hoststubgen.asm.ClassNodes
import java.util.concurrent.atomic.AtomicInteger

/**
 * Various stats of HostStubGen processing.
 */
open class HostStubGenStats(
    /** Total end-to-end time. */
    var totalTime: Double = .0,

    /** Time took to build [ClassNodes] */
    var loadStructureTime: Double = .0,

    /** Total real time spent for processing bytecode */
    var totalProcessTime: Double = .0,

    /** Total real time spent on writing class files into zip. */
    var totalWriteTime: Double = .0,

    /** # of entries in the input jar file */
    var totalEntries: AtomicInteger = AtomicInteger(),

    /** # of *.class files in the input jar file */
    var totalClasses: AtomicInteger = AtomicInteger(),
) {
    override fun toString(): String {
        return """
            HostStubGenStats {
              totalTime=$totalTime,
              loadStructureTime=$loadStructureTime,
              totalProcessTime=$totalProcessTime,
              totalWriteTime=$totalWriteTime,
              totalEntries=$totalEntries,
              totalClasses=$totalClasses,
            }
            """.trimIndent()
    }
}

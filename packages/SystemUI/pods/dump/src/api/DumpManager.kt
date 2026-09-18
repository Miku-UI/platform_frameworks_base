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

package com.android.systemui.dump

import com.android.systemui.Dumpable
import com.android.systemui.dump.DumpsysEntry.DumpableEntry
import com.android.systemui.dump.DumpsysEntry.LogBufferEntry
import com.android.systemui.dump.DumpsysEntry.TableLogBufferEntry
import com.android.systemui.log.LogBuffer
import com.android.systemui.log.table.TableLogBuffer

/** Maintains a registry of things that should be dumped when a bug report is taken. */
public interface DumpManager {
    /** See [registerCriticalDumpable]. */
    public fun registerCriticalDumpable(module: Dumpable)

    /**
     * Registers a dumpable to be called during the CRITICAL section of the bug report.
     *
     * The CRITICAL section gets very high priority during a dump, but also a very limited amount of
     * time to do the dumping. So, please don't dump an excessive amount of stuff using CRITICAL.
     */
    public fun registerCriticalDumpable(name: String, module: Dumpable)

    /** See [registerNormalDumpable]. */
    public fun registerNormalDumpable(module: Dumpable)

    /**
     * Registers a dumpable to be called during the NORMAL section of the bug report.
     *
     * The NORMAL section gets a lower priority during a dump, but also more time. This should be
     * used by [LogBuffer] instances, [ProtoDumpable] instances, and any [Dumpable] instances that
     * dump a lot of information.
     */
    public fun registerNormalDumpable(name: String, module: Dumpable)

    /**
     * Register a dumpable to be called during a bug report.
     *
     * @param name The name to register the dumpable under. This is typically the qualified class
     *   name of the thing being dumped (getClass().getName()), but can be anything as long as it
     *   doesn't clash with an existing registration.
     * @param priority the priority level of this dumpable, which affects at what point in the bug
     *   report this gets dump. By default, the dumpable will be called during the CRITICAL section
     *   of the bug report, so don't dump an excessive amount of stuff here.
     */
    @Deprecated("Use registerCriticalDumpable or registerNormalDumpable instead")
    public fun registerDumpable(name: String, module: Dumpable, priority: DumpPriority)

    /** See [registerDumpable]. */
    @Deprecated("Use registerCriticalDumpable or registerNormalDumpable instead")
    public fun registerDumpable(name: String, module: Dumpable) {
        registerDumpable(name, module, DumpPriority.CRITICAL)
    }

    /** Same as the above override, but automatically uses the class name as the dumpable name. */
    public fun registerDumpable(module: Dumpable)

    /** Unregisters a previously-registered dumpable. */
    public fun unregisterDumpable(name: String)

    /** Register a [LogBuffer] to be dumped during a bug report. */
    public fun registerBuffer(name: String, buffer: LogBuffer)

    /** Register a [TableLogBuffer] to be dumped during a bugreport */
    public fun registerTableLogBuffer(name: String, buffer: TableLogBuffer)

    public fun getDumpables(): Collection<DumpableEntry>

    public fun getLogBuffers(): Collection<LogBufferEntry>

    public fun getTableLogBuffers(): Collection<TableLogBufferEntry>

    public fun freezeBuffers()

    public fun unfreezeBuffers()

    /** Clears contents of all log buffers. */
    public fun clearBuffers()
}

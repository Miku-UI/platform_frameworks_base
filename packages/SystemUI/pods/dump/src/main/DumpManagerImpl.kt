/*
 * Copyright (C) 2020 The Android Open Source Project
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
import java.util.TreeMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
public open class DumpManagerImpl @Inject constructor() : DumpManager {
    private val dumpables: MutableMap<String, DumpableEntry> = TreeMap()
    private val buffers: MutableMap<String, LogBufferEntry> = TreeMap()
    private val tableLogBuffers: MutableMap<String, TableLogBufferEntry> = TreeMap()

    override fun registerCriticalDumpable(module: Dumpable) {
        registerCriticalDumpable(module::class.java.name, module)
    }

    override fun registerCriticalDumpable(name: String, module: Dumpable) {
        registerDumpable(name, module, DumpPriority.CRITICAL)
    }

    override fun registerNormalDumpable(module: Dumpable) {
        registerNormalDumpable(module::class.java.name, module)
    }

    override fun registerNormalDumpable(name: String, module: Dumpable) {
        registerDumpable(name, module, DumpPriority.NORMAL)
    }

    @Synchronized
    @Deprecated("Use registerCriticalDumpable or registerNormalDumpable instead")
    // TODO: b/218523243 - This should be removed after the migration to
    // registerCriticalDumpable/registerNormalDumpable is complete.
    override fun registerDumpable(name: String, module: Dumpable, priority: DumpPriority) {
        if (!canAssignToNameLocked(name, module)) {
            throw IllegalArgumentException("'$name' is already registered")
        }

        dumpables[name] = DumpableEntry(module, name, priority)
    }

    @Synchronized
    override fun registerDumpable(module: Dumpable) {
        registerDumpable(module::class.java.name, module)
    }

    @Synchronized
    override fun unregisterDumpable(name: String) {
        dumpables.remove(name)
    }

    @Synchronized
    override fun registerBuffer(name: String, buffer: LogBuffer) {
        if (!canAssignToNameLocked(name, buffer)) {
            throw IllegalArgumentException("'$name' is already registered")
        }

        buffers[name] = LogBufferEntry(buffer, name)
    }

    @Synchronized
    override fun registerTableLogBuffer(name: String, buffer: TableLogBuffer) {
        if (!canAssignToNameLocked(name, buffer)) {
            throw IllegalArgumentException("'$name' is already registered")
        }

        tableLogBuffers[name] = TableLogBufferEntry(buffer, name)
    }

    @Synchronized override fun getDumpables(): Collection<DumpableEntry> = dumpables.values.toList()

    @Synchronized override fun getLogBuffers(): Collection<LogBufferEntry> = buffers.values.toList()

    @Synchronized
    override fun getTableLogBuffers(): Collection<TableLogBufferEntry> =
        tableLogBuffers.values.toList()

    @Synchronized
    override fun freezeBuffers() {
        for (buffer in buffers.values) {
            buffer.buffer.freeze()
        }
    }

    @Synchronized
    override fun unfreezeBuffers() {
        for (buffer in buffers.values) {
            buffer.buffer.unfreeze()
        }
    }

    @Synchronized
    override fun clearBuffers() {
        for (buffer in buffers.values) {
            buffer.buffer.clear()
        }
    }

    private fun canAssignToNameLocked(name: String, newDumpable: Any): Boolean {
        val existingDumpable =
            dumpables[name]?.dumpable ?: buffers[name]?.buffer ?: tableLogBuffers[name]?.table
        return existingDumpable == null || newDumpable == existingDumpable
    }
}

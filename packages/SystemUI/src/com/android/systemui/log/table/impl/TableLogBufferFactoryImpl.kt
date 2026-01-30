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

package com.android.systemui.log.table.impl

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dump.DumpManager
import com.android.systemui.log.LogBufferHelper.Companion.adjustMaxSize
import com.android.systemui.log.LogProxy
import com.android.systemui.log.LogcatEchoTracker
import com.android.systemui.log.table.TableLogBuffer
import com.android.systemui.log.table.TableLogBufferFactory
import com.android.systemui.log.table.TableLogBufferImpl
import com.android.systemui.util.time.SystemClock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@SysUISingleton
class TableLogBufferFactoryImpl
@Inject
constructor(
    private val dumpManager: DumpManager,
    private val systemClock: SystemClock,
    private val logcatEchoTracker: LogcatEchoTracker,
    private val logProxy: LogProxy,
) : TableLogBufferFactory {
    private val existingBuffers = ConcurrentHashMap<String, TableLogBuffer>()

    override fun create(name: String, maxSize: Int): TableLogBuffer {
        val tableBuffer =
            TableLogBufferImpl(
                adjustMaxSize(maxSize),
                name,
                systemClock,
                logcatEchoTracker,
                logProxy,
            )
        dumpManager.registerTableLogBuffer(name, tableBuffer)
        return tableBuffer
    }

    override fun getOrCreate(name: String, maxSize: Int): TableLogBuffer =
        synchronized(existingBuffers) {
            existingBuffers.getOrElse(name) {
                val buffer = create(name, maxSize)
                existingBuffers[name] = buffer
                buffer
            }
        }
}

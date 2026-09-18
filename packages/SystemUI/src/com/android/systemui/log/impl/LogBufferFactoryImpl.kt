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

package com.android.systemui.log.impl

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dump.DumpManager
import com.android.systemui.log.LogBuffer
import com.android.systemui.log.LogBufferFactory
import com.android.systemui.log.LogBufferHelper.Companion.adjustMaxSize
import com.android.systemui.log.LogcatEchoTracker
import com.android.systemui.log.echo.LogcatEchoTrackerAlways
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@SysUISingleton
class LogBufferFactoryImpl
@Inject
constructor(
    private val dumpManager: DumpManager,
    private val logcatEchoTracker: LogcatEchoTracker,
) : LogBufferFactory {
    private val existingBuffers = ConcurrentHashMap<String, LogBuffer>()

    override fun create(
        name: String,
        maxSize: Int,
        systrace: Boolean,
        alwaysLogToLogcat: Boolean,
        systraceTrackName: String,
    ): LogBuffer {
        val echoTracker = if (alwaysLogToLogcat) LogcatEchoTrackerAlways else logcatEchoTracker
        val buffer =
            LogBuffer(name, adjustMaxSize(maxSize), echoTracker, systrace, systraceTrackName)
        dumpManager.registerBuffer(name, buffer)
        return buffer
    }

    override fun getOrCreate(
        name: String,
        maxSize: Int,
        systrace: Boolean,
        alwaysLogToLogcat: Boolean,
        systraceTrackName: String,
    ): LogBuffer =
        existingBuffers.computeIfAbsent(name) {
            create(name, maxSize, systrace, alwaysLogToLogcat, systraceTrackName)
        }
}

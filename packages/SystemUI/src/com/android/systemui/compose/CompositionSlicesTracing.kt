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

package com.android.systemui.compose

import android.os.Trace
import androidx.compose.runtime.Composer
import androidx.compose.runtime.CompositionTracer
import androidx.compose.runtime.InternalComposeTracingApi

/** Reports a slice with the composable name while the composition is in progress. */
@OptIn(InternalComposeTracingApi::class)
object CompositionSlicesTracing {
    fun enable() {
        Composer.setTracer(
            object : CompositionTracer {
                override fun traceEventStart(key: Int, dirty1: Int, dirty2: Int, info: String) {
                    Trace.traceBegin(Trace.TRACE_TAG_APP, info)
                }

                override fun traceEventEnd() = Trace.traceEnd(Trace.TRACE_TAG_APP)

                override fun isTraceInProgress(): Boolean = Trace.isEnabled()
            }
        )
    }

    fun disable() {
        Composer.setTracer(null)
    }
}

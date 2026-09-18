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

package com.android.compose.animation.scene.debug

import com.android.compose.animation.scene.debug.StlDebugConfig.DEBUG_STL

internal fun generateDefaultStlDebugName(): String {
    if (!DEBUG_STL) {
        return "STL"
    }
    val stackTrace = Thread.currentThread().stackTrace

    val caller =
        stackTrace.firstOrNull { frame ->
            val className = frame.className
            val methodName = frame.methodName
            val lineNumber = frame.lineNumber

            if (className.startsWith("dalvik.") || className.startsWith("java."))
                return@firstOrNull false
            if (lineNumber < 0) return@firstOrNull false
            if (methodName == "generateDefaultStlDebugName") return@firstOrNull false
            if (className.contains("SceneTransitionLayout")) return@firstOrNull false
            true
        }

    return caller?.let { "STL (${it.fileName}:${it.lineNumber})" } ?: "STL (Unknown)"
}

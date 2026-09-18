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

@file:Suppress("NOTHING_TO_INLINE")

package com.android.compose.animation.scene.debug

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.compose.animation.scene.debug.StlDebugConfig.elementKeyFilter
import com.android.compose.animation.scene.debug.StlDebugKeys.ELEMENT_FILTER
import com.android.compose.animation.scene.debug.StlDebugKeys.EXCLUDE_STLS
import com.android.compose.animation.scene.debug.StlDebugKeys.LOG_ELEMENTS
import com.android.compose.animation.scene.debug.StlDebugKeys.LOG_ELEMENTS_VERBOSE
import com.android.compose.animation.scene.debug.StlDebugKeys.POS_CONTENT_LABEL
import com.android.compose.animation.scene.debug.StlDebugKeys.POS_ELEMENT_LABEL
import com.android.compose.animation.scene.debug.StlDebugKeys.POS_STL_LABEL
import com.android.compose.animation.scene.debug.StlDebugKeys.SHOW_CONTENT_BORDERS
import com.android.compose.animation.scene.debug.StlDebugKeys.SHOW_CONTENT_LABELS
import com.android.compose.animation.scene.debug.StlDebugKeys.SHOW_ELEMENT_BORDERS
import com.android.compose.animation.scene.debug.StlDebugKeys.SHOW_ELEMENT_LABELS
import com.android.compose.animation.scene.debug.StlDebugKeys.SHOW_STL_BORDERS
import com.android.compose.animation.scene.debug.StlDebugKeys.SHOW_STL_LABELS
import com.android.systemui.util.Compile

/**
 * Manages the global configuration for visual and logging debug of [SceneTransitionLayout].
 *
 * The settings can be set either via App or adb using the following commands example:
 * ```
 * Set Boolean true: `adb shell settings put global debug_stl_show_element_borders 1`
 * Set String filter: `adb shell settings put global debug_stl_element_filter "Button,Icon,Title"`
 * Set Enum by name: `adb shell settings put global debug_stl_pos_element_label "TopLeft"`
 * Reset to default: `adb shell settings delete global debug_stl`
 * ```
 *
 * The keys are found in [StlDebugKeys]
 */
internal object StlDebugConfig {
    /**
     * Master switch to turn off all debugging. In PROD this is completely turned off. For ENG and
     * USERDEBUG builds only we register content observers to listen to the settings set through adb
     * or App.
     */
    const val DEBUG_STL = Compile.IS_DEBUG

    // ====================== ELEMENT ======================
    /**
     * Shows borders around elements. The borders are inset on 3 lanes and dashed to reduce
     * overlapping and better visual. Filtered by [elementKeyFilter].
     */
    private var showElementBorders by mutableStateOf(false)

    inline fun showElementBorders(): Boolean {
        return DEBUG_STL && showElementBorders
    }

    /** Show names of the elements within the element. Filtered by [elementKeyFilter]. */
    private var showElementLabels by mutableStateOf(false)

    inline fun showElementLabels(): Boolean {
        return DEBUG_STL && showElementLabels
    }

    inline fun isDebuggingElement(): Boolean {
        return DEBUG_STL && (showElementBorders or showElementLabels)
    }

    /**
     * Position of the element label within the element. This can be changed to reduce overlapping
     * of labels.
     */
    var elementLabelPosition by mutableStateOf(DebugLabelPosition.CenterTop)

    /**
     * Log the element states of all elements on each transition changes. Filtered by
     * [elementKeyFilter] but can also used without filter if you want to understand the hierarchy.
     *
     * This is useful if you want to understand which contents currently hold which state and which
     * content is currently responsible for placing the element during a transition.
     */
    private var logElements by mutableStateOf(false)

    inline fun logElements(): Boolean {
        return DEBUG_STL && logElements
    }

    /**
     * Log the verbose path that is taken during measure, layout and drawing of a specific element.
     * This is filtered by [elementKeyFilter] and will only log if a filter is set.
     *
     * This is useful if you want to debug the inner workings of STL.
     */
    private var logElementsVerbose by mutableStateOf(false)

    inline fun logElementsVerbose(): Boolean {
        return DEBUG_STL && logElementsVerbose
    }

    /**
     * The filter accepts a comma separated list of keys.
     *
     * Limit visual debugging and logging to elements matching one of the filter Strings with the
     * [elementKey.debugName].
     */
    private var elementKeyFilter: String = ""
        set(value) {
            field = value
            elementFilterList = value.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }

    /**
     * List of filters for [ElementKey.debugName]. The [debugName] has to match one of the strings
     * in this list (case insensitive).
     */
    var elementFilterList by mutableStateOf(emptyList<String>())
        private set

    // ====================== CONTENT ======================

    /** Shows borders around contents */
    private var showContentBorders by mutableStateOf(false)

    inline fun showContentBorders(): Boolean {
        return DEBUG_STL && showContentBorders
    }

    /** Show the content name on a label */
    private var showContentLabels by mutableStateOf(false)

    inline fun showContentLabels(): Boolean {
        return DEBUG_STL && showContentLabels
    }

    inline fun isDebuggingContent(): Boolean {
        return DEBUG_STL && (showContentBorders or showContentLabels)
    }

    /** Position of the content label within the content */
    var contentLabelPosition by mutableStateOf(DebugLabelPosition.BottomRight)

    // ====================== STL ======================

    /** Show a border around an STL */
    private var showStlBorders by mutableStateOf(false)

    inline fun showStlBorders(): Boolean {
        return DEBUG_STL && showStlBorders
    }

    /** Show a label containing the STL name and the currently active transition */
    private var showStlLabels by mutableStateOf(false)

    inline fun showStlLabels(): Boolean {
        return DEBUG_STL && showStlLabels
    }

    inline fun isDebuggingStl(): Boolean {
        return DEBUG_STL && (showStlBorders or showStlLabels)
    }

    /** Position of the STL label */
    var stlLabelPosition by mutableStateOf(DebugLabelPosition.CenterLow)

    /**
     * A comma separated string of STL [debugName]'s to exclude from debugging (case insensitive).
     */
    private var excludeStls: String = ""
        set(value) {
            field = value
            excludeStlsList = value.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }

    /** List of STL [debugName]'s to exclude from debugging (case insensitive). */
    var excludeStlsList by mutableStateOf(emptyList<String>())
        private set

    private var isListening = false

    /** Call to setup Setting listeners that will set debug vars. */
    fun initDebug(context: Context) {
        if (!DEBUG_STL || isListening) return
        isListening = true

        updateFromSettings(context)

        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    updateFromSettings(context)
                }
            }

        StlDebugKeys.entries.forEach { item ->
            context.contentResolver.registerContentObserver(
                Settings.Global.getUriFor(item.key),
                false,
                observer,
            )
        }
    }

    private fun updateFromSettings(context: Context) {
        val resolver = context.contentResolver

        fun readBool(key: String, def: Boolean): Boolean {
            return Settings.Global.getInt(resolver, key, if (def) 1 else 0) == 1
        }

        fun readStr(key: String): String {
            return Settings.Global.getString(resolver, key) ?: ""
        }

        fun <T : Enum<T>> readEnum(key: String, default: T, valueOf: (String) -> T): T {
            val valStr = Settings.Global.getString(resolver, key) ?: ""
            return if (valStr.isNotEmpty())
                try {
                    valueOf(valStr)
                } catch (_: Exception) {
                    default
                }
            else default
        }

        elementKeyFilter = readStr(ELEMENT_FILTER.key)
        excludeStls = readStr(EXCLUDE_STLS.key)

        val defaultToggleValue = false
        showElementBorders = readBool(SHOW_ELEMENT_BORDERS.key, defaultToggleValue)
        showElementLabels = readBool(SHOW_ELEMENT_LABELS.key, defaultToggleValue)
        logElements = readBool(LOG_ELEMENTS.key, defaultToggleValue)
        logElementsVerbose = readBool(LOG_ELEMENTS_VERBOSE.key, defaultToggleValue)

        showContentBorders = readBool(SHOW_CONTENT_BORDERS.key, defaultToggleValue)
        showContentLabels = readBool(SHOW_CONTENT_LABELS.key, defaultToggleValue)

        showStlBorders = readBool(SHOW_STL_BORDERS.key, defaultToggleValue)
        showStlLabels = readBool(SHOW_STL_LABELS.key, defaultToggleValue)

        elementLabelPosition =
            readEnum(POS_ELEMENT_LABEL.key, DebugLabelPosition.CenterTop) {
                DebugLabelPosition.valueOf(it)
            }
        contentLabelPosition =
            readEnum(POS_CONTENT_LABEL.key, DebugLabelPosition.BottomRight) {
                DebugLabelPosition.valueOf(it)
            }
        stlLabelPosition =
            readEnum(POS_STL_LABEL.key, DebugLabelPosition.CenterLow) {
                DebugLabelPosition.valueOf(it)
            }
    }
}

enum class DebugLabelPosition {
    TopLeft,
    TopRight,
    BottomLeft,
    BottomRight,
    Center,
    CenterTop,
    CenterBottom,
    CenterHigh, // 1/3rd down
    CenterLow, // 2/3rds down
}

enum class StlDebugKeys(val key: String) {
    ELEMENT_FILTER("debug_stl_element_filter"),
    SHOW_ELEMENT_BORDERS("debug_stl_show_element_borders"),
    SHOW_ELEMENT_LABELS("debug_stl_show_element_labels"),
    POS_ELEMENT_LABEL("debug_stl_pos_element_label"),
    LOG_ELEMENTS("debug_stl_log_elements"),
    LOG_ELEMENTS_VERBOSE("debug_stl_log_elements_verbose"),
    SHOW_CONTENT_BORDERS("debug_stl_show_content_borders"),
    SHOW_CONTENT_LABELS("debug_stl_show_content_labels"),
    POS_CONTENT_LABEL("debug_stl_pos_content_label"),
    SHOW_STL_BORDERS("debug_stl_show_stl_borders"),
    SHOW_STL_LABELS("debug_stl_show_stl_labels"),
    POS_STL_LABEL("debug_stl_pos_stl_label"),
    EXCLUDE_STLS("debug_stl_exclude_stls"),
}

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

package com.android.systemui.customization.clocks

import android.annotation.SuppressLint
import android.icu.text.SimpleDateFormat
import android.icu.util.TimeZone
import android.icu.util.ULocale
import com.android.systemui.plugins.keyguard.ui.clocks.TimeFormatKind
import java.util.Locale

abstract class DigitalFormatter
protected constructor(
    val timeKeeper: TimeKeeper,
    private val enableContentDescription: Boolean,
    private val textModifier: (String) -> String = { it },
) : TimeKeeper.Callback {

    abstract fun getTextPattern(formatKind: TimeFormatKind): String

    abstract fun getDescriptionPattern(formatKind: TimeFormatKind): String

    val textPattern: String
        get() = getTextPattern(formatKind)

    val descriptionPattern: String
        get() = getDescriptionPattern(formatKind)

    var formatKind = TimeFormatKind.HALF_DAY
        set(value) {
            if (field != value) {
                field = value
                applyPattern()
            }
        }

    var locale: Locale = Locale.getDefault()
        set(value) {
            if (field != value) {
                field = value
                onLocaleChanged()
            }
        }

    protected lateinit var textFormat: SimpleDateFormat
    protected var descriptionFormat: SimpleDateFormat? = null

    protected fun initialize() {
        timeKeeper.callbacks.add(this)
        onLocaleChanged()
    }

    fun onLocaleChanged() {
        textFormat = getTextFormat(locale)
        descriptionFormat = getDescriptionFormat(locale)
        onTimeZoneChanged()
    }

    var overrideTimeZone: TimeZone? = null
        set(value) {
            if (field != value) {
                field = value
                onTimeZoneChanged()
            }
        }

    override fun onTimeZoneChanged() {
        textFormat.timeZone = overrideTimeZone ?: timeKeeper.timeZone
        descriptionFormat?.timeZone = overrideTimeZone ?: timeKeeper.timeZone
        applyPattern()
    }

    @SuppressLint("SimpleDateFormat")
    private fun getTextFormat(locale: Locale): SimpleDateFormat {
        return if (locale.language.equals(Locale.ENGLISH.language)) {
            // force date format in English, and time format to use defined format
            SimpleDateFormat(textPattern, textPattern, ULocale.forLocale(locale))
        } else {
            SimpleDateFormat.getInstanceForSkeleton(textPattern, locale) as SimpleDateFormat
        }
    }

    private fun getDescriptionFormat(locale: Locale): SimpleDateFormat? {
        if (!enableContentDescription) return null
        return SimpleDateFormat.getInstanceForSkeleton(descriptionPattern, locale)
            as SimpleDateFormat
    }

    protected abstract fun applyPattern()

    fun getText(): String {
        return textModifier(textFormat.format(timeKeeper.time))
    }

    fun getContentDescription(): String? {
        return descriptionFormat?.format(timeKeeper.time)
    }
}

class DigitalDateFormatter(
    private val pattern: String,
    timeKeeper: TimeKeeper,
    enableContentDescription: Boolean = false,
    textModifier: (String) -> String = { it },
) : DigitalFormatter(timeKeeper, enableContentDescription, textModifier) {
    override fun getTextPattern(formatKind: TimeFormatKind) = pattern

    override fun getDescriptionPattern(formatKind: TimeFormatKind) = "EEEE MMMM d"

    override fun applyPattern() {
        /* No-op */
    }

    init {
        initialize()
    }
}

class DigitalTimeFormatter(
    private val pattern: String,
    timeKeeper: TimeKeeper,
    enableContentDescription: Boolean = false,
    textModifier: (String) -> String = { it },
) : DigitalFormatter(timeKeeper, enableContentDescription, textModifier) {
    override fun getTextPattern(formatKind: TimeFormatKind): String {
        return when (formatKind) {
            TimeFormatKind.HALF_DAY -> pattern
            TimeFormatKind.FULL_DAY -> pattern.replace("hh", "h").replace("h", "HH")
        }
    }

    override fun getDescriptionPattern(formatKind: TimeFormatKind): String {
        return when (formatKind) {
            TimeFormatKind.HALF_DAY -> "hh:mm"
            TimeFormatKind.FULL_DAY -> "HH:mm"
        }
    }

    override fun applyPattern() {
        textFormat.applyPattern(textPattern)
        descriptionFormat?.applyPattern(descriptionPattern)
    }

    init {
        initialize()
    }
}

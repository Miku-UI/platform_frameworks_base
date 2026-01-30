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

package com.android.systemui.supervision.data.model

import android.graphics.drawable.Drawable

/**
 * Info representing the app that provides and enforces supervision (e.g. parental controls) on the
 * device.
 */
data class SupervisionModel(
    /** Holds `true` if supervision restrictions are enabled on the device, `false` otherwise. */
    val isSupervisionEnabled: Boolean,
    /**
     * The user-visible name of the app enforcing supervision on the device. If null, a fallback
     * generic label will be used (e.g. "Parental Controls").
     */
    val label: CharSequence?,
    /**
     * The user-visible icon of the app enforcing supervision on the device. If null, a fallback
     * icon will be used.
     */
    val icon: Drawable?,
    /**
     * The user-visible text that should be shown on the QS footer button if supervision is enabled
     * on the device. If null, a fallback disclaimer will used.
     */
    val footerText: CharSequence?,
    /**
     * The user-visible text shown on the disclaimer dialog shown after tapping the QS footer
     * button. If null, a fallback text will be used.
     */
    val disclaimerText: CharSequence?,
)

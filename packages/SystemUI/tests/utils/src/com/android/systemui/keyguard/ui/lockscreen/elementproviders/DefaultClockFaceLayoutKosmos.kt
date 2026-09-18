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

package com.android.systemui.keyguard.ui.lockscreen.elementproviders

import android.content.testableContext
import android.graphics.Typeface
import com.android.systemui.customization.clocks.ClockContextImpl
import com.android.systemui.customization.clocks.ClockLogger
import com.android.systemui.customization.clocks.FixedTimeKeeper
import com.android.systemui.customization.clocks.TypefaceCache
import com.android.systemui.customization.clocks.view.DefaultClockFaceLayout
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.plugins.keyguard.ui.clocks.ClockSettings
import com.android.systemui.shared.clocks.FlexClockContext
import com.android.systemui.shared.clocks.view.FlexClockTextView

private val messageBuffer = ClockLogger.DEBUG_MESSAGE_BUFFER

val Kosmos.defaultClockFaceLayout by
    Kosmos.Fixture {
        DefaultClockFaceLayout(
            view =
                FlexClockTextView(
                    clockCtx =
                        FlexClockContext(
                            typefaceCache =
                                TypefaceCache(messageBuffer, 20) {
                                    return@TypefaceCache Typeface.create(
                                        "google-sans-flex-clock",
                                        Typeface.NORMAL,
                                    )
                                },
                            innerCtx =
                                ClockContextImpl(
                                    context = testableContext,
                                    resources = testableContext.resources,
                                    settings = ClockSettings(),
                                    messageBuffer = messageBuffer,
                                    vibrator = null,
                                    timeKeeper = FixedTimeKeeper(),
                                    isAnimationEnabled = false,
                                ),
                        ),
                    isLargeClock = true,
                )
        )
    }

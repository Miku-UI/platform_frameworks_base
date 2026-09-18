/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.android.systemui.customization.clocks

import android.content.Context
import android.content.res.Resources
import android.os.Vibrator
import com.android.systemui.log.core.MessageBuffer
import com.android.systemui.plugins.keyguard.ui.clocks.ClockSettings

interface ClockContext {
    val context: Context
    val resources: Resources
    val settings: ClockSettings
    val messageBuffer: MessageBuffer
    val vibrator: Vibrator?
    val timeKeeper: TimeKeeper
    val isAnimationEnabled: Boolean
}

data class ClockContextImpl(
    override val context: Context,
    override val resources: Resources,
    override val settings: ClockSettings,
    override val messageBuffer: MessageBuffer,
    override val vibrator: Vibrator?,
    override val timeKeeper: TimeKeeper,
    override val isAnimationEnabled: Boolean,
) : ClockContext

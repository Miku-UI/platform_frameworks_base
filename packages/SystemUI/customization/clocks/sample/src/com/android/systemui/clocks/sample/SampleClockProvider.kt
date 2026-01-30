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
package com.android.systemui.clocks.sample

import android.content.Context
import com.android.internal.annotations.Keep
import com.android.systemui.customization.clocks.TimeKeeperImpl
import com.android.systemui.log.LogcatOnlyMessageBuffer
import com.android.systemui.log.core.LogLevel
import com.android.systemui.plugins.annotations.Requires
import com.android.systemui.plugins.keyguard.ui.clocks.ClockController
import com.android.systemui.plugins.keyguard.ui.clocks.ClockMessageBuffers
import com.android.systemui.plugins.keyguard.ui.clocks.ClockMetadata
import com.android.systemui.plugins.keyguard.ui.clocks.ClockPickerConfig
import com.android.systemui.plugins.keyguard.ui.clocks.ClockProviderPlugin
import com.android.systemui.plugins.keyguard.ui.clocks.ClockSettings

@Keep // Proguard should not remove this class as it's functionally the entrypoint
@Requires(target = ClockProviderPlugin::class, version = ClockProviderPlugin.VERSION)
class SampleClockProvider : ClockProviderPlugin {
    private lateinit var hostCtx: Context
    private lateinit var pluginCtx: Context
    private lateinit var messageBuffers: ClockMessageBuffers

    override fun onCreate(hostCtx: Context, pluginCtx: Context) {
        this.hostCtx = hostCtx
        this.pluginCtx = pluginCtx
    }

    override fun initialize(buffers: ClockMessageBuffers?) {
        this.messageBuffers =
            buffers ?: ClockMessageBuffers(LogcatOnlyMessageBuffer(LogLevel.DEBUG))
    }

    override fun getClocks(): List<ClockMetadata> {
        return listOf(ClockMetadata(SAMPLE_CLOCK_ID))
    }

    override fun createClock(ctx: Context, settings: ClockSettings): ClockController {
        if (settings.clockId != SAMPLE_CLOCK_ID)
            throw IllegalArgumentException("${settings.clockId} unsupported by this provider")
        return SampleClockController(ctx, pluginCtx, settings, messageBuffers, TimeKeeperImpl())
    }

    override fun getClockPickerConfig(settings: ClockSettings): ClockPickerConfig {
        if (settings.clockId != SAMPLE_CLOCK_ID)
            throw IllegalArgumentException("${settings.clockId} unsupported by this provider")
        return ClockPickerConfig(
            SAMPLE_CLOCK_ID,
            pluginCtx.resources.getString(R.string.sample_clock_name),
            pluginCtx.resources.getString(R.string.sample_clock_description),
            pluginCtx.resources.getDrawable(R.drawable.sample_clock_thumbnail, null),
        )
    }

    companion object {
        val SAMPLE_CLOCK_ID = "SAMPLE_CLOCK"
    }
}

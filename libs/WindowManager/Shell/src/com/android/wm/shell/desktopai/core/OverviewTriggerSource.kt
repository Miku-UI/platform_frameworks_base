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

package com.android.wm.shell.desktopai.core

import com.android.wm.shell.desktopai.api.ITriggerSource
import com.android.wm.shell.desktopai.api.TriggerEvent
import com.android.wm.shell.desktopai.api.TriggerEventType
import com.android.wm.shell.sysui.OverviewVisibilityChangeListener
import com.android.wm.shell.sysui.ShellController

/**
 * A [ITriggerSource] implementation that monitors the visibility state of the Recents/Overview
 * screen.
 *
 * This source subscribes to [ShellController] events and maps specific Android callbacks to
 * normalized [TriggerEvent] objects with the type [TriggerEventType.SYSTEM].
 *
 * @property shellController The system controller used to register visibility listeners.
 */
class OverviewTriggerSource(private val shellController: ShellController) : ITriggerSource {

    /**
     * Starts listening for overview visibility changes via the ShellController.
     *
     * When the overview is shown or hidden, this method maps the callback to a [TriggerEvent]
     * containing the event ID ("OVERVIEW_SHOWN" or "OVERVIEW_HIDDEN") and populates the payload
     * with the "displayId".
     *
     * @param onEvent The callback to invoke when a relevant system event occurs.
     */
    override fun start(onEvent: (TriggerEvent) -> Unit) {
        shellController.addOverviewVisibilityChangeListener(
            object : OverviewVisibilityChangeListener {
                override fun onOverviewShown(displayId: Int) {
                    onEvent(
                        TriggerEvent(
                            type = TriggerEventType.SYSTEM,
                            id = "OVERVIEW_SHOWN",
                            payload = mapOf("displayId" to displayId),
                        )
                    )
                }

                override fun onOverviewHidden(displayId: Int) {
                    onEvent(
                        TriggerEvent(
                            type = TriggerEventType.SYSTEM,
                            id = "OVERVIEW_HIDDEN",
                            payload = mapOf("displayId" to displayId),
                        )
                    )
                }
            }
        )
    }
}

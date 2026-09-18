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

package com.android.wm.shell.desktopai.extensions.ace

import android.service.personalcontext.PersonalContextManager
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.desktopai.api.CujHandler
import com.android.wm.shell.desktopai.api.TriggerEvent
import com.android.wm.shell.desktopai.api.config.CujConfiguration
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_DESKTOP_AI
import javax.inject.Inject

/**
 * A [CujHandler] that maps a [TriggerEvent] to a [ContextHint] and sends it to the
 * [PersonalContextManagerService].
 */
class ContextEngineCujHandler
@Inject
constructor(
    private val personalContextManager: PersonalContextManager,
    private val hintMapperRegistry: HintMapperRegistry,
) : CujHandler {

    override fun handle(cujConfig: CujConfiguration, triggerEvent: TriggerEvent) {
        val hint = hintMapperRegistry.map(triggerEvent)
        if (hint != null) {
            // This hint is for general context understanding and not tied to a specific UI surface.
            // Therefore, no explicit render tokens are provided.
            personalContextManager.publishTriggeringHint(listOf(hint), null)
        } else {
            // TODO(b/265510619): Add proper ProtoLog warning/error logging for mapping failures.
            ProtoLog.v(
                WM_SHELL_DESKTOP_AI,
                "$TAG: Failed to map TriggerEvent to a ContextHint: %s",
                triggerEvent,
            )
        }
    }

    companion object {
        private const val TAG = "ContextEngineCujHandler"
    }
}

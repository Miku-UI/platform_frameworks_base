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

import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.dagger.WMSingleton
import com.android.wm.shell.desktopai.api.CujHandler
import com.android.wm.shell.desktopai.api.IUserContextService
import com.android.wm.shell.desktopai.api.TriggerEvent
import com.android.wm.shell.desktopai.api.config.CujConfiguration
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_DESKTOP_AI
import javax.inject.Inject

@WMSingleton
class ShellCujHandler @Inject constructor(private val contextService: IUserContextService) :
    CujHandler {
    override fun handle(cujConfig: CujConfiguration, triggerEvent: TriggerEvent) {
        // Context collection phase
        val contextQuery = cujConfig.contextQueryFactory.create(triggerEvent)
        val contextData = contextService.getContext(contextQuery)
        ProtoLog.v(WM_SHELL_DESKTOP_AI, "$TAG: CujHandlerRegistry: %s", contextData)
        // TODO(b/477202336): Use context Data
    }

    companion object {
        private const val TAG = "ShellCujHandler"
    }
}

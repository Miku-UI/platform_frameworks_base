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

package com.android.systemui.appfunctions.trampoline

import android.os.Bundle
import androidx.activity.ComponentActivity
import javax.inject.Inject

/**
 * Internal proxy activity for triggering(fire and forget) AppFunction execution requests.
 *
 * Mainly intended (but not limited to) for UI surfaces which only support Intent / PendingIntent
 * launches on UI but would also like to support AppFunctions.
 *
 * Allowed callers are currently limited to components within SysUI. These are currently considered
 * PCC compliant and already hold permissions to execute AppFunctions. Other components must not be
 * granted access to launch this activity unless they already hold rights to execute AppFunctions.
 */
class AppFunctionExecutorProxy
@Inject
internal constructor(private val controller: AppFunctionExecutorProxyController) :
    ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controller.parseAppFuncAndExecute(intent)
        finish()
    }
}

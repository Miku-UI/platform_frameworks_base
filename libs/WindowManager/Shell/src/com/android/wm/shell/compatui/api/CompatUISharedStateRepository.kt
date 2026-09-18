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

package com.android.wm.shell.compatui.api

import android.content.res.Configuration
import android.graphics.Rect
import android.os.Bundle
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.dagger.WMSingleton
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_APP_COMPAT
import com.android.wm.shell.repository.GenericRepository
import com.android.wm.shell.repository.MemoryRepositoryImpl
import javax.inject.Inject

/** Represents the state shared between all the components. */
data class CompatUISharedState(
    val taskId: Int = -1,
    val stableBounds: Rect? = null,
    val areParentBoundsChanged: Boolean = false,
    val taskConfiguration: Configuration,
    val layoutDirectionFn: () -> Int = { taskConfiguration.layoutDirection },
    val taskBoundsFn: () -> Rect = { taskConfiguration.windowConfiguration.bounds },
    val paramsBundle: Bundle = Bundle(),
)

/** [GenericRepository] with information shared with multiple [CompatUIComponent]s. */
@WMSingleton
class CompatUISharedStateRepository @Inject constructor() :
    GenericRepository<Int, CompatUISharedState> by MemoryRepositoryImpl(
        logger = { msg ->
            ProtoLog.v(WM_SHELL_APP_COMPAT, "CompatUISharedStateRepository: %s", msg)
        }
    )

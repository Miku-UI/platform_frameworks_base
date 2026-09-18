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

package com.android.systemui.globalactions.data.repository.impl

import android.content.Context
import android.util.Log
import com.android.internal.R
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.globalactions.data.repository.GlobalActionsRepository
import com.android.systemui.globalactions.shared.model.GlobalActionType
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Encapsulates application state for global actions. */
@SysUISingleton
class GlobalActionsRepositoryImpl
@Inject
constructor(@param:Application private val context: Context) : GlobalActionsRepository {
    private val _isVisible: MutableStateFlow<Boolean> = MutableStateFlow(false)
    /** Is the global actions dialog visible. */
    override val isVisible = _isVisible.asStateFlow()

    /** Sets whether the global actions dialog is visible. */
    override fun setVisible(isVisible: Boolean) {
        _isVisible.value = isVisible
    }

    /** The list of available global actions. */
    override val possibleGlobalActions: List<GlobalActionType> =
        context.resources.getStringArray(R.array.config_globalActionsList).mapNotNull { key ->
            val actionType = GlobalActionType.fromConfigKey(key)
            if (actionType == null) {
                Log.e(TAG, "Invalid global action key in config_globalActionsList: $key")
            }
            actionType
        }

    /** Actions not available when device is unprovisioned */
    override val unprovisionedDeviceStateBlockList: List<GlobalActionType> =
        listOf(
            GlobalActionType.AIRPLANE,
            GlobalActionType.BUGREPORT,
            GlobalActionType.SILENT,
            GlobalActionType.USERS,
            GlobalActionType.LOCKDOWN,
            GlobalActionType.LOCK,
            GlobalActionType.SCREENSHOT,
            GlobalActionType.LOGOUT,
            GlobalActionType.SYSTEM_UPDATE,
        )

    /** Actions not available when device is locked. */
    override val lockedDeviceStateBlockList: List<GlobalActionType> = listOf(GlobalActionType.LOCK)

    companion object {
        private const val TAG = "GlobalActionsRepositoryImpl"
    }
}

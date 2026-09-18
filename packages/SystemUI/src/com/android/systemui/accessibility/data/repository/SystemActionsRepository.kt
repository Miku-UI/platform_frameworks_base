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

package com.android.systemui.accessibility.data.repository

import android.annotation.StringRes
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.RemoteAction
import android.content.Context
import android.graphics.drawable.Icon
import android.view.accessibility.AccessibilityManager
import com.android.internal.R
import com.android.systemui.accessibility.SystemActions
import com.android.systemui.accessibility.SystemActionsHelper
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Helper class for [SystemActions], written in Kotlin so coroutines can be used. */
@SysUISingleton
@SuppressLint("MissingPermission")
class SystemActionsRepository
@Inject
constructor(
    @Background private val backgroundDispatcher: CoroutineDispatcher,
    private val context: Context,
    private val accessibilityManager: AccessibilityManager,
) {

    /** Registers a system action to dismiss the notification shade. */
    suspend fun registerDismissShadeSystemAction() {
        if (!SceneContainerFlag.isEnabled) {
            return
        }
        val intent =
            SystemActionsHelper.createIntent(
                context,
                SystemActionsHelper.INTENT_ACTION_ACCESSIBILITY_DISMISS_NOTIFICATION_SHADE,
            )
        val pendingIntent =
            withContext(backgroundDispatcher) {
                PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            }
        accessibilityManager.registerSystemAction(
            pendingIntent.toRemoteAction(),
            DISMISS_SHADE_SYSTEM_ACTION,
        )
    }

    /** Unregisters a system action to dismiss the notification shade. */
    suspend fun unregisterDismissShadeSystemAction() {
        if (!SceneContainerFlag.isEnabled) {
            return
        }
        withContext(backgroundDispatcher) {
            accessibilityManager.unregisterSystemAction(DISMISS_SHADE_SYSTEM_ACTION)
        }
    }

    private fun PendingIntent.toRemoteAction(): RemoteAction {
        val label = context.getString(actionLabel)
        return RemoteAction(
            Icon.createWithResource(context, R.drawable.ic_info),
            label,
            label,
            this,
        )
    }

    companion object {
        private const val DISMISS_SHADE_SYSTEM_ACTION =
            SystemActions.SYSTEM_ACTION_ID_ACCESSIBILITY_DISMISS_NOTIFICATION_SHADE

        @StringRes
        private val actionLabel = R.string.accessibility_system_action_dismiss_notification_shade
    }
}

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

package com.android.systemui.statusbar.notification.stack

import com.android.systemui.kairos.awaitClose
import com.android.systemui.statusbar.notification.NotificationActivityStarter
import com.android.systemui.utils.coroutines.flow.conflatedCallbackFlow
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.suspendCancellableCoroutine

val OnboardingAffordanceView.onDismissClicked: Flow<Unit>
    get() = conflatedCallbackFlow {
        setOnDismissClickListener { trySend(Unit) }
        awaitClose { setOnDismissClickListener(null) }
    }

val OnboardingAffordanceView.onTurnOnClicked: Flow<Unit>
    get() = conflatedCallbackFlow {
        setOnTurnOnClickListener { trySend(Unit) }
        awaitClose { setOnTurnOnClickListener(null) }
    }

class NotificationActivityStarterScope(
    private val starter: NotificationActivityStarter,
    private val view: OnboardingAffordanceView,
) {
    suspend fun startSettingsIntent(settingsIntent: NotificationActivityStarter.SettingsIntent) {
        suspendCancellableCoroutine { k ->
            view.setOnActivityLaunchEndListener { k.resume(Unit) }
            starter.startSettingsIntent(view, settingsIntent)
        }
    }
}

suspend fun OnboardingAffordanceView.activityStarterScope(
    starter: NotificationActivityStarter,
    block: suspend NotificationActivityStarterScope.() -> Unit,
) {
    val scope = NotificationActivityStarterScope(starter, this)
    scope.block()
}

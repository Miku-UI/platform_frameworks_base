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

package com.android.systemui.activity.data.repository

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import com.android.app.tracing.coroutines.launchTraced as launch
import com.android.systemui.activity.data.repository.ActivityIntentRepository.Companion.getTargetActivityInfoFlags
import com.android.systemui.activity.data.repository.ActivityIntentRepository.Companion.toActivityInfo
import com.android.systemui.activity.data.repository.ActivityIntentRepository.Companion.wouldActivityInfoShowOverLockscreen
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import java.util.concurrent.Executor
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext

@SuppressLint("MissingPermission")
@SysUISingleton
class ActivityIntentRepositoryImpl
@Inject
constructor(
    @Background private val backgroundScope: CoroutineScope,
    @Background private val backgroundDispatcher: CoroutineDispatcher,
    private val packageManager: PackageManager,
) : ActivityIntentRepository {
    override fun <T> wouldPendingIntentShowOverLockscreen(
        intent: PendingIntent,
        currentUserId: Int,
        executor: Executor,
        callbackParameters: T,
        callback: WouldPendingIntentShowOverLockscreenCallback<T>,
    ) {
        backgroundScope.launch {
            val flags: Int = getTargetActivityInfoFlags(onlyDirectBootAware = false)
            // queryIntentComponents can make a binder call, so do it in the background.
            val intentComponents: List<ResolveInfo> =
                withContext(backgroundDispatcher) { intent.queryIntentComponents(flags) }
            val activityInfo =
                intentComponents.toActivityInfo(intent.intent, flags, currentUserId, packageManager)
            val wouldPendingShowOverLockscreen = activityInfo.wouldActivityInfoShowOverLockscreen()
            executor.execute {
                callback.onWouldPendingShowOverLockscreenFetched(
                    wouldPendingShowOverLockscreen,
                    callbackParameters,
                )
            }
        }
    }
}
